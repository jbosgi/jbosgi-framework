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
package org.jboss.osgi.msc.bundle;

// $Id$

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.osgi.msc.plugin.Plugin;
import org.jboss.osgi.msc.plugin.ServicePlugin;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.spi.util.ConstantsHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;

/**
 * An impementation of an OSGi Framework
 * 
 * @author thomas.diesler@jboss.com
 * @since 21-Aug-2009
 */
public final class FrameworkState
{
   // Provide logging
   final Logger log = Logger.getLogger(FrameworkState.class);

   // The bundle manager
   private BundleManager bundleManager;
   // The sytem bundle
   private SystemBundle systemBundle;
   // The framework stop monitor
   private AtomicInteger stopMonitor = new AtomicInteger(0);
   // The framework stop executor 
   private Executor stopExecutor = Executors.newFixedThreadPool(10);
   /// The registered plugins 
   private Map<Class<? extends Plugin>, Plugin> plugins = Collections.synchronizedMap(new LinkedHashMap<Class<? extends Plugin>, Plugin>());

   FrameworkState(BundleManager bundleManager, Map<Class<? extends Plugin>, Plugin> plugins, Map<String, String> props)
   {
      this.bundleManager = bundleManager;
      this.plugins = plugins;
      
      this.systemBundle = new SystemBundle(bundleManager, props);
   }

   public SystemBundle getSystemBundle()
   {
      return systemBundle;
   }

   public String getProperty(String key)
   {
      assertFrameworkActive();
      BundleContext bundleContext = systemBundle.getBundleContext();
      return bundleContext.getProperty(key);
   }

   /**
    * True when the {@link SystemBundle} is active.
    */
   boolean isFrameworkActive()
   {
      // We are active if the system bundle is ACTIVE
      return systemBundle.getState() == Bundle.ACTIVE;
   }
   
   /**
    * Assert that the {@link SystemBundle} is active.
    * @throws IllegalStateException if not
    */
   void assertFrameworkActive()
   {
      int systemState = systemBundle.getState();
      if (systemState != Bundle.ACTIVE)
         throw new IllegalStateException("SystemBundle not ACTIVE, it is: " + ConstantsHelper.bundleState(systemState));
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

      int state = systemBundle.getState();

      // This method does nothing if called when this Framework is in the STARTING, ACTIVE or STOPPING state
      if (state == Bundle.STARTING || state == Bundle.ACTIVE || state == Bundle.STOPPING)
         return;

      // Put into the STARTING state
      systemBundle.changeState(Bundle.STARTING);

      // Create the system bundle context
      systemBundle.start();

      // [TODO] Have event handling enabled
      //FrameworkEventsPlugin eventsPlugin = getPlugin(FrameworkEventsPlugin.class);
      //eventsPlugin.setActive(true);

      // [TODO] Have registered any framework services.
      //for (Plugin plugin : new ArrayList<Plugin>(plugins.values()))
      //{
      //   if (plugin instanceof ServicePlugin)
      //   {
      //      ServicePlugin servicePlugin = (ServicePlugin)plugin;
      //      servicePlugin.startService();
      //   }
      //}

      // [TODO] Cleanup the storage area
      //String storageClean = properties.getProperty(Constants.FRAMEWORK_STORAGE_CLEAN);
      //BundleStoragePlugin storagePlugin = getOptionalPlugin(BundleStoragePlugin.class);
      //if (storagePlugin != null)
      //   storagePlugin.cleanStorage(storageClean);
   }

   public void startFramework() throws BundleException
   {
      // If this Framework is not in the STARTING state, initialize this Framework
      if (systemBundle.getState() != Bundle.STARTING)
         initFramework();

      // [TODO] All installed bundles must be started
      //AutoInstallPlugin autoInstall = getOptionalPlugin(AutoInstallPlugin.class);
      //if (autoInstall != null)
      //{
      //   autoInstall.installBundles();
      //   autoInstall.startBundles();
      //}

      // [TODO] Add the system bundle to the resolver
      //ResolverPlugin resolver = getOptionalPlugin(ResolverPlugin.class);
      //if (resolver != null)
      //{
      //   resolver.addBundle(systemBundle);
      //   resolver.resolve(Collections.singletonList(systemBundle.getBundleInternal()));
      //}

      // [TODO] Increase to initial start level
      //StartLevelPlugin startLevel = getOptionalPlugin(StartLevelPlugin.class);
      //if (startLevel != null)
      //   startLevel.increaseStartLevel(startLevel.getInitialBundleStartLevel());

      // This Framework's state is set to ACTIVE
      systemBundle.changeState(Bundle.ACTIVE);

      // [TODO] A framework event of type STARTED is fired
      //FrameworkEventsPlugin plugin = getPlugin(FrameworkEventsPlugin.class);
      //plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.STARTED, null);
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
         if (systemBundle.getState() != Bundle.ACTIVE)
            return;

         // This Framework's state is set to Bundle.STOPPING
         systemBundle.changeState(Bundle.STOPPING);
      }

      //[TODO] Move to start level 0 in the current thread
      // StartLevelPlugin startLevel = getOptionalPlugin(StartLevelPlugin.class);
      //if (startLevel != null)
      //   startLevel.decreaseStartLevel(0);

      // No Start Level Service available, stop all bundles individually...
      // All installed bundles must be stopped without changing each bundle's persistent autostart setting
      for (AbstractBundle bundleState : bundleManager.getBundles())
      {
         if (bundleState != systemBundle)
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

      // Stop registered service plugins
      List<Plugin> reverseServicePlugins = new ArrayList<Plugin>(plugins.values());
      Collections.reverse(reverseServicePlugins);
      for (Plugin plugin : reverseServicePlugins)
      {
         if (plugin instanceof ServicePlugin)
         {
            ServicePlugin servicePlugin = (ServicePlugin)plugin;
            servicePlugin.stopService();
         }
      }

      // [TODO] Event handling is disabled
      //FrameworkEventsPlugin eventsPlugin = getPlugin(FrameworkEventsPlugin.class);
      //eventsPlugin.setActive(false);

      // This Framework's state is set to Bundle.RESOLVED
      systemBundle.changeState(Bundle.RESOLVED);

      // All resources held by this Framework are released
      systemBundle.destroyBundleContext();

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
         int state = systemBundle.getState();
         if (state != Bundle.STARTING && state != Bundle.ACTIVE && state != Bundle.STOPPING)
            return new FrameworkEvent(FrameworkEvent.STOPPED, systemBundle, null);

         stopMonitor.wait(timeout);
      }

      if (systemBundle.getState() != Bundle.RESOLVED)
         return new FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, systemBundle, null);

      return new FrameworkEvent(FrameworkEvent.STOPPED, systemBundle, null);
   }
}