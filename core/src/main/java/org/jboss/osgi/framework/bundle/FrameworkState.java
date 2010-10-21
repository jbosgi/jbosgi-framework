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
package org.jboss.osgi.framework.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.framework.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.framework.plugin.Plugin;
import org.jboss.osgi.framework.plugin.ResolverPlugin;
import org.jboss.osgi.framework.plugin.StartLevelPlugin;
import org.jboss.osgi.framework.util.Java;
import org.jboss.osgi.spi.util.ConstantsHelper;
import org.jboss.osgi.spi.util.SysPropertyActions;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

/**
 * The Framework state.
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Aug-2009
 */
public class FrameworkState extends SystemBundle implements Framework
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

   // The Framework wrapper
   private FrameworkWrapper frameworkWrapper;
   // The framework stop monitor
   private AtomicBoolean stopMonitor = new AtomicBoolean(false);
   // The framework stopped event
   private int stoppedEvent = FrameworkEvent.STOPPED;
   // The framework executor
   private Executor executor = Executors.newFixedThreadPool(10);
   // Flag that indicates the first init of this instance
   private boolean firstInit = true;

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

            OSGi_FRAMEWORK_OS_NAME = SysPropertyActions.getProperty("os.name", null);
            OSGi_FRAMEWORK_OS_VERSION = SysPropertyActions.getProperty("os.version", null);
            OSGi_FRAMEWORK_PROCESSOR = SysPropertyActions.getProperty("os.arch", null);

            System.setProperty("org.osgi.vendor.framework", "org.jboss.osgi.framework");
            return null;
         }
      });
   }

   FrameworkState(BundleManager bundleManager)
   {
      super(bundleManager);

      // Init default framework properties
      if (bundleManager.getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT) == null)
         bundleManager.setProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, OSGi_FRAMEWORK_EXECUTIONENVIRONMENT);
      if (bundleManager.getProperty(Constants.FRAMEWORK_LANGUAGE) == null)
         bundleManager.setProperty(Constants.FRAMEWORK_LANGUAGE, OSGi_FRAMEWORK_LANGUAGE);
      if (bundleManager.getProperty(Constants.FRAMEWORK_OS_NAME) == null)
         bundleManager.setProperty(Constants.FRAMEWORK_OS_NAME, OSGi_FRAMEWORK_OS_NAME);
      if (bundleManager.getProperty(Constants.FRAMEWORK_OS_VERSION) == null)
         bundleManager.setProperty(Constants.FRAMEWORK_OS_VERSION, OSGi_FRAMEWORK_OS_VERSION);
      if (bundleManager.getProperty(Constants.FRAMEWORK_PROCESSOR) == null)
         bundleManager.setProperty(Constants.FRAMEWORK_PROCESSOR, OSGi_FRAMEWORK_PROCESSOR);
      if (bundleManager.getProperty(Constants.FRAMEWORK_VENDOR) == null)
         bundleManager.setProperty(Constants.FRAMEWORK_VENDOR, OSGi_FRAMEWORK_VENDOR);
      if (bundleManager.getProperty(Constants.FRAMEWORK_VERSION) == null)
         bundleManager.setProperty(Constants.FRAMEWORK_VERSION, OSGi_FRAMEWORK_VERSION);
   }

   public Framework getBundleWrapper()
   {
      if (frameworkWrapper == null)
         frameworkWrapper = new FrameworkWrapper(this);
      return frameworkWrapper;
   }

   public Map<String, Object> getProperties()
   {
      Map<String, Object> properties = getBundleManager().getProperties();
      return Collections.unmodifiableMap(properties);
   }

   public String getProperty(String key)
   {
      Object value = getBundleManager().getProperty(key);
      return (value instanceof String ? (String)value : null);
   }

   public void setProperty(String key, Object value)
   {
      getBundleManager().setProperty(key, value);
   }

   /**
    * True when the {@link this} is active.
    */
   boolean isFrameworkActive()
   {
      // We are active if the system bundle is ACTIVE
      return getState() == Bundle.ACTIVE;
   }

   /**
    * Assert that the {@link this} is active.
    * @throws IllegalStateException if not
    */
   void assertFrameworkActive()
   {
      int systemState = getState();
      if (systemState != Bundle.ACTIVE)
         throw new IllegalStateException("this not ACTIVE, it is: " + ConstantsHelper.bundleState(systemState));
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
   @Override
   public void init() throws BundleException
   {
      // This method does nothing if called when this Framework is in the STARTING, ACTIVE or STOPPING state
      int state = getState();
      if (state == Bundle.STARTING || state == Bundle.ACTIVE || state == Bundle.STOPPING)
         return;

      // Put into the STARTING state
      changeState(Bundle.STARTING);

      // Create the system bundle context
      createBundleContext();

      // Have event handling enabled
      BundleManager bundleManager = getBundleManager();
      FrameworkEventsPlugin eventsPlugin = bundleManager.getPlugin(FrameworkEventsPlugin.class);
      eventsPlugin.setActive(true);

      // Init Plugins Lifecycle
      for (Plugin plugin : bundleManager.getPlugins())
         plugin.initPlugin();

      // Cleanup the storage area
      String storageClean = getProperty(Constants.FRAMEWORK_STORAGE_CLEAN);
      BundleStoragePlugin storagePlugin = bundleManager.getPlugin(BundleStoragePlugin.class);
      if (firstInit == true && Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(storageClean))
         storagePlugin.cleanStorage();

      // Install the persisted bundles
      try
      {
         List<BundleStorageState> storageStates = storagePlugin.getBundleStorageStates();
         bundleManager.installPersistedBundles(storageStates);
      }
      catch (IOException ex)
      {
         throw new BundleException("Cannot install persisted bundles", ex);
      }

      firstInit = false;
      log.debug("Framework initialized");
   }

   @Override
   public void start() throws BundleException
   {
      start(0);
   }

   @Override
   public void start(int options) throws BundleException
   {
      // If this Framework is not in the STARTING state, initialize this Framework
      if (getState() != Bundle.STARTING)
         init();

      // Resolve the system bundle
      ResolverPlugin resolver = getBundleManager().getPlugin(ResolverPlugin.class);
      resolver.resolve(getResolverModule());

      // This Framework's state is set to ACTIVE
      changeState(Bundle.ACTIVE);

      // Start Plugins Lifecycle
      for (Plugin plugin : getBundleManager().getPlugins())
         plugin.startPlugin();

      // Increase to initial start level
      StartLevelPlugin startLevel = getBundleManager().getOptionalPlugin(StartLevelPlugin.class);
      if (startLevel != null)
         startLevel.increaseStartLevel(getBeginningStartLevel());

      // A framework event of type STARTED is fired
      FrameworkEventsPlugin plugin = getBundleManager().getPlugin(FrameworkEventsPlugin.class);
      plugin.fireFrameworkEvent(this, FrameworkEvent.STARTED, null);

      log.info("Framework started");
   }

   private int getBeginningStartLevel()
   {
      String beginning = getBundleManager().getSystemContext().getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
      if (beginning == null)
         return 1;

      try
      {
         return Integer.parseInt(beginning);
      }
      catch (NumberFormatException nfe)
      {
         log.errorf("Could not set beginning start level to: %s", beginning);
         return 1;
      }
   }

   @Override
   public void stop() throws BundleException
   {
      stop(0);
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
   @Override
   public void stop(int options)
   {
      int state = getState();
      if (state != Bundle.STARTING && state != Bundle.ACTIVE)
         return;

      Runnable cmd = new Runnable()
      {
         public void run()
         {
            try
            {
               stopInternal(false);
            }
            catch (Exception ex)
            {
               log.errorf(ex, "Error stopping framework");
            }
         }
      };
      executor.execute(cmd);
   }

   @Override
   public void update(InputStream input) throws BundleException
   {
      if (input != null)
      {
         try
         {
            input.close();
         }
         catch (IOException ex)
         {
            // ignore
         }
      }
      update();
   }

   @Override
   public void update()
   {
      int state = getState();
      if (state != Bundle.STARTING && state != Bundle.ACTIVE)
         return;

      final int targetState = getState();
      Runnable cmd = new Runnable()
      {
         public void run()
         {
            try
            {
               stopInternal(true);
               if (targetState == Bundle.STARTING)
                  init();
               if (targetState == Bundle.ACTIVE)
                  start();
            }
            catch (Exception ex)
            {
               log.errorf(ex, "Error stopping framework");
            }
         }
      };
      executor.execute(cmd);
   }

   private void stopInternal(boolean stopForUpdate)
   {
      try
      {
         synchronized (stopMonitor)
         {
            // If the Framework is not STARTING and not ACTIVE there is nothing to do
            int state = getState();
            if (state != Bundle.STARTING && state != Bundle.ACTIVE)
               return;

            stoppedEvent = stopForUpdate ? FrameworkEvent.STOPPED_UPDATE : FrameworkEvent.STOPPED;
            changeState(Bundle.STOPPING);
         }

         // Move to start level 0 in the current thread
         StartLevelPlugin startLevel = getBundleManager().getOptionalPlugin(StartLevelPlugin.class);
         if (startLevel != null)
         {
            startLevel.decreaseStartLevel(0);
         }
         else
         {
            // No Start Level Service available, stop all bundles individually...
            // All installed bundles must be stopped without changing each bundle's persistent autostart setting
            for (AbstractBundle bundleState : getBundleManager().getBundles())
            {
               if (bundleState != this)
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
                     getBundleManager().fireError(bundleState, "stopping bundle", ex);
                  }
               }
            }
         }

         // Stop Plugins Lifecycle
         List<Plugin> reversePlugins = new ArrayList<Plugin>(getBundleManager().getPlugins());
         Collections.reverse(reversePlugins);
         for (Plugin plugin : reversePlugins)
         {
            try
            {
               plugin.stopPlugin();
            }
            catch (RuntimeException ex)
            {
               log.errorf(ex, "Cannot stop plugin: %s", plugin);
            }
         }

         // Event handling is disabled
         FrameworkEventsPlugin eventsPlugin = getBundleManager().getPlugin(FrameworkEventsPlugin.class);
         eventsPlugin.setActive(false);

         // This Framework's state is set to Bundle.RESOLVED
         changeState(Bundle.RESOLVED);

         // Destroy the BundeleManager
         getBundleManager().destroy();

         // All resources held by this Framework are released
         destroyBundleContext();

         log.info("Framework stopped");
      }
      finally
      {
         synchronized (stopMonitor)
         {
            stopMonitor.notifyAll();
         }
      }
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
   @Override
   public FrameworkEvent waitForStop(long timeout) throws InterruptedException
   {
      synchronized (stopMonitor)
      {
         // Only wait when this Framework is STARTING, ACTIVE, or STOPPING
         int state = getState();
         if (state == Bundle.STARTING || state == Bundle.ACTIVE || state == Bundle.STOPPING)
         {
            stopMonitor.wait(timeout);
         }
         else
         {
            return new FrameworkEvent(stoppedEvent, this, null);
         }
      }

      if (getState() != Bundle.RESOLVED)
         return new FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, this, null);

      return new FrameworkEvent(stoppedEvent, this, null);
   }

   /**
    * The Framework cannot be uninstalled.
    * <p>
    * This method always throws a BundleException.
    */
   @Override
   public void uninstall() throws BundleException
   {
      throw new BundleException("The system bundle cannot be uninstalled");
   }
}