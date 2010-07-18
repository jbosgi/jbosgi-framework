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
package org.jboss.osgi.container.plugin.internal;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.HostBundle;
import org.jboss.osgi.container.bundle.SystemBundle;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.container.plugin.StartLevelPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;
import org.osgi.service.startlevel.StartLevel;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class StartLevelPluginImpl extends AbstractPlugin implements StartLevelPlugin
{
   final Logger log = Logger.getLogger(StartLevelPluginImpl.class);

   private final FrameworkEventsPlugin eventsPlugin;
   private Executor executor = Executors.newSingleThreadExecutor();
   private int initialBundleStartLevel = 1; // Synchronized on this
   private ServiceRegistration registration;
   private int startLevel = 0; // Synchronized on this

   public StartLevelPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      eventsPlugin = getPlugin(FrameworkEventsPlugin.class);
   }

   @Override
   public void startPlugin()
   {
      BundleContext sc = getBundleManager().getSystemContext();
      registration = sc.registerService(StartLevel.class.getName(), this, null);
   }

   @Override
   public void stopPlugin()
   {
      if (registration != null)
      {
         registration.unregister();
         registration = null;
      }
   }

   @Override
   public synchronized int getStartLevel()
   {
      return startLevel;
   }

   @Override
   public synchronized void setStartLevel(final int sl)
   {
      if (sl > getStartLevel())
      {
         log.info("About to increase start level from " + getStartLevel() + " to " + sl);
         executor.execute(new Runnable()
         {
            @Override
            public void run()
            {
               log.info("Increasing start level from " + getStartLevel() + " to " + sl);
               increaseStartLevel(sl);
               eventsPlugin.fireFrameworkEvent(getBundleManager().getSystemContext().getBundle(),
                     FrameworkEvent.STARTLEVEL_CHANGED, null);
            }
         });
      }
      else if (sl < getStartLevel())
      {
         log.info("About to decrease start level from " + getStartLevel() + " to " + sl);
         executor.execute(new Runnable()
         {
            @Override
            public void run()
            {
               log.info("Decreasing start level from " + getStartLevel() + " to " + sl);
               decreaseStartLevel(sl);
               eventsPlugin.fireFrameworkEvent(getBundleManager().getSystemContext().getBundle(),
                     FrameworkEvent.STARTLEVEL_CHANGED, null);
            }
         });
      }
   }

   @Override
   public int getBundleStartLevel(Bundle bundle)
   {
      if (bundle instanceof Framework)
         return 0;

      AbstractBundle b = AbstractBundle.assertBundleState(bundle);
      if (b instanceof SystemBundle)
         return 0;
      else if (b instanceof HostBundle)
         return ((HostBundle)b).getStartLevel();

      return StartLevelPlugin.BUNDLE_STARTLEVEL_UNSPECIFIED;
   }

   @Override
   public void setBundleStartLevel(Bundle bundle, int sl)
   {
      final HostBundle hb = HostBundle.assertBundleState(bundle);
      hb.setStartLevel(sl);

      if (sl <= getStartLevel())
      {
         if (bundle.getState() == Bundle.ACTIVE)
            return;

         log.info("Start Level Service about to start: " + hb);
         executor.execute(new Runnable()
         {
            @Override
            public void run()
            {
               log.info("Start Level Service starting: " + hb);
               try
               {
                  int opts = Bundle.START_TRANSIENT;
                  if (isBundleActivationPolicyUsed(hb))
                     opts |= Bundle.START_ACTIVATION_POLICY;

                  hb.start(opts);
               }
               catch (BundleException e)
               {
                  eventsPlugin.fireFrameworkEvent(hb, FrameworkEvent.ERROR, e);
               }
            }
         });
      }
      else
      {
         if (bundle.getState() != Bundle.ACTIVE)
            return;

         log.info("Start Level Service about to stop: " + hb);
         executor.execute(new Runnable()
         {
            @Override
            public void run()
            {
               log.info("Start Level Service stopping: " + hb);
               try
               {
                  hb.stop(Bundle.STOP_TRANSIENT);
               }
               catch (BundleException e)
               {
                  eventsPlugin.fireFrameworkEvent(hb, FrameworkEvent.ERROR, e);
               }
            }
         });
      }
   }

   @Override
   public synchronized int getInitialBundleStartLevel()
   {
      return initialBundleStartLevel;
   }

   @Override
   public synchronized void setInitialBundleStartLevel(int startlevel)
   {
      initialBundleStartLevel = startlevel;
   }

   @Override
   public boolean isBundlePersistentlyStarted(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      return bundleState.isPersistentlyStarted();
   }

   @Override
   public boolean isBundleActivationPolicyUsed(Bundle bundle)
   {
      // TODO Needs to be implemented
      return false;
   }

   /** 
    * Increases the Start Level of the Framework in the current thread.
    * @param sl the target Start Level to which the Framework should move. 
    */
   @Override
   public synchronized void increaseStartLevel(int sl)
   {
      Collection<AbstractBundle> bundles = getBundleManager().getBundles();
      while (startLevel < sl)
      {
         startLevel++;
         log.info("Starting bundles for start level " + startLevel);
         for (AbstractBundle b : bundles)
         {
            if (!(b instanceof HostBundle))
               continue;

            HostBundle hb = (HostBundle)b;
            if (hb.getStartLevel() == startLevel && hb.isPersistentlyStarted())
            {
               try
               {
                  int opts = Bundle.START_TRANSIENT;
                  if (isBundleActivationPolicyUsed(b))
                  {
                     opts |= Bundle.START_ACTIVATION_POLICY;
                  }
                  b.start(opts);
               }
               catch (Throwable e)
               {
                  eventsPlugin.fireFrameworkEvent(b, FrameworkEvent.ERROR, e);
               }
            }
         }
      }
   }

   /** 
    * Decreases the Start Level of the Framework in the current thread.
    * @param sl the target Start Level to which the Framework should move. 
    */
   @Override
   public synchronized void decreaseStartLevel(int sl)
   {
      Collection<AbstractBundle> bundles = getBundleManager().getBundles();
      while (startLevel > sl)
      {
         log.info("Stopping bundles for start level " + startLevel);
         for (AbstractBundle b : bundles)
         {
            if (!(b instanceof HostBundle))
               continue;

            HostBundle hb = (HostBundle)b;
            if (hb.getStartLevel() == startLevel)
            {
               try
               {
                  b.stop(Bundle.STOP_TRANSIENT);
               }
               catch (Throwable e)
               {
                  eventsPlugin.fireFrameworkEvent(b, FrameworkEvent.ERROR, e);
               }
            }
         }
         startLevel--;
      }
   }
}
