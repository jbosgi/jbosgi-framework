/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.container.bundle;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.plugin.AutoInstallPlugin;
import org.jboss.osgi.container.plugin.BundleStoragePlugin;
import org.jboss.osgi.container.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.container.plugin.Plugin;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.container.plugin.StartLevelPlugin;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.spi.util.ConstantsHelper;
import org.jboss.util.platform.Java;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;

/**
 * The Framework state.
 * 
 * @author thomas.diesler@jboss.com
 * @since 21-Aug-2009
 */
public class FrameworkState
{
   // Provide logging
   final Logger log = Logger.getLogger(FrameworkState.class);

   // The framework execution environment 
   private static String OSGi_FRAMEWORK_EXECUTIONENVIRONMENT;
   // The framework language 
   private static String OSGi_FRAMEWORK_LANGUAGE = Locale.getDefault().getISO3Language(); // REVIEW correct?
   // The os name 
   private static String OSGi_FRAMEWORK_OS_NAME;
   // The os version 
   private static String OSGi_FRAMEWORK_OS_VERSION;
   // The os version 
   private static String OSGi_FRAMEWORK_PROCESSOR;
   // The framework vendor 
   private static String OSGi_FRAMEWORK_VENDOR = "jboss.org";
   // The framework version. This is the version of the org.osgi.framework package in r4v42 
   private static String OSGi_FRAMEWORK_VERSION = "1.5";

   // The bundle manager
   private BundleManager bundleManager;
   // The framework properties
   private Map<String, String> properties;
   // The framework stop monitor
   private AtomicInteger stopMonitor = new AtomicInteger(0);
   // The framework stop executor 
   private Executor stopExecutor = Executors.newFixedThreadPool(10);

   static
   {
      AccessController.doPrivileged(new PrivilegedAction<Object>()
      {
         public Object run()
         {
            List<String> execEnvironments = new ArrayList<String>();
            if (Java.isCompatible(Java.VERSION_1_5))
               execEnvironments.add("J2SE-1.5");
            if (Java.isCompatible(Java.VERSION_1_6))
               execEnvironments.add("JavaSE-1.6");

            String envlist = execEnvironments.toString();
            envlist = envlist.substring(1, envlist.length() - 1);
            OSGi_FRAMEWORK_EXECUTIONENVIRONMENT = envlist;

            OSGi_FRAMEWORK_OS_NAME = System.getProperty("os.name");
            OSGi_FRAMEWORK_OS_VERSION = System.getProperty("os.version");
            OSGi_FRAMEWORK_PROCESSOR = System.getProperty("os.arch");

            System.setProperty("org.osgi.vendor.framework", "org.jboss.osgi.framework");
            return null;
         }
      });
   }

   FrameworkState(BundleManager bundleManager, Map<String, String> props)
   {
      if (bundleManager == null)
         throw new IllegalArgumentException("Null bundleManager");

      this.bundleManager = bundleManager;

      // Initialize the framework properties
      properties = new HashMap<String, String>();
      if (props != null)
         properties.putAll(props);

      // Init default framework properties
      if (getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT) == null)
         setProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, OSGi_FRAMEWORK_EXECUTIONENVIRONMENT);
      if (getProperty(Constants.FRAMEWORK_LANGUAGE) == null)
         setProperty(Constants.FRAMEWORK_LANGUAGE, OSGi_FRAMEWORK_LANGUAGE);
      if (getProperty(Constants.FRAMEWORK_OS_NAME) == null)
         setProperty(Constants.FRAMEWORK_OS_NAME, OSGi_FRAMEWORK_OS_NAME);
      if (getProperty(Constants.FRAMEWORK_OS_VERSION) == null)
         setProperty(Constants.FRAMEWORK_OS_VERSION, OSGi_FRAMEWORK_OS_VERSION);
      if (getProperty(Constants.FRAMEWORK_PROCESSOR) == null)
         setProperty(Constants.FRAMEWORK_PROCESSOR, OSGi_FRAMEWORK_PROCESSOR);
      if (getProperty(Constants.FRAMEWORK_VENDOR) == null)
         setProperty(Constants.FRAMEWORK_VENDOR, OSGi_FRAMEWORK_VENDOR);
      if (getProperty(Constants.FRAMEWORK_VERSION) == null)
         setProperty(Constants.FRAMEWORK_VERSION, OSGi_FRAMEWORK_VERSION);
   }

   public SystemBundle getSystemBundle()
   {
      return bundleManager.getSystemBundle();
   }

   public Map<String, String> getProperties()
   {
      return Collections.unmodifiableMap(properties);
   }

   public String getProperty(String key)
   {
      Object value = properties.get(key);
      if (value == null)
         value = System.getProperty(key);

      if (value instanceof String == false)
         return null;

      return (String)value;
   }

   public void setProperty(String key, String value)
   {
      SystemBundle sysBundle = getSystemBundle();
      if (sysBundle != null && sysBundle.getState() != Bundle.INSTALLED)
         throw new IllegalStateException("Cannot add property to ACTIVE framwork");

      properties.put(key, value);
   }

   /**
    * True when the {@link getSystemBundle()} is active.
    */
   boolean isFrameworkActive()
   {
      // We are active if the system bundle is ACTIVE
      return getSystemBundle().getState() == Bundle.ACTIVE;
   }

   /**
    * Assert that the {@link getSystemBundle()} is active.
    * @throws IllegalStateException if not
    */
   void assertFrameworkActive()
   {
      int systemState = getSystemBundle().getState();
      if (systemState != Bundle.ACTIVE)
         throw new IllegalStateException("getSystemBundle() not ACTIVE, it is: " + ConstantsHelper.bundleState(systemState));
   }

   /**
    * Initialize this Framework. 
    * 
    * After calling this method, this Framework must:
    * - Be in the Bundle.STARTING state.
    * - Have a valid Bundle Context.
    * - Be at start level 0.
    * - Have event handling enabled.
    * - Have reified Bundle objects for all installed bundles.
    * - Have registered any framework services. For example, PackageAdmin, ConditionalPermissionAdmin, StartLevel.
    * 
    * This Framework will not actually be started until start is called.
    * 
    * This method does nothing if called when this Framework is in the Bundle.STARTING, Bundle.ACTIVE or Bundle.STOPPING states. 
    */
   public void initFramework() throws BundleException
   {
      // Log INFO about this implementation
      String implTitle = getClass().getPackage().getImplementationTitle();
      String implVersion = getClass().getPackage().getImplementationVersion();
      log.info(implTitle + " - " + implVersion);

      int state = getSystemBundle().getState();

      // This method does nothing if called when this Framework is in the STARTING, ACTIVE or STOPPING state
      if (state == Bundle.STARTING || state == Bundle.ACTIVE || state == Bundle.STOPPING)
         return;

      // Put into the STARTING state
      getSystemBundle().changeState(Bundle.STARTING);

      // Create the system bundle context
      getSystemBundle().createBundleContext();

      // Have event handling enabled
      FrameworkEventsPlugin eventsPlugin = bundleManager.getPlugin(FrameworkEventsPlugin.class);
      eventsPlugin.setActive(true);

      // Init Plugins Lifecycle
      for (Plugin plugin : bundleManager.getPlugins())
      {
         plugin.initPlugin();
      }

      // Cleanup the storage area
      String storageClean = getProperty(Constants.FRAMEWORK_STORAGE_CLEAN);
      BundleStoragePlugin storagePlugin = bundleManager.getOptionalPlugin(BundleStoragePlugin.class);
      if (storagePlugin != null)
         storagePlugin.cleanStorage(storageClean);
   }

   public void startFramework() throws BundleException
   {
      // If this Framework is not in the STARTING state, initialize this Framework
      if (getSystemBundle().getState() != Bundle.STARTING)
         initFramework();

      // Start Plugins Lifecycle
      for (Plugin plugin : bundleManager.getPlugins())
      {
         plugin.startPlugin();
      }

      // All installed bundles must be started
      AutoInstallPlugin autoInstall = bundleManager.getOptionalPlugin(AutoInstallPlugin.class);
      if (autoInstall != null)
      {
         autoInstall.installBundles();
         autoInstall.startBundles();
      }

      // Resolve the system bundle
      ResolverPlugin resolver = bundleManager.getPlugin(ResolverPlugin.class);
      resolver.resolve(getSystemBundle());

      // This Framework's state is set to ACTIVE
      getSystemBundle().changeState(Bundle.ACTIVE);

      // Increase to initial start level
      StartLevelPlugin startLevel = bundleManager.getOptionalPlugin(StartLevelPlugin.class);
      if (startLevel != null)
         startLevel.increaseStartLevel(getBeginningStartLevel());

      // A framework event of type STARTED is fired
      FrameworkEventsPlugin plugin = bundleManager.getPlugin(FrameworkEventsPlugin.class);
      plugin.fireFrameworkEvent(getSystemBundle(), FrameworkEvent.STARTED, null);
   }

   private int getBeginningStartLevel()
   {
      String beginning = bundleManager.getSystemContext().getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
      if (beginning == null)
         return 1;

      try
      {
         return Integer.parseInt(beginning);
      }
      catch (NumberFormatException nfe)
      {
         log.error("Could not set beginning start level to: '" + beginning + "'");
         return 1;
      }
   }

   /**
    * Stop this Framework.
    * 
    * The method returns immediately to the caller after initiating the following steps to be taken on another thread.
    * 
    * 1. This Framework's state is set to Bundle.STOPPING.
    * 2. All installed bundles must be stopped without changing each bundle's persistent autostart setting. 
    * 3. Unregister all services registered by this Framework.
    * 4. Event handling is disabled.
    * 5. This Framework's state is set to Bundle.RESOLVED.
    * 6. All resources held by this Framework are released. This includes threads, bundle class loaders, open files, etc.
    * 7. Notify all threads that are waiting at waitForStop that the stop operation has completed.
    * 
    * After being stopped, this Framework may be discarded, initialized or started. 
    */
   public void stopFramework()
   {
      Runnable stopcmd = new Runnable()
      {
         public void run()
         {
            try
            {
               stopInternal();
            }
            catch (Exception ex)
            {
               log.error("Error stopping framework", ex);
            }
         }
      };
      stopExecutor.execute(stopcmd);
   }

   private void stopInternal()
   {
      synchronized (stopMonitor)
      {
         // Do nothing if the framework is not active
         if (getSystemBundle().getState() != Bundle.ACTIVE)
            return;

         // This Framework's state is set to Bundle.STOPPING
         getSystemBundle().changeState(Bundle.STOPPING);
      }

      // Move to start level 0 in the current thread
      StartLevelPlugin startLevel = bundleManager.getOptionalPlugin(StartLevelPlugin.class);
      if (startLevel != null)
         startLevel.decreaseStartLevel(0);
      else
      {
         // No Start Level Service available, stop all bundles individually...
         // All installed bundles must be stopped without changing each bundle's persistent autostart setting
         for (AbstractBundle bundleState : bundleManager.getBundles())
         {
            if (bundleState != getSystemBundle())
            {
               try
               {
                  // [TODO] don't change the  persistent state
                  bundleState.stop();
               }
               catch (Exception ex)
               {
                  // Any exceptions that occur during bundle stopping must be wrapped in a BundleException and then 
                  // published as a framework event of type FrameworkEvent.ERROR
                  bundleManager.fireError(bundleState, "stopping bundle", ex);
               }
            }
         }
      }

      // Stop Plugins Lifecycle
      List<Plugin> reversePlugins = new ArrayList<Plugin>(bundleManager.getPlugins());
      Collections.reverse(reversePlugins);
      for (Plugin plugin : reversePlugins)
      {
         try
         {
            plugin.stopPlugin();
         }
         catch (RuntimeException ex)
         {
            log.error("Cannot stop service: " + plugin, ex);
         }
      }

      // Event handling is disabled
      FrameworkEventsPlugin eventsPlugin = bundleManager.getPlugin(FrameworkEventsPlugin.class);
      eventsPlugin.setActive(false);

      // This Framework's state is set to Bundle.RESOLVED
      getSystemBundle().changeState(Bundle.RESOLVED);

      // Destroy Plugins Lifecycle
      for (Plugin plugin : reversePlugins)
      {
         try
         {
            plugin.destroyPlugin();
         }
         catch (RuntimeException ex)
         {
            log.error("Cannot destroy service: " + plugin, ex);
         }
      }

      // All resources held by this Framework are released
      getSystemBundle().destroyBundleContext();

      // Notify all threads that are waiting at waitForStop that the stop operation has completed
      synchronized (stopMonitor)
      {
         stopMonitor.notifyAll();
      }
   }

   public void restartFramework()
   {
      throw new NotImplementedException();
   }

   /**
    * Wait until this Framework has completely stopped. 
    * 
    * The stop and update methods on a Framework performs an asynchronous stop of the Framework. 
    * This method can be used to wait until the asynchronous stop of this Framework has completed. 
    * This method will only wait if called when this Framework is in the Bundle.STARTING, Bundle.ACTIVE, or Bundle.STOPPING states. 
    * Otherwise it will return immediately.
    * 
    * A Framework Event is returned to indicate why this Framework has stopped.
    */
   public FrameworkEvent waitForStop(long timeout) throws InterruptedException
   {
      synchronized (stopMonitor)
      {
         // Only wait when this Framework is in Bundle.STARTING, Bundle.ACTIVE, or Bundle.STOPPING state
         int state = getSystemBundle().getState();
         if (state != Bundle.STARTING && state != Bundle.ACTIVE && state != Bundle.STOPPING)
            return new FrameworkEvent(FrameworkEvent.STOPPED, getSystemBundle(), null);

         stopMonitor.wait(timeout);
      }

      if (getSystemBundle().getState() != Bundle.RESOLVED)
         return new FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, getSystemBundle(), null);

      return new FrameworkEvent(FrameworkEvent.STOPPED, getSystemBundle(), null);
   }
}