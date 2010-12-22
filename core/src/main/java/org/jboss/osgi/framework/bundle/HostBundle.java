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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
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
   
   private final AtomicBoolean awaitLazyActivation;
   private BundleActivator bundleActivator;
   private int startLevel;

   private final StartLevelPlugin startLevelPlugin;

   HostBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment);

      startLevelPlugin = getBundleManager().getOptionalPlugin(StartLevelPlugin.class);
      startLevel = (startLevelPlugin != null ? startLevelPlugin.getInitialBundleStartLevel() : StartLevelPlugin.BUNDLE_STARTLEVEL_UNSPECIFIED);
      awaitLazyActivation = new AtomicBoolean(isActivationLazy());
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
   public boolean ensureResolved()
   {
      boolean result = true;

      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      // If this bundle cannot be resolved, a Framework event of type FrameworkEvent.ERROR is fired
      // containing a BundleException with details of the reason this bundle could not be resolved.
      if (getState() == Bundle.INSTALLED)
      {
         try
         {
            getResolverPlugin().resolve(getResolverModule());
         }
         catch (BundleException ex)
         {
            getFrameworkEventsPlugin().fireFrameworkEvent(this, FrameworkEvent.ERROR, ex);
            result = false;
         }
      }

      return result;
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

   public void setStartLevel(int sl)
   {
      startLevel = sl;
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

   private void setBundleActivationPolicyUsed(boolean started)
   {
      BundleStorageState storageState = getBundleStorageState();
      storageState.setBundleActivationPolicyUsed(started);
   }

   @Override
   void startInternal(int options) throws BundleException
   {
      // If this bundle's state is ACTIVE then this method returns immediately.
      if (getState() == Bundle.ACTIVE)
         return;

      // Check the bundle's start level
      if (checkBundleStartLevel(options) == true)
      {
         transitionToStarting(options);

         if (awaitLazyActivation.get() == false)
            transitionToActive(options);
      }
      
      // The Framework must set this bundle's persistent autostart setting to 
      // Started with declared activation if the START_ACTIVATION_POLICY option is set or 
      // Started with eager activation if not set. 
      setPersistentlyStarted(true);
      setBundleActivationPolicyUsed((options & Bundle.START_ACTIVATION_POLICY) != 0);
   }

   private boolean checkBundleStartLevel(int options) throws BundleException
   {
      if (startLevelPlugin == null)
         return true;

      if (getStartLevel() <= startLevelPlugin.getStartLevel())
         return true;

      // If the START_TRANSIENT option is set, then a BundleException is thrown indicating this 
      // bundle cannot be started due to the Framework's current start level
      if ((options & Bundle.START_TRANSIENT) != 0)
         throw new BundleException("Bundle cannot be started due to the Framework's current start level");

      return false;
   }

   private void transitionToStarting(int options) throws BundleException
   {
      // If this bundle's state is ACTIVE then this method returns immediately.
      if (getState() == Bundle.ACTIVE || getState() == Bundle.STARTING)
         return;

      try
      {
         // Resolve this bundles
         XModule resModule = getResolverModule();
         getResolverPlugin().resolve(resModule);

         // Create the bundle context
         createBundleContext();

         // This bundle's state is set to STARTING
         changeState(Bundle.STARTING);
      }
      catch (Throwable th)
      {
         if (th instanceof BundleException)
            throw (BundleException)th;

         throw new BundleException("Cannot transition to starting: " + this, th);
      }
   }

   private void transitionToActive(int options) throws BundleException
   {
      // The normal STARTING event is fired
      if (isActivationLazy() == true)
         fireBundleEvent(BundleEvent.STARTING);

      // The BundleActivator.start(BundleContext) method of this bundle's BundleActivator, if one is specified, is called.
      try
      {
         // Do we have a bundle activator
         XModule resModule = getResolverModule();
         String bundleActivatorClassName = resModule.getModuleActivator();
         if (bundleActivatorClassName != null)
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
         changeState(ACTIVE);
         
         log.infof("Bundle started: %s", this);
      }

      // If the BundleActivator is invalid or throws an exception then:
      //   * This bundle's state is set to STOPPING.
      //   * A bundle event of type BundleEvent.STOPPING is fired.
      //   * Any services registered by this bundle must be unregistered.
      //   * Any services used by this bundle must be released.
      //   * Any listeners registered by this bundle must be removed.
      //   * This bundle's state is set to RESOLVED.
      //   * A bundle event of type BundleEvent.STOPPED is fired.
      //   * A BundleException is then thrown.
      catch (Throwable t)
      {
         // This bundle's state is set to STOPPING
         // A bundle event of type BundleEvent.STOPPING is fired
         changeState(STOPPING);

         // Any services registered by this bundle must be unregistered.
         // Any services used by this bundle must be released.
         // Any listeners registered by this bundle must be removed.
         stopInternal(options);

         // This bundle's state is set to RESOLVED
         // A bundle event of type BundleEvent.STOPPED is fired
         destroyBundleContext();
         changeState(RESOLVED);

         if (t instanceof BundleException)
            throw (BundleException)t;

         throw new BundleException("Cannot start bundle: " + this, t);
      }
   }

   public boolean isActivationLazy()
   {
      ActivationPolicyMetaData activationPolicy = getActivationPolicy();
      String policyType = (activationPolicy != null ? activationPolicy.getType() : null);
      return Constants.ACTIVATION_LAZY.equals(policyType);
   }

   public ActivationPolicyMetaData getActivationPolicy()
   {
      ActivationPolicyMetaData policyMetaData = getOSGiMetaData().getBundleActivationPolicy();
      return policyMetaData;
   }

   public void activateOnClassLoad(final Class<?> definedClass) throws BundleException
   {
      if (checkBundleStartLevel(0))
      {
         // If the bundle was started lazily, we transition to ACTIVE now
         if (awaitLazyActivation.compareAndSet(true, false))
         {
            transitionToStarting(0);
            transitionToActive(0);
         }
      }
   }

   @Override
   void stopInternal(int options) throws BundleException
   {
      // If this bundle's state is UNINSTALLED then an IllegalStateException is thrown.
      if (getState() == Bundle.UNINSTALLED)
         throw new IllegalStateException("Bundle already uninstalled: " + this);

      // [TODO] If this bundle is in the process of being activated or deactivated then this method must wait for activation or deactivation
      // to complete before continuing. If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle
      // was unable to be stopped.

      // [TODO] If the STOP_TRANSIENT option is not set then then set this bundle's persistent autostart setting to Stopped.
      // When the Framework is restarted and this bundle's autostart setting is Stopped, this bundle must not be automatically started.
      if ((options & Bundle.STOP_TRANSIENT) == 0)
         setPersistentlyStarted(false);

      // If this bundle's state is not STARTING or ACTIVE then this method returns immediately
      if (getState() != Bundle.STARTING && getState() != Bundle.ACTIVE)
         return;

      // This bundle's state is set to STOPPING
      // A bundle event of type BundleEvent.STOPPING is fired
      int priorState = getState();
      changeState(STOPPING);

      // If this bundle's state was ACTIVE prior to setting the state to STOPPING,
      // the BundleActivator.stop(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is specified, is called.
      // If that method throws an exception, this method must continue to stop this bundle and a BundleException must be thrown after completion
      // of the remaining steps.
      Throwable rethrow = null;
      if (priorState == Bundle.ACTIVE)
      {
         if (bundleActivator != null && getBundleContext() != null)
         {
            try
            {
               if (bundleActivator instanceof ModuleActivatorBridge)
               {
                  bundleActivator.stop(getBundleContextInternal());
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

      // Any services registered by this bundle must be unregistered
      for (ServiceState serviceState : getRegisteredServicesInternal())
         serviceState.unregister();

      // [TODO] Any listeners registered by this bundle must be removed

      // If this bundle's state is UNINSTALLED, because this bundle was uninstalled while the
      // BundleActivator.stop method was running, a BundleException must be thrown
      if (getState() == Bundle.UNINSTALLED)
         throw new BundleException("Bundle uninstalled during activator stop: " + this);

      // This bundle's state is set to RESOLVED
      // A bundle event of type BundleEvent.STOPPED is fired
      destroyBundleContext();
      changeState(RESOLVED);

      log.infof("Bundle stopped: %s", this);
      
      if (rethrow != null)
         throw new BundleException("Error during stop of bundle: " + this, rethrow);
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

   static class LazyActivationService extends AbstractService<LazyActivationService>
   {
      static final ServiceName SERVICE_NAME_BASE = ServiceName.JBOSS.append("osgi", "bundle", "activation");
      static final AtomicLong activationCount = new AtomicLong();
      private final HostBundle hostBundle;
      private final Class<?> definedClass;

      private LazyActivationService(HostBundle hostBundle, Class<?> definedClass)
      {
         this.hostBundle = hostBundle;
         this.definedClass = definedClass;
      }

      static void addService(final HostBundle hostBundle, final Class<?> definedClass)
      {
         ServiceTarget target = hostBundle.getBundleManager().getServiceContainer();
         ServiceName serviceName = SERVICE_NAME_BASE.append("" + activationCount.incrementAndGet());
         LazyActivationService service = new LazyActivationService(hostBundle, definedClass);
         ServiceBuilder<?> serviceBuilder = target.addService(serviceName, service);
         serviceBuilder.install();
      }

      @Override
      public void start(StartContext context) throws StartException
      {
         context.getController().setMode(Mode.REMOVE);

         ActivationPolicyMetaData policyMetaData = hostBundle.getOSGiMetaData().getBundleActivationPolicy();
         List<String> includes = policyMetaData.getIncludes();
         if (includes != null && includes.contains(definedClass.getPackage().getName()) == false)
            return;

         List<String> excludes = policyMetaData.getExcludes();
         if (excludes != null && excludes.contains(definedClass.getPackage().getName()) == true)
            return;

         try
         {
            hostBundle.transitionToStarting(0);
            hostBundle.transitionToActive(0);
         }
         catch (BundleException ex)
         {
            throw new StartException(ex);
         }
      }

      @Override
      public LazyActivationService getValue() throws IllegalStateException
      {
         return this;
      }
   }
}
