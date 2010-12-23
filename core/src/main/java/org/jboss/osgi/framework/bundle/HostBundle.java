/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.plugin.StartLevelPlugin;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.modules.ModuleActivator;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * This is the internal implementation of a host Bundle.
 *
 * Bundle specific functionality is handled here, such as Start Level,
 * the {@link BundleActivator} and internal implementations of lifecycle management.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public final class HostBundle extends AbstractUserBundle
{
   private static final Logger log = Logger.getLogger(HostBundle.class);

   private final StartLevelPlugin startLevelPlugin;
   private final AtomicBoolean awaitLazyActivation;
   private final Semaphore activationSemaphore;

   private BundleActivator bundleActivator;
   private int startLevel;

   HostBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment);

      startLevelPlugin = getBundleManager().getOptionalPlugin(StartLevelPlugin.class);
      startLevel = (startLevelPlugin != null ? startLevelPlugin.getInitialBundleStartLevel() : StartLevelPlugin.BUNDLE_STARTLEVEL_UNSPECIFIED);
      awaitLazyActivation = new AtomicBoolean(isActivationLazy());
      activationSemaphore = new Semaphore(1);
   }

   /**
    * Assert that the given bundle is an instance of InternalBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of InternalBundle
    */
   public static HostBundle assertBundleState(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);

      if (bundleState instanceof HostBundle == false)
         throw new IllegalArgumentException("Not an InternalBundle: " + bundleState);

      return (HostBundle)bundleState;
   }

   @Override
   AbstractUserRevision createRevisionInternal(Deployment dep) throws BundleException
   {
      return new HostRevision(this, dep);
   }

   @Override
   public HostRevision getCurrentRevision()
   {
      return (HostRevision)super.getCurrentRevision();
   }

   public List<VirtualFile> getContentRoots()
   {
      return getCurrentRevision().getContentRoots();
   }

   /**
    * A bundle is destroyed if it is no longer known to the system.
    * An uninstalled bundle can potentially live on
    * if there are other bundles depending on it. Only after a call to
    * {@link PackageAdmin#refreshPackages(Bundle[])} the bundle gets destroyed.
    * @return whether or not the bundle is destroyed.
    */
   public boolean isDestroyed()
   {
      return getBundleManager().getBundleById(getBundleId()) == null;
   }

   @Override
   public boolean isFragment()
   {
      return false;
   }

   @Override
   public boolean ensureResolved(boolean fireEvent)
   {
      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      // If this bundle cannot be resolved, a Framework event of type FrameworkEvent.ERROR is fired
      // containing a BundleException with details of the reason this bundle could not be resolved.
      synchronized (this)
      {
         XModule resModule = getResolverModule();
         if (resModule.isResolved())
            return true;

         try
         {
            getResolverPlugin().resolve(resModule);
            return true;
         }
         catch (BundleException ex)
         {
            if (fireEvent == true)
               getFrameworkEventsPlugin().fireFrameworkEvent(this, FrameworkEvent.ERROR, ex);

            return false;
         }
      }
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      return new HostBundleContext(this, null);
   }

   public int getStartLevel()
   {
      return startLevel;
   }

   public void setStartLevel(int level)
   {
      startLevel = level;
   }

   public boolean isPersistentlyStarted()
   {
      BundleStorageState storageState = getBundleStorageState();
      return storageState.isPersistentlyStarted();
   }

   private void setPersistentlyStarted(boolean started)
   {
      BundleStorageState storageState = getBundleStorageState();
      storageState.setPersistentlyStarted(started);
   }

   public boolean isBundleActivationPolicyUsed()
   {
      BundleStorageState storageState = getBundleStorageState();
      return storageState.isBundleActivationPolicyUsed();
   }

   private void setBundleActivationPolicyUsed(boolean usePolicy)
   {
      BundleStorageState storageState = getBundleStorageState();
      storageState.setBundleActivationPolicyUsed(usePolicy);
   }

   @Override
   void startInternal(int options) throws BundleException
   {
      // If the Framework's current start level is less than this bundle's start level
      if (hasStartLevelValidForStart() == false)
      {
         // If the START_TRANSIENT option is set, then a BundleException is thrown
         // indicating this bundle cannot be started due to the Framework's current start level
         if ((options & Bundle.START_TRANSIENT) != 0)
            throw new BundleException("Bundle cannot be started due to the Framework's current start level");

         // Set this bundle's autostart setting
         persistAutoStartSettings(options);
         return;
      }

      try
      {
         // #1 If this bundle is in the process of being activated or deactivated 
         // then this method must wait  for activation or deactivation to complete before continuing. 
         // If this does not occur in a reasonable time, a BundleException is thrown
         aquireActivationLock();

         // #2 If this bundle's state is ACTIVE then this method returns immediately.
         if (getState() == Bundle.ACTIVE)
            return;

         // #3 Set this bundle's autostart setting
         persistAutoStartSettings(options);

         // #4 If this bundle's state is not RESOLVED, an attempt is made to resolve this bundle. 
         // If the Framework cannot resolve this bundle, a BundleException is thrown. 
         if (ensureResolved(false) == false)
            throw new BundleException("Cannot resolve bundle: " + this);

         // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
         if (getBundleContextInternal() == null)
            createBundleContext();

         // #5 If the START_ACTIVATION_POLICY option is set and this bundle's declared activation policy is lazy
         boolean useActivationPolicy = (options & Bundle.START_ACTIVATION_POLICY) != 0;
         if (awaitLazyActivation.get() == true && useActivationPolicy == true)
         {
            transitionToStarting(options);
         }
         else
         {
            transitionToActive(options);
         }
      }
      finally
      {
         activationSemaphore.release();
      }
   }

   private void aquireActivationLock() throws BundleException
   {
      try
      {
         if (activationSemaphore.tryAcquire(10, TimeUnit.SECONDS) == false)
            throw new BundleException("Cannot acquire start/stop lock for: " + this);
      }
      catch (InterruptedException ex)
      {
         log.warnf("Tread interupted while trying to start/stop bundle: %s", this);
         return;
      }
   }

   private void persistAutoStartSettings(int options)
   {
      // The Framework must set this bundle's persistent autostart setting to 
      // Started with declared activation if the START_ACTIVATION_POLICY option is set or 
      // Started with eager activation if not set.

      if ((options & Bundle.START_TRANSIENT) == 0)
         setPersistentlyStarted(true);

      boolean activationPolicyUsed = (options & Bundle.START_ACTIVATION_POLICY) != 0;
      setBundleActivationPolicyUsed(activationPolicyUsed);
   }

   private boolean hasStartLevelValidForStart()
   {
      if (startLevelPlugin == null)
         return true;

      return getStartLevel() <= startLevelPlugin.getStartLevel();
   }

   private void transitionToStarting(int options) throws BundleException
   {
      // #5.1 If this bundle's state is STARTING then this method returns immediately.
      if (getState() == Bundle.STARTING)
         return;

      // #5.2 This bundle's state is set to STARTING.
      // #5.3 A bundle event of type BundleEvent.LAZY_ACTIVATION is fired
      changeState(Bundle.STARTING, BundleEvent.LAZY_ACTIVATION);
   }

   private void transitionToActive(int options) throws BundleException
   {
      // #6 This bundle's state is set to STARTING.
      // #7 A bundle event of type BundleEvent.STARTING is fired. 
      changeState(Bundle.STARTING);

      // #8 The BundleActivator.start(BundleContext) method of this bundle is called
      XModule resModule = getResolverModule();
      String bundleActivatorClassName = resModule.getModuleActivator();
      if (bundleActivatorClassName != null)
      {
         try
         {
            Object result = loadClass(bundleActivatorClassName).newInstance();
            if (result instanceof ModuleActivator)
            {
               bundleActivator = new ModuleActivatorBridge((ModuleActivator)result);
               bundleActivator.start(getBundleContext());
            }
            else if (result instanceof BundleActivator)
            {
               bundleActivator = (BundleActivator)result;
               bundleActivator.start(getBundleContext());
            }
            else
            {
               throw new BundleException(bundleActivatorClassName + " is not an implementation of " + BundleActivator.class.getName());
            }
         }

         // If the BundleActivator is invalid or throws an exception then
         catch (Exception ex)
         {
            // #8.1 This bundle's state is set to STOPPING
            // #8.2 A bundle event of type BundleEvent.STOPPING is fired
            changeState(STOPPING);

            // #8.3 Any services registered by this bundle must be unregistered.
            // #8.4 Any services used by this bundle must be released.
            // #8.5 Any listeners registered by this bundle must be removed.
            removeServicesAndListeners();

            // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
            destroyBundleContext();

            // #8.6 This bundle's state is set to RESOLVED
            // #8.7 A bundle event of type BundleEvent.STOPPED is fired
            changeState(RESOLVED);

            // #8.8 A BundleException is then thrown
            if (ex instanceof BundleException)
               throw (BundleException)ex;

            throw new BundleException("Cannot start bundle: " + this, ex);
         }
      }

      // #9 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while 
      // the BundleActivator.start method was running, a BundleException is thrown
      if (getState() == UNINSTALLED)
         throw new BundleException("Bundle was uninstalled while activator was running: " + this);

      // #10 This bundle's state is set to ACTIVE.
      // #11 A bundle event of type BundleEvent.STARTED is fired
      changeState(ACTIVE);

      log.infof("Bundle started: %s", this);
   }

   public boolean isActivationLazy()
   {
      ActivationPolicyMetaData activationPolicy = getActivationPolicy();
      String policyType = (activationPolicy != null ? activationPolicy.getType() : null);
      return Constants.ACTIVATION_LAZY.equals(policyType);
   }

   public ActivationPolicyMetaData getActivationPolicy()
   {
      return getOSGiMetaData().getBundleActivationPolicy();
   }

   public void activateOnClassLoad(final Class<?> definedClass) throws BundleException
   {
      if (awaitLazyActivation.getAndSet(false))
      {
         if (hasStartLevelValidForStart() == true)
            startInternal(Bundle.START_TRANSIENT);
      }
   }

   @Override
   void stopInternal(int options) throws BundleException
   {
      // #1 If this bundle's state is UNINSTALLED then an IllegalStateException is thrown
      if (getState() == Bundle.UNINSTALLED)
         throw new IllegalStateException("Bundle already uninstalled: " + this);

      try
      {
         // #2 f this bundle is in the process of being activated or deactivated 
         // then this method must wait for activation or deactivation to complete before continuing. 
         // If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle was unable to be stopped
         aquireActivationLock();

         // #3 If the STOP_TRANSIENT option is not set then then set this bundle's persistent autostart setting to Stopped.
         // When the Framework is restarted and this bundle's autostart setting is Stopped, this bundle must not be automatically started.
         if ((options & Bundle.STOP_TRANSIENT) == 0)
            setPersistentlyStarted(false);

         // [TODO] Verify if this is correct here
         setBundleActivationPolicyUsed(false);
         
         // #4 If this bundle's state is not STARTING or ACTIVE then this method returns immediately
         if (getState() != Bundle.STARTING && getState() != Bundle.ACTIVE)
            return;

         // #5 This bundle's state is set to STOPPING
         // #6 A bundle event of type BundleEvent.STOPPING is fired
         int priorState = getState();
         changeState(STOPPING);

         // #7 If this bundle's state was ACTIVE prior to setting the state to STOPPING,
         // the BundleActivator.stop(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is specified, is called.
         // If that method throws an exception, this method must continue to stop this bundle and a BundleException must be thrown after completion
         // of the remaining steps.
         Throwable rethrow = null;
         if (priorState == Bundle.ACTIVE)
         {
            if (bundleActivator != null)
            {
               try
               {
                  if (bundleActivator instanceof ModuleActivatorBridge)
                  {
                     bundleActivator.stop(getBundleContext());
                  }
                  else
                  {
                     bundleActivator.stop(getBundleContext());
                  }
               }
               catch (Throwable t)
               {
                  rethrow = t;
               }
            }
         }

         // #8 Any services registered by this bundle must be unregistered.
         // #9 Any services used by this bundle must be released. 
         // #10 Any listeners registered by this bundle must be removed.
         removeServicesAndListeners();

         // #11 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while the
         // BundleActivator.stop method was running, a BundleException must be thrown
         if (getState() == Bundle.UNINSTALLED)
            throw new BundleException("Bundle uninstalled during activator stop: " + this);

         // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
         destroyBundleContext();

         // #12 This bundle's state is set to RESOLVED
         // #13 A bundle event of type BundleEvent.STOPPED is fired
         changeState(RESOLVED);

         log.infof("Bundle stopped: %s", this);

         if (rethrow != null)
            throw new BundleException("Error during stop of bundle: " + this, rethrow);
      }
      finally
      {
         activationSemaphore.release();
      }
   }

   private void removeServicesAndListeners()
   {
      // Any services registered by this bundle must be unregistered.
      // Any services used by this bundle must be released. 
      for (ServiceState serviceState : getRegisteredServicesInternal())
         serviceState.unregister();

      // Any listeners registered by this bundle must be removed
      getFrameworkEventsPlugin().removeBundleListeners(this);
   }

   @Override
   protected void beforeUninstall() throws BundleException
   {
      BundleManager bundleManager = getBundleManager();

      // If this bundle's state is ACTIVE, STARTING or STOPPING, this bundle is stopped
      // as described in the Bundle.stop method.
      int state = getState();
      if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING)
      {
         try
         {
            stopInternal(0);
         }
         catch (Exception ex)
         {
            // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is
            // fired containing the exception
            bundleManager.fireError(this, "Error stopping bundle: " + this, ex);
         }
      }
   }
}
