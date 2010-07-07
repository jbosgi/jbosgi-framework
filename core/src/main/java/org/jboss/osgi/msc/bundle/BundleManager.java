/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.osgi.msc.bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.Manifest;

import org.jboss.logging.Logger;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.msc.metadata.internal.OSGiManifestMetaData;
import org.jboss.osgi.msc.plugin.AutoInstallPlugin;
import org.jboss.osgi.msc.plugin.BundleStoragePlugin;
import org.jboss.osgi.msc.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.msc.plugin.LifecycleInterceptorPlugin;
import org.jboss.osgi.msc.plugin.ModuleManagerPlugin;
import org.jboss.osgi.msc.plugin.PackageAdminPlugin;
import org.jboss.osgi.msc.plugin.Plugin;
import org.jboss.osgi.msc.plugin.ResolverPlugin;
import org.jboss.osgi.msc.plugin.ServiceManagerPlugin;
import org.jboss.osgi.msc.plugin.SystemPackagesPlugin;
import org.jboss.osgi.msc.plugin.internal.AutoInstallPluginImpl;
import org.jboss.osgi.msc.plugin.internal.BundleStoragePluginImpl;
import org.jboss.osgi.msc.plugin.internal.FrameworkEventsPluginImpl;
import org.jboss.osgi.msc.plugin.internal.LifecycleInterceptorPluginImpl;
import org.jboss.osgi.msc.plugin.internal.ModuleManagerPluginImpl;
import org.jboss.osgi.msc.plugin.internal.PackageAdminPluginImpl;
import org.jboss.osgi.msc.plugin.internal.ResolverPluginImpl;
import org.jboss.osgi.msc.plugin.internal.ServiceManagerPluginImpl;
import org.jboss.osgi.msc.plugin.internal.SystemPackagesPluginImpl;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.spi.util.BundleInfo;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;

/**
 * OSGiBundleManager.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class BundleManager
{
   // Provide logging
   private static final Logger log = Logger.getLogger(BundleManager.class);
   
   // The BundleId generator 
   private AtomicLong identityGenerator = new AtomicLong();
   // Maps bundleId to Bundle
   private Map<Long, AbstractBundle> bundleMap = Collections.synchronizedMap(new LinkedHashMap<Long, AbstractBundle>());
   /// The registered plugins 
   private Map<Class<? extends Plugin>, Plugin> plugins = Collections.synchronizedMap(new LinkedHashMap<Class<? extends Plugin>, Plugin>());
   // The Framework state
   private FrameworkState frameworkState;

   public BundleManager(Map<String, String> props)
   {
      frameworkState = new FrameworkState(this, props);

      // Register the framework plugins
      // [TODO] Externalize plugin registration
      plugins.put(AutoInstallPlugin.class, new AutoInstallPluginImpl(this));
      plugins.put(BundleStoragePlugin.class, new BundleStoragePluginImpl(this));
      plugins.put(FrameworkEventsPlugin.class, new FrameworkEventsPluginImpl(this));
      plugins.put(LifecycleInterceptorPlugin.class, new LifecycleInterceptorPluginImpl(this));
      plugins.put(ModuleManagerPlugin.class, new ModuleManagerPluginImpl(this));
      plugins.put(PackageAdminPlugin.class, new PackageAdminPluginImpl(this));
      plugins.put(ResolverPlugin.class, new ResolverPluginImpl(this));
      plugins.put(ServiceManagerPlugin.class, new ServiceManagerPluginImpl(this));
      plugins.put(SystemPackagesPlugin.class, new SystemPackagesPluginImpl(this));

      // Create and add the system bundle
      addBundleState(frameworkState.getSystemBundle());
   }

   public FrameworkState getFrameworkState()
   {
      return frameworkState;
   }

   long getNextBundleId()
   {
      return identityGenerator.incrementAndGet();
   }

   public SystemBundle getSystemBundle()
   {
      return frameworkState.getSystemBundle();
   }

   public BundleContext getSystemContext()
   {
      return getSystemBundle().getBundleContext();
   }

   public boolean isFrameworkActive()
   {
      // We are active if the system bundle is ACTIVE
      SystemBundle bundleState = getSystemBundle();
      return bundleState.getState() == Bundle.ACTIVE;
   }
   
   void addBundleState(AbstractBundle bundleState)
   {
      ResolverPlugin plugin = getPlugin(ResolverPlugin.class);
      plugin.addBundle(bundleState);
      
      bundleMap.put(bundleState.getBundleId(), bundleState);
      bundleState.changeState(Bundle.INSTALLED);
   }

   void removeBundleState(AbstractBundle bundleState)
   {
      ResolverPlugin plugin = getPlugin(ResolverPlugin.class);
      plugin.removeBundle(bundleState);
      
      bundleMap.remove(bundleState.getBundleId());
      bundleState.changeState(Bundle.UNINSTALLED);
   }

   public AbstractBundle getBundleById(long bundleId)
   {
      if (bundleId == 0)
         return getSystemBundle();

      return bundleMap.get(bundleId);
   }

   /**
    * Get a bundle by location
    * 
    * @param location the location of the bundle
    * @return the bundle or null if there is no bundle with that location
    */
   AbstractBundle getBundleByLocation(String location)
   {
      if (location == null)
         throw new IllegalArgumentException("Null location");

      AbstractBundle result = null;
      for (AbstractBundle aux : getBundles())
      {
         String auxLocation = aux.getLocation();
         if (location.equals(auxLocation))
         {
            result = aux;
            break;
         }
      }
      return result;
   }

   public List<AbstractBundle> getBundles()
   {
      List<AbstractBundle> bundles = new ArrayList<AbstractBundle>(bundleMap.values());
      return Collections.unmodifiableList(bundles);
   }

   /**
    * Get the list of registered plugins
    */
   public List<Plugin> getPlugins()
   {
      return Collections.unmodifiableList(new ArrayList<Plugin>(plugins.values()));
   }

   /**
    * Get a plugin that is registered with the bundle manager.
    * @throws IllegalStateException if the requested plugin class is not registered
    */
   @SuppressWarnings("unchecked")
   public <T extends Plugin> T getPlugin(Class<T> clazz)
   {
      T plugin = (T)plugins.get(clazz);
      if (plugin == null)
         throw new IllegalStateException("Cannot obtain plugin for: " + clazz.getName());

      return plugin;
   }

   /**
    * Get an optional plugin that is registered with the bundle manager.
    * @return The plugin instance or null if the requested plugin class is not registered
    */
   @SuppressWarnings("unchecked")
   public <T extends Plugin> T getOptionalPlugin(Class<T> clazz)
   {
      return (T)plugins.get(clazz);
   }

   /**
    * Install a bundle from the given location.
    */
   public AbstractBundle installBundle(URL location) throws BundleException
   {
      return installBundle(location.toExternalForm(), null);
   }

   /**
    * Install a bundle from the given location.
    */
   public AbstractBundle installBundle(String location) throws BundleException
   {
      return installBundle(location, null);
   }

   /**
    * Install a bundle from the given location and optional input stream.
    */
   public AbstractBundle installBundle(String location, InputStream input) throws BundleException
   {
      if (location == null)
         throw new BundleException("Null location");

      URL locationURL;

      // Get the location URL
      if (input != null)
      {
         try
         {
            BundleStoragePlugin plugin = getPlugin(BundleStoragePlugin.class);
            String path = plugin.getStorageDir(getSystemBundle()).getCanonicalPath();

            File file = new File(path + "/bundle-" + System.currentTimeMillis() + ".jar");
            FileOutputStream fos = new FileOutputStream(file);
            VFSUtils.copyStream(input, fos);
            fos.close();

            locationURL = file.toURI().toURL();
         }
         catch (IOException ex)
         {
            throw new BundleException("Cannot store bundle from input stream", ex);
         }
      }
      else
      {
         locationURL = getLocationURL(location);
      }

      // Get the root file
      VirtualFile root;
      try
      {
         root = AbstractVFS.getRoot(locationURL);
      }
      catch (IOException e)
      {
         throw new BundleException("Invalid bundle location=" + locationURL, e);
      }

      return install(root, location, false);
   }

   /**
    * Install a bundle from the given {@link VirtualFile}
    */
   private AbstractBundle install(VirtualFile root, String location, boolean autoStart) throws BundleException
   {
      if (location == null)
         throw new IllegalArgumentException("Null location");

      Deployment dep;
      try
      {
         BundleInfo info = BundleInfo.createBundleInfo(root, location);
         dep = DeploymentFactory.createDeployment(info);
         dep.setAutoStart(autoStart);
      }
      catch (RuntimeException ex)
      {
         throw new BundleException("Cannot install bundle: " + root, ex);
      }

      return installBundle(dep);
   }

   /**
    * Install a bundle from a {@link Deployment} 
    */
   private AbstractBundle installBundle(Deployment dep) throws BundleException
   {
      if (dep == null)
         throw new IllegalArgumentException("Null deployment");

      // If a bundle containing the same location identifier is already installed, 
      // the Bundle object for that bundle is returned. 
      AbstractBundle bundleState = getBundleByLocation(dep.getLocation());
      if (bundleState != null)
         return bundleState;

      // Create the bundle state
      bundleState = createBundle(dep);
      addBundleState(bundleState);
      return bundleState;
   }

   private AbstractBundle createBundle(Deployment dep) throws BundleException
   {
      VirtualFile rootFile = dep.getRoot();
      String location = dep.getLocation();

      Manifest manifest;
      try
      {
         manifest = VFSUtils.getManifest(rootFile);
      }
      catch (IOException ex)
      {
         throw new BundleException("Cannot obtain manifest from: " + dep);
      }

      OSGiMetaData metadata = new OSGiManifestMetaData(manifest);
      if (metadata.getFragmentHost() != null)
         throw new NotImplementedException("Fragments not support");

      AbstractBundle bundleState = new HostBundle(this, metadata, location, rootFile);
      return bundleState;
   }

   private URL getLocationURL(String location) throws BundleException
   {
      // Try location as URL
      URL url = null;
      try
      {
         url = new URL(location);
      }
      catch (MalformedURLException e)
      {
         // ignore
      }

      // Try location as File
      if (url == null)
      {
         try
         {
            File file = new File(location);
            if (file.exists())
               url = file.toURI().toURL();
         }
         catch (MalformedURLException e)
         {
            // ignore
         }
      }

      if (url == null)
         throw new BundleException("Unable to handle location=" + location);

      return url;
   }

   /**
    * Fire a framework error
    */
   void fireError(Bundle bundle, String context, Throwable t)
   {
      FrameworkEventsPlugin plugin = getPlugin(FrameworkEventsPlugin.class);
      if (t instanceof BundleException)
         plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, t);
      else if (bundle != null)
         plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, new BundleException("Error " + context + " bundle: " + bundle, t));
      else
         plugin.fireFrameworkEvent(getSystemBundle(), FrameworkEvent.ERROR, new BundleException("Error " + context, t));
   }

   /**
    * Fire a framework warning
    */
   void fireWarning(Bundle bundle, String context, Throwable t)
   {
      FrameworkEventsPlugin plugin = getPlugin(FrameworkEventsPlugin.class);
      if (t instanceof BundleException)
         plugin.fireFrameworkEvent(bundle, FrameworkEvent.WARNING, t);
      else if (bundle != null)
         plugin.fireFrameworkEvent(bundle, FrameworkEvent.WARNING, new BundleException("Error " + context + " bundle: " + bundle, t));
      else
         plugin.fireFrameworkEvent(getSystemBundle(), FrameworkEvent.WARNING, new BundleException("Error " + context, t));
   }
}
