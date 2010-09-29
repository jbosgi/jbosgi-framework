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
package org.jboss.osgi.framework.bundle;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceController;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.plugin.AutoInstallPlugin;
import org.jboss.osgi.framework.plugin.BundleDeploymentPlugin;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.framework.plugin.DeployerServicePlugin;
import org.jboss.osgi.framework.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.framework.plugin.LifecycleInterceptorPlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.NativeCodePlugin;
import org.jboss.osgi.framework.plugin.PackageAdminPlugin;
import org.jboss.osgi.framework.plugin.Plugin;
import org.jboss.osgi.framework.plugin.ResolverPlugin;
import org.jboss.osgi.framework.plugin.ServiceManagerPlugin;
import org.jboss.osgi.framework.plugin.StartLevelPlugin;
import org.jboss.osgi.framework.plugin.SystemPackagesPlugin;
import org.jboss.osgi.framework.plugin.internal.AutoInstallPluginImpl;
import org.jboss.osgi.framework.plugin.internal.BundleDeploymentPluginImpl;
import org.jboss.osgi.framework.plugin.internal.BundleStoragePluginImpl;
import org.jboss.osgi.framework.plugin.internal.DeployerServicePluginImpl;
import org.jboss.osgi.framework.plugin.internal.FrameworkEventsPluginImpl;
import org.jboss.osgi.framework.plugin.internal.LifecycleInterceptorPluginImpl;
import org.jboss.osgi.framework.plugin.internal.ModuleManagerPluginImpl;
import org.jboss.osgi.framework.plugin.internal.NativeCodePluginImpl;
import org.jboss.osgi.framework.plugin.internal.PackageAdminPluginImpl;
import org.jboss.osgi.framework.plugin.internal.ResolverPluginImpl;
import org.jboss.osgi.framework.plugin.internal.ServiceManagerPluginImpl;
import org.jboss.osgi.framework.plugin.internal.StartLevelPluginImpl;
import org.jboss.osgi.framework.plugin.internal.SystemPackagesPluginImpl;
import org.jboss.osgi.framework.plugin.internal.WebXMLVerifierInterceptor;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XVersionRange;
import org.jboss.osgi.spi.util.BundleInfo;
import org.jboss.osgi.spi.util.SysPropertyActions;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;

/**
 * The BundleManager is the central managing entity for OSGi bundles.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public class BundleManager
{
   // Provide logging
   private final Logger log = Logger.getLogger(BundleManager.class);

   // The raw properties
   private Map<String, Object> properties = new HashMap<String, Object>();
   // The BundleId generator
   private AtomicLong identityGenerator = new AtomicLong();
   // Maps bundleId to Bundle
   private Map<Long, AbstractBundle> bundleMap = Collections.synchronizedMap(new LinkedHashMap<Long, AbstractBundle>());
   /// The registered plugins
   private Map<Class<? extends Plugin>, Plugin> plugins = new LinkedHashMap<Class<? extends Plugin>, Plugin>();
   // The Framework state
   private FrameworkState frameworkState;

   // The Framework integration mode
   public enum IntegrationMode
   {
      STANDALONE, CONTAINER
   }

   public BundleManager(Map<String, Object> initialProperties)
   {
      // The properties on the BundleManager are mutable as long the framework is not created
      // Plugins may modify these properties in their respective constructor
      if (initialProperties != null)
         properties.putAll(initialProperties);

      // Register the framework plugins
      plugins.put(BundleDeploymentPlugin.class, new BundleDeploymentPluginImpl(this));
      plugins.put(BundleStoragePlugin.class, new BundleStoragePluginImpl(this));
      plugins.put(FrameworkEventsPlugin.class, new FrameworkEventsPluginImpl(this));
      plugins.put(ModuleManagerPlugin.class, new ModuleManagerPluginImpl(this));
      plugins.put(NativeCodePlugin.class, new NativeCodePluginImpl(this));
      plugins.put(ResolverPlugin.class, new ResolverPluginImpl(this));
      plugins.put(ServiceManagerPlugin.class, new ServiceManagerPluginImpl(this));
      plugins.put(SystemPackagesPlugin.class, new SystemPackagesPluginImpl(this));

      // Register system service plugins
      plugins.put(DeployerServicePlugin.class, new DeployerServicePluginImpl(this));
      plugins.put(LifecycleInterceptorPlugin.class, new LifecycleInterceptorPluginImpl(this));
      plugins.put(WebXMLVerifierInterceptor.class, new WebXMLVerifierInterceptor(this));
      plugins.put(PackageAdminPlugin.class, new PackageAdminPluginImpl(this));
      plugins.put(StartLevelPlugin.class, new StartLevelPluginImpl(this));

      // Finally add the AutoInstallPlugin
      plugins.put(AutoInstallPlugin.class, new AutoInstallPluginImpl(this));

      // Create the Framework state
      frameworkState = new FrameworkState(this);
   }

   public FrameworkState getFrameworkState()
   {
      return frameworkState;
   }

   public SystemBundle getSystemBundle()
   {
      return frameworkState;
   }

   public IntegrationMode getIntegrationMode()
   {
      // The AS integration layer provides the ServiceController
      Object controller = getProperty(ServiceController.class.getName());
      return controller != null ? IntegrationMode.CONTAINER : IntegrationMode.STANDALONE;
   }

   long getNextBundleId()
   {
      return identityGenerator.incrementAndGet();
   }

   public BundleContext getSystemContext()
   {
      return frameworkState.getBundleContext();
   }

   public Object getProperty(String key)
   {
      Object value = properties.get(key);
      if (value == null)
         value = SysPropertyActions.getProperty(key, null);
      return value;
   }

   public Map<String, Object> getProperties()
   {
      return Collections.unmodifiableMap(properties);
   }

   public void setProperty(String key, Object value)
   {
      if (isFrameworkActive())
         throw new IllegalStateException("Cannot add property to ACTIVE framwork");

      properties.put(key, value);
   }

   public boolean isFrameworkActive()
   {
      // We are active if the system bundle is ACTIVE
      return frameworkState != null && frameworkState.getState() == Bundle.ACTIVE;
   }

   void addBundleState(AbstractBundle bundleState)
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");

      long bundleId = bundleState.getBundleId();
      if (bundleMap.containsKey(bundleId) == true)
         throw new IllegalStateException("Bundle already added: " + bundleState);

      log.tracef("Add bundle: %s", bundleState);

      // Register the bundle with the manager
      bundleMap.put(bundleId, bundleState);
      bundleState.changeState(Bundle.INSTALLED);

      // Add the bundle to the resolver
      bundleState.addToResolver();
   }

   void removeBundle(AbstractBundle bundleState)
   {
      log.tracef("Remove bundle: %s", bundleState);
      bundleState.removeFromResolver();
      bundleMap.remove(bundleState.getBundleId());
   }

   void uninstallBundle(AbstractBundle bundleState)
   {
      bundleState.changeState(Bundle.UNINSTALLED);
      List<AbstractBundle> uninstalled = getBundles(Bundle.UNINSTALLED);
      for (AbstractBundle aux : uninstalled)
      {
         AbstractUserBundle userBundle = AbstractUserBundle.assertBundleState(aux);
         if (userBundle.hasActiveWires() == false)
            userBundle.remove();
      }
   }

   /**
    * Get a bundle by id
    *
    * Note, this will get the bundle regadless of its state.
    * i.e. The returned bundle may have been UNINSTALLED
    *
    * @param bundleId The identifier of the bundle
    * @return The bundle or null if there is no bundle with that id
    */
   public AbstractBundle getBundleById(long bundleId)
   {
      if (bundleId == 0)
         return frameworkState;

      return bundleMap.get(bundleId);
   }

   /**
    * Get a bundle by location
    *
    * Note, this will get the bundle regadless of its state.
    * i.e. The returned bundle may have been UNINSTALLED
    *
    * @param location the location of the bundle
    * @return the bundle or null if there is no bundle with that location
    */
   public AbstractBundle getBundleByLocation(String location)
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

   /**
    * Get a bundle by symbolic name and version
    *
    * Note, this will get the bundle regadless of its state.
    * i.e. The returned bundle may have been UNINSTALLED
    *
    * @param symbolicName The bundle symbolic name
    * @param versionRange The optional bundle version
    * @return The bundle or null if there is no bundle with that name and version
    */
   public AbstractBundle getBundle(String symbolicName, String versionRange)
   {
      AbstractBundle result = null;
      for (AbstractBundle aux : getBundles())
      {
         if (aux.getSymbolicName().equals(symbolicName))
         {
            if (versionRange == null || XVersionRange.parse(versionRange).isInRange(aux.getVersion()))
            {
               result = aux;
               break;
            }
         }
      }
      return result;
   }

   /**
    * Get the list of installed bundles.
    * i.e. Bundles in state UNINSTALLED are not returned
    */
   public List<AbstractBundle> getBundles()
   {
      List<AbstractBundle> result = new ArrayList<AbstractBundle>();
      for (AbstractBundle aux : bundleMap.values())
      {
         if (aux.getState() != Bundle.UNINSTALLED)
            result.add(aux);
      }
      return Collections.unmodifiableList(result);
   }

   /**
    * Get the list of bundles that are in one of the given states.
    * If the states pattern is null, it returns all registered bundles.
    *
    * @param states The binary or combination of states or null
    */
   public List<AbstractBundle> getBundles(Integer states)
   {
      List<AbstractBundle> result = new ArrayList<AbstractBundle>();
      for (AbstractBundle aux : bundleMap.values())
      {
         if (states == null || (aux.getState() & states.intValue()) != 0)
            result.add(aux);
      }
      return Collections.unmodifiableList(result);
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
            File streamFile = plugin.storeBundleStream(location, input, 0);
            locationURL = streamFile.toURI().toURL();
         }
         catch (IOException ex)
         {
            throw new BundleException("Cannot store bundle from stream", ex);
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
      catch (Exception ex)
      {
         throw new BundleException("Invalid bundle location=" + locationURL, ex);
      }

      return install(root, location, false);
   }

   /**
    * Install a bundle from the given {@link VirtualFile}
    */
   private AbstractBundle install(VirtualFile rootFile, String location, boolean autoStart) throws BundleException
   {
      Deployment dep;
      try
      {
         BundleDeploymentPlugin plugin = getPlugin(BundleDeploymentPlugin.class);
         dep = plugin.createDeployment(rootFile, location);
      }
      catch (BundleException ex)
      {
         deleteContentRoot(rootFile);
         throw ex;
      }

      return installBundle(dep);
   }

   /**
    * Install a bundle from a {@link ModuleIdentifier}.
    * 
    * This method can be used to install plain modules or bundles to the {@link BundleManager}. 
    * A plain module is one that does not have a valid OSGi manifest. 
    * 
    * When installing a plain module:
    * 
    *    - module dependencies are not installed automatically
    *    - module may or may not have been loaded previously
    *    - module cannot be installed multiple times 
    */
   public Bundle installBundle(ModuleIdentifier identifier) throws BundleException
   {
      if (identifier == null)
         throw new IllegalArgumentException("Null identifier");

      // First check if this is a valid OSGi bundle
      BundleInfo info = null;
      try
      {
         VirtualFile rootFile = getRootFile(identifier);
         if (rootFile != null)
            info = BundleInfo.createBundleInfo(rootFile);
      }
      catch (BundleException ex)
      {
         // ignore
      }

      // If we have a valid bundle, install normally without loading the module
      if (info != null)
      {
         Deployment dep = DeploymentFactory.createDeployment(info);
         return installBundle(dep);
      }

      // Load the module
      Module module;
      try
      {
         ModuleLoader loader = Module.getCurrentLoader();
         module = loader.loadModule(identifier);
      }
      catch (ModuleLoadException ex)
      {
         throw new BundleException("Cannot load module: " + identifier, ex);
      }

      // Do a sanity check that this is not an OSGi bundle
      ModuleClassLoader classLoader = module.getClassLoader();
      InputStream inStream = classLoader.getResourceAsStream(JarFile.MANIFEST_NAME);
      if (inStream != null)
      {
         try
         {
            Manifest manifest = new Manifest();
            manifest.read(inStream);
            if (BundleInfo.isValidateBundleManifest(manifest))
               throw new BundleException("Cannot install bundle from loaded module: " + identifier);
         }
         catch (IOException ex)
         {
            throw new BundleException("Cannot read mainfest from: " + identifier, ex);
         }
      }

      String location = identifier.getName() + ":" + identifier.getSlot();
      String symbolicName = identifier.getName();
      Version version;
      try
      {
         version = Version.parseVersion(identifier.getSlot());
      }
      catch (IllegalArgumentException ex)
      {
         version = Version.emptyVersion;
      }

      // Build the resolver capabilities, which exports every package
      ResolverPlugin resolverPlugin = getPlugin(ResolverPlugin.class);
      XModuleBuilder builder = resolverPlugin.getModuleBuilder();
      builder.createModule(symbolicName, version, 0);
      builder.addBundleCapability(symbolicName, version);
      for (String path : module.getExportedPaths())
      {
         if (path.startsWith("/"))
            path = path.substring(1);
         if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
         if (path.startsWith("META-INF"))
            continue;

         String packageName = path.replace('/', '.');
         builder.addPackageCapability(packageName, null, null);
      }
      XModule resModule = builder.getModule();
      resModule.addAttachment(Module.class, module);
      
      Deployment dep = DeploymentFactory.createDeployment(location, symbolicName, version);
      dep.addAttachment(XModule.class, resModule);
      dep.addAttachment(Module.class, module);
      return installBundle(dep);
   }

   /**
    * Get virtual file for the singe jar that corresponds to the given identifier
    * @return The root virtual or null 
    */
   private VirtualFile getRootFile(ModuleIdentifier identifier)
   {
      File rootPath = new File(getProperty("module.path").toString());
      String identifierPath = identifier.getName().replace('.', File.separatorChar) + File.separator + identifier.getSlot();
      File moduleDir = new File(rootPath + File.separator + identifierPath);
      if (moduleDir.isDirectory() == false)
      {
         log.warnf("Cannot obtain module directory: %s", moduleDir);
         return null;
      }

      String[] files = moduleDir.list(new FilenameFilter()
      {
         @Override
         public boolean accept(File dir, String name)
         {
            return name.endsWith(".jar");
         }
      });
      if (files.length == 0)
      {
         log.warnf("Cannot find module jar in: %s", moduleDir);
         return null;
      }
      if (files.length > 1)
      {
         log.warnf("Multiple module jars in: %s", moduleDir);
         return null;
      }

      File moduleFile = new File(moduleDir + File.separator + files[0]);
      if (moduleFile.exists() == false)
      {
         log.warnf("Module file does not exist: %s", moduleFile);
         return null;
      }

      try
      {
         return AbstractVFS.getRoot(moduleFile.toURI().toURL());
      }
      catch (IOException ex)
      {
         log.errorf(ex, "Cannot obtain root file: %s", moduleFile);
         return null;
      }
   }

   /**
    * Install a bundle from a {@link Deployment}
    */
   public AbstractBundle installBundle(Deployment dep) throws BundleException
   {
      if (dep == null)
         throw new IllegalArgumentException("Null deployment");

      // If a bundle containing the same location identifier is already installed,
      // the Bundle object for that bundle is returned.
      AbstractBundle bundleState = getBundleByLocation(dep.getLocation());
      if (bundleState != null)
         return bundleState;

      try
      {
         bundleState = createBundle(dep);
      }
      catch (BundleException ex)
      {
         deleteContentRoot(dep.getRoot());
         throw ex;
      }

      addBundleState(bundleState);

      return bundleState;
   }

   private AbstractBundle createBundle(Deployment dep) throws BundleException
   {
      BundleDeploymentPlugin deploymentPlugin = getPlugin(BundleDeploymentPlugin.class);
      OSGiMetaData metadata = deploymentPlugin.createOSGiMetaData(dep);

      // Create the bundle state
      boolean isFragment = metadata.getFragmentHost() != null;
      AbstractBundle bundleState = (isFragment ? new FragmentBundle(this, dep) : new HostBundle(this, dep));
      dep.addAttachment(AbstractBundle.class, bundleState);

      // Validate the deployed bundle
      // The system bundle is not validated
      validateBundle(bundleState);

      // Process the Bundle-NativeCode header if there is one
      if (metadata.getBundleNativeCode() != null)
      {
         NativeCodePlugin nativeCodePlugin = getOptionalPlugin(NativeCodePlugin.class);
         if (nativeCodePlugin != null)
            nativeCodePlugin.deployNativeCode(dep);
      }

      return bundleState;
   }

   private void validateBundle(AbstractBundle bundleState) throws BundleException
   {
      OSGiMetaData osgiMetaData = bundleState.getOSGiMetaData();
      if (osgiMetaData == null)
         return;

      BundleValidator validator;

      // Delegate to the validator for the appropriate revision
      if (osgiMetaData.getBundleManifestVersion() > 1)
         validator = new BundleValidatorR4(this);
      else
         validator = new BundleValidatorR3(this);

      validator.validateBundle(bundleState);
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

   void deleteContentRoot(VirtualFile rootFile)
   {
      if (rootFile != null)
      {
         String contentRootPath = rootFile.getPathName();
         rootFile.close();

         File streamDir = getPlugin(BundleStoragePlugin.class).getBundleStreamDir();
         if (contentRootPath.startsWith(streamDir.getAbsolutePath()))
         {
            File file = new File(contentRootPath);
            file.delete();
         }
      }
   }
}
