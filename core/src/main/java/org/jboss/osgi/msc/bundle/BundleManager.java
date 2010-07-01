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

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.msc.plugin.BundleStoragePlugin;
import org.jboss.osgi.msc.plugin.Plugin;
import org.jboss.osgi.msc.plugin.ServiceManagerPlugin;
import org.jboss.osgi.msc.plugin.internal.BundleStoragePluginImpl;
import org.jboss.osgi.msc.plugin.internal.ServiceManagerPluginImpl;
import org.jboss.osgi.spi.util.BundleInfo;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * OSGiBundleManager.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class BundleManager
{
   // The BundleId generator 
   private AtomicLong identityGenerator = new AtomicLong();
   // Maps bundleId to Bundle
   private Map<Long, AbstractBundle> bundleMap = Collections.synchronizedMap(new LinkedHashMap<Long, AbstractBundle>());
   /// The registered plugins 
   private Map<Class<? extends Plugin>, Plugin> plugins = Collections.synchronizedMap(new LinkedHashMap<Class<? extends Plugin>, Plugin>());
   // The module loader
   private ModuleManager moduleManager;
   // The Framework state
   private FrameworkState frameworkState;

   public BundleManager(Map<String, String> props)
   {
      frameworkState = new FrameworkState(this, plugins, props);
      addBundleState(frameworkState.getSystemBundle());

      // Create the ModuleLoader
      moduleManager = new ModuleManager();
      
      // Register the framework plugins
      // [TODO] Externalize plugin registration
      plugins.put(BundleStoragePlugin.class, new BundleStoragePluginImpl(this));
      plugins.put(ServiceManagerPlugin.class, new ServiceManagerPluginImpl(this));
   }

   public ModuleManager getModuleManager()
   {
      return moduleManager;
   }

   public FrameworkState getFrameworkState()
   {
      return frameworkState;
   }

   public SystemBundle getSystemBundle()
   {
      return frameworkState.getSystemBundle();
   }

   long getNextBundleId()
   {
      return identityGenerator.incrementAndGet();
   }
   
   void addBundleState(AbstractBundle bundleState)
   {
      bundleMap.put(bundleState.getBundleId(), bundleState);
      bundleState.changeState(Bundle.INSTALLED);
   }

   void removeBundleState(AbstractBundle bundleState)
   {
      bundleMap.remove(bundleState.getBundleId());
      bundleState.changeState(Bundle.UNINSTALLED);
   }

   AbstractBundle getBundleById(long bundleId)
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
   
   List<AbstractBundle> getBundles()
   {
      List<AbstractBundle> bundles = new ArrayList<AbstractBundle>(bundleMap.values());
      return Collections.unmodifiableList(bundles);
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
   AbstractBundle installBundle(String location) throws BundleException
   {
      return installBundle(location, null);
   }

   /**
    * Install a bundle from the given location and optional input stream.
    */
   AbstractBundle installBundle(String location, InputStream input) throws BundleException
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
      bundleState = AbstractBundle.createBundle(this, dep);
      addBundleState(bundleState);
      return bundleState;
   }
   
   List<HostBundle> resolveBundles(List<HostBundle> bundles) throws BundleException
   {
      // Get the list of unresolved bundles
      List<HostBundle> unresolvedBundles = new ArrayList<HostBundle>();
      if (bundles == null)
      {
         for (AbstractBundle aux : getBundles())
         {
            if (aux instanceof HostBundle && aux.getState() == Bundle.INSTALLED)
               unresolvedBundles.add((HostBundle)aux);
         }
      }
      else
      {
         unresolvedBundles.addAll(bundles);
      }
      
      List<HostBundle> resolvedBundles = new ArrayList<HostBundle>();
      for (HostBundle bundleState : unresolvedBundles)
      {
         try
         {
            Module module = moduleManager.createModule(bundleState.getModuleSpec());
            bundleState.setModule(module);
            resolvedBundles.add(bundleState);
         }
         catch (ModuleLoadException ex)
         {
            throw new BundleException("Cannot load module: " + bundleState, ex);
         }
      }
      return Collections.unmodifiableList(resolvedBundles);
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
      //FrameworkEventsPlugin plugin = getPlugin(FrameworkEventsPlugin.class);
      //if (t instanceof BundleException)
      //   plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, t);
      //else if (bundle != null)
      //   plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, new BundleException("Error " + context + " bundle: " + bundle, t));
      //else
      //   plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.ERROR, new BundleException("Error " + context, t));
   }

   /**
    * Fire a framework warning
    */
   void fireWarning(Bundle bundle, String context, Throwable t)
   {
      //FrameworkEventsPlugin plugin = getPlugin(FrameworkEventsPlugin.class);
      //if (t instanceof BundleException)
      //   plugin.fireFrameworkEvent(bundle, FrameworkEvent.WARNING, t);
      //else if (bundle != null)
      //   plugin.fireFrameworkEvent(bundle, FrameworkEvent.WARNING, new BundleException("Error " + context + " bundle: " + bundle, t));
      //else
      //   plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.WARNING, new BundleException("Error " + context, t));
   }
}
