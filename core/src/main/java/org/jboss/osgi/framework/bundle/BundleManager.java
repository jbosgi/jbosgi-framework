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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.osgi.deployment.deployer.Deployment;
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
import org.jboss.osgi.framework.plugin.internal.DefaultDeployerServicePlugin;
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
import org.jboss.osgi.resolver.XVersionRange;
import org.jboss.osgi.spi.util.SysPropertyActions;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;

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
   // The registered plugins
   private Map<Class<? extends Plugin>, Plugin> plugins = new LinkedHashMap<Class<? extends Plugin>, Plugin>();
   // The default ModuleLoader
   private final ModuleLoader systemModuleLoader;
   // The ServiceContainer
   private ServiceContainer serviceContainer;
   // The Framework state
   private FrameworkState frameworkState;
   // Flag that indicates the first init of this instance
   private boolean firstInit = true;

   // The Framework integration mode
   public enum IntegrationMode
   {
      STANDALONE, CONTAINER
   }

   public BundleManager(Map<String, Object> initialProperties)
   {
      // Log INFO about this implementation
      String implTitle = getClass().getPackage().getImplementationTitle();
      String implVersion = getClass().getPackage().getImplementationVersion();
      log.infof(implTitle + " - " + implVersion);

      // The properties on the BundleManager are mutable as long the framework is not created
      // Plugins may modify these properties in their respective constructor
      if (initialProperties != null)
         properties.putAll(initialProperties);

      // Initialize the default module loader
      ModuleLoader mlProp = (ModuleLoader)getProperty(ModuleLoader.class.getName());
      systemModuleLoader = mlProp != null ? mlProp : Module.getSystemModuleLoader();

      // Get/Create the service container
      ServiceContainer scProp = (ServiceContainer)getProperty(ServiceContainer.class.getName());
      serviceContainer = scProp != null ? scProp : ServiceContainer.Factory.create();

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
      plugins.put(DeployerServicePlugin.class, new DefaultDeployerServicePlugin(this));
      plugins.put(LifecycleInterceptorPlugin.class, new LifecycleInterceptorPluginImpl(this));
      plugins.put(WebXMLVerifierInterceptor.class, new WebXMLVerifierInterceptor(this));
      plugins.put(PackageAdminPlugin.class, new PackageAdminPluginImpl(this));
      plugins.put(StartLevelPlugin.class, new StartLevelPluginImpl(this));

      // Finally add the AutoInstallPlugin
      plugins.put(AutoInstallPlugin.class, new AutoInstallPluginImpl(this));

      // Cleanup the storage area
      BundleStoragePlugin storagePlugin = getPlugin(BundleStoragePlugin.class);
      String storageClean = (String)getProperty(Constants.FRAMEWORK_STORAGE_CLEAN);
      if (firstInit == true && Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(storageClean))
         storagePlugin.cleanStorage();

      // Create the Framework state
      frameworkState = new FrameworkState(this);
      firstInit = false;
   }

   public FrameworkState getFrameworkState()
   {
      return frameworkState;
   }

   public SystemBundle getSystemBundle()
   {
      return frameworkState;
   }

   public ModuleLoader getSystemModuleLoader()
   {
      return systemModuleLoader;
   }

   public ServiceContainer getServiceContainer()
   {
      return serviceContainer;
   }

   public IntegrationMode getIntegrationMode()
   {
      Object value = getProperty(IntegrationMode.class.getName());
      return value != null ? (IntegrationMode)value : IntegrationMode.STANDALONE;
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

      log.infof("Install bundle: %s", bundleState);

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

   public void uninstallBundle(Deployment dep) throws BundleException
   {
      AbstractUserBundle bundleState = AbstractUserBundle.assertBundleState(dep.getAttachment(Bundle.class));
      bundleState.uninstallInternal();
   }

   void uninstallBundle(AbstractUserBundle bundleState)
   {
      bundleState.changeState(Bundle.UNINSTALLED, 0);
      List<AbstractBundle> uninstalled = getBundles(Bundle.UNINSTALLED);
      for (AbstractBundle aux : uninstalled)
      {
         AbstractUserBundle userBundle = AbstractUserBundle.assertBundleState(aux);
         if (userBundle.hasActiveWires() == false)
            userBundle.remove();
      }
      bundleState.fireBundleEvent(BundleEvent.UNINSTALLED);
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
    * Add a plugin to the bundle manager
    * @return The previous plugin that was registered for the given key, or null.
    */
   @SuppressWarnings("unchecked")
   public <T extends Plugin> T addPlugin(Class<T> clazz, T plugin)
   {
      return (T)plugins.put(clazz, plugin);
   }

   /**
    * Install a bundle from a given location and {@link ModuleIdentifier}.
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
   public Bundle installBundle(String location, ModuleIdentifier identifier) throws BundleException
   {
      BundleDeploymentPlugin plugin = getPlugin(BundleDeploymentPlugin.class);
      Deployment dep = plugin.createDeployment(location, identifier);
      return installBundle(dep);
   }

   /**
    * Install a bundle from a {@link ModuleIdentifier}.
    */
   public Bundle installBundle(ModuleIdentifier identifier) throws BundleException
   {
      BundleDeploymentPlugin plugin = getPlugin(BundleDeploymentPlugin.class);
      Deployment dep = plugin.createDeployment(identifier);
      return installBundle(dep);
   }

   /**
    * Install a bundle from a {@link Deployment}
    */
   public AbstractBundle installBundle(Deployment dep) throws BundleException
   {
      if (dep == null)
         throw new IllegalArgumentException("Null deployment");

      // Setup the bundle storage area if not done so already
      BundleStorageState storageState = dep.getAttachment(BundleStorageState.class);
      if (storageState == null)
      {
         try
         {
            BundleStoragePlugin plugin = getPlugin(BundleStoragePlugin.class);
            storageState = plugin.createStorageState(getNextBundleId(), dep.getLocation(), dep.getRoot());
            dep.addAttachment(BundleStorageState.class, storageState);
         }
         catch (IOException ex)
         {
            throw new BundleException("Cannot setup storage for: " + dep, ex);
         }
      }

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
         BundleStorageState bundleStorage = storageState;
         if (bundleStorage != null)
            bundleStorage.deleteRevisionStorage();
         throw ex;
      }

      addBundleState(bundleState);
      return bundleState;
   }

   public long getNextBundleId()
   {
      return identityGenerator.incrementAndGet();
   }

   void installPersistedBundles(List<BundleStorageState> storageStates)
   {
      BundleDeploymentPlugin deploymentPlugin = getPlugin(BundleDeploymentPlugin.class);
      for (BundleStorageState storageState : storageStates)
      {
         long bundleId = storageState.getBundleId();
         if (bundleId == 0)
            continue;

         try
         {
            Deployment dep = deploymentPlugin.createDeployment(storageState);
            installBundle(dep);
         }
         catch (BundleException ex)
         {
            log.errorf(ex, "Cannot install persistet bundle: %s", storageState);
         }
      }
   }

   private AbstractBundle createBundle(Deployment dep) throws BundleException
   {
      BundleDeploymentPlugin deploymentPlugin = getPlugin(BundleDeploymentPlugin.class);
      OSGiMetaData metadata = deploymentPlugin.createOSGiMetaData(dep);

      // Create the bundle state
      boolean isFragment = metadata.getFragmentHost() != null;
      AbstractBundle bundleState = (isFragment ? new FragmentBundle(this, dep) : new HostBundle(this, dep));
      dep.addAttachment(Bundle.class, bundleState);

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

   void destroy()
   {
      // Destroy Plugins Lifecycle
      List<Plugin> reversePlugins = new ArrayList<Plugin>(getPlugins());
      Collections.reverse(reversePlugins);
      for (Plugin plugin : reversePlugins)
      {
         try
         {
            plugin.destroyPlugin();
         }
         catch (RuntimeException ex)
         {
            log.errorf(ex, "Cannot destroy plugin: %s", plugin);
         }
      }

      // Clear out the bundles
      bundleMap.clear();
   }
}
