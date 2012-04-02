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
package org.jboss.osgi.framework.internal;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.modules.ModuleActivator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.startlevel.StartLevel;

/**
 * Represents the INSTALLED state of a host bundle.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
final class HostBundleState extends UserBundleState {

    static final Logger log = Logger.getLogger(HostBundleState.class);

    private final Semaphore activationSemaphore = new Semaphore(1);
    private final AtomicBoolean alreadyStarting = new AtomicBoolean();
    private final AtomicBoolean awaitLazyActivation = new AtomicBoolean();
    private BundleActivator bundleActivator;
    private int startLevel;

    HostBundleState(FrameworkState frameworkState, long bundleId, Deployment dep) {
        super(frameworkState, bundleId, dep);
    }

    static HostBundleState assertBundleState(Bundle bundle) {
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);

        if (bundleState instanceof HostBundleState == false)
            throw new IllegalArgumentException("Not a HostBundleState: " + bundleState);

        return (HostBundleState) bundleState;
    }

    void initUserBundleState(OSGiMetaData metadata) {
        StartLevel startLevelService = getCoreServices().getStartLevelPlugin();
        startLevel = startLevelService.getInitialBundleStartLevel();
        awaitLazyActivation.set(isActivationLazy());
    }

    @Override
    HostBundleContext createContextInternal() {
        return new HostBundleContext(this);
    }

    @Override
    HostBundleRevision createRevisionInternal(Deployment dep) throws BundleException {
        return new HostBundleRevision(this, dep);
    }

    // Invalid discovery of Bundle.getBundleContext() method
    // http://issues.ops4j.org/browse/PAXSB-44
    public BundleContext getBundleContext() {
        return super.getBundleContext();
    }

    @Override
    boolean isFragment() {
        return false;
    }

    int getStartLevel() {
        return startLevel;
    }

    void setStartLevel(int level) {
        startLevel = level;
    }

    @Override
    HostBundleRevision getCurrentBundleRevision() {
        return (HostBundleRevision) super.getCurrentBundleRevision();
    }

    boolean isPersistentlyStarted() {
        BundleStorageState storageState = getBundleStorageState();
        return storageState.isPersistentlyStarted();
    }

    boolean isActivationLazy() {
        ActivationPolicyMetaData activationPolicy = getActivationPolicy();
        String policyType = (activationPolicy != null ? activationPolicy.getType() : null);
        return Constants.ACTIVATION_LAZY.equals(policyType);
    }

    ActivationPolicyMetaData getActivationPolicy() {
        return getOSGiMetaData().getBundleActivationPolicy();
    }

    boolean awaitLazyActivation() {
        return awaitLazyActivation.get();
    }

    void activateLazily() throws BundleException {
        if (awaitLazyActivation.getAndSet(false)) {
            if (startLevelValidForStart() == true) {
                int options = START_TRANSIENT;
                if (isBundleActivationPolicyUsed()) {
                    options |= START_ACTIVATION_POLICY;
                }
                log.debugf("Lazy activation of: %s", this);
                startInternal(options);
            }
        }
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        LazyActivationTracker.startTracking(this, className);
        try {
            Class<?> loadedClass = super.loadClass(className);
            LazyActivationTracker.processLoadedClass(loadedClass);
            return loadedClass;
        } finally {
            LazyActivationTracker.stopTracking(this, className);
        }
    }

    private boolean startLevelValidForStart() {
        StartLevel startLevelPlugin = getCoreServices().getStartLevelPlugin();
        return getStartLevel() <= startLevelPlugin.getStartLevel();
    }

    private boolean isBundleActivationPolicyUsed() {
        BundleStorageState storageState = getBundleStorageState();
        return storageState.isBundleActivationPolicyUsed();
    }

    private void setBundleActivationPolicyUsed(boolean usePolicy) {
        BundleStorageState storageState = getBundleStorageState();
        storageState.setBundleActivationPolicyUsed(usePolicy);
    }

    boolean isAlreadyStarting() {
        return alreadyStarting.get();
    }

    Set<UserBundleState> getDependentBundles() {
        Set<UserBundleState> result = new HashSet<UserBundleState>();
        if (isResolved() == true) {
            BundleWiring wiring = getCurrentBundleRevision().getWiring();
            List<Wire> wires = wiring.getRequiredResourceWires(null);
            for (Wire wire : wires) {
                Resource provider = wire.getProvider();
				AbstractBundleState bundleState = ((AbstractBundleRevision) provider).getBundleState();
				if (bundleState instanceof UserBundleState)
					result.add((UserBundleState)bundleState);
            }
        }
        return result;
    }

    void startInternal(int options) throws BundleException {

        // Assert the required start conditions
        assertStartConditions();

        // If the Framework's current start level is less than this bundle's start level
        if (startLevelValidForStart() == false) {
            // If the START_TRANSIENT option is set, then a BundleException is thrown
            // indicating this bundle cannot be started due to the Framework's current start level
            if ((options & START_TRANSIENT) != 0)
                throw new BundleException("Bundle cannot be started due to the Framework's current start level");

            // Set this bundle's autostart setting
            persistAutoStartSettings(options);
            return;
        }

        alreadyStarting.set(true);
        try {
            // #1 If this bundle is in the process of being activated or deactivated
            // then this method must wait for activation or deactivation to complete before continuing.
            // If this does not occur in a reasonable time, a BundleException is thrown
            aquireActivationLock();

            // #2 If this bundle's state is ACTIVE then this method returns immediately.
            if (getState() == ACTIVE)
                return;

            // #3 Set this bundle's autostart setting
            persistAutoStartSettings(options);

            // #4 If this bundle's state is not RESOLVED, an attempt is made to resolve this bundle.
            // If the Framework cannot resolve this bundle, a BundleException is thrown.
            ResolutionException resex = ensureResolved(true);
            if (resex != null)
                throw new BundleException("Cannot resolve bundle: " + this, resex);

            // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
            if (getBundleContextInternal() == null)
                createBundleContext();

            // #5 If the START_ACTIVATION_POLICY option is set and this bundle's declared activation policy is lazy
            boolean useActivationPolicy = (options & START_ACTIVATION_POLICY) != 0;
            if (awaitLazyActivation.get() == true && useActivationPolicy == true) {
                transitionToStarting(options);
            } else {
                transitionToActive(options);
            }
        } finally {
            alreadyStarting.set(false);
            releaseActivationLock();
        }
    }

    private void assertStartConditions() throws BundleException {

        // The service platform may run this bundle if any of the execution environments named in the
        // Bundle-RequiredExecutionEnvironment header matches one of the execution environments it implements.
        List<String> requiredEnvs = getOSGiMetaData().getRequiredExecutionEnvironment();
        if (requiredEnvs != null) {
            boolean foundSupportedEnv = false;
            String frameworkEnvProp = (String) getBundleManager().getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
            List<String> availableEnvs = Arrays.asList(frameworkEnvProp.split("[,\\s]+"));
            for (String aux : requiredEnvs) {
                if (availableEnvs.contains(aux)) {
                    foundSupportedEnv = true;
                    break;
                }
            }
            if (foundSupportedEnv == false)
                throw new BundleException("Unsupported execution environment " + requiredEnvs + " we have " + availableEnvs);
        }
    }

    private void persistAutoStartSettings(int options) {
        // The Framework must set this bundle's persistent autostart setting to
        // Started with declared activation if the START_ACTIVATION_POLICY option is set or
        // Started with eager activation if not set.

        if ((options & START_TRANSIENT) == 0) {
            setPersistentlyStarted(true);
            boolean activationPolicyUsed = (options & START_ACTIVATION_POLICY) != 0;
            setBundleActivationPolicyUsed(activationPolicyUsed);
        }
    }

    private void setPersistentlyStarted(boolean started) {
        BundleStorageState storageState = getBundleStorageState();
        storageState.setPersistentlyStarted(started);
    }

    private void transitionToStarting(int options) throws BundleException {
        // #5.1 If this bundle's state is STARTING then this method returns immediately.
        if (getState() == STARTING)
            return;

        // #5.2 This bundle's state is set to STARTING.
        // #5.3 A bundle event of type BundleEvent.LAZY_ACTIVATION is fired
        changeState(STARTING, BundleEvent.LAZY_ACTIVATION);
    }

    private void transitionToActive(int options) throws BundleException {
        // #6 This bundle's state is set to STARTING.
        // #7 A bundle event of type BundleEvent.STARTING is fired.
        try {
            changeState(STARTING);
        } catch (LifecycleInterceptorException ex) {
            throw new BundleException("Cannot transition to STARTING: " + this, ex);
        }

        // #8 The BundleActivator.start(BundleContext) method of this bundle is called
        String bundleActivatorClassName = getOSGiMetaData().getBundleActivator();
        if (bundleActivatorClassName != null) {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(null);
                Object result = loadClass(bundleActivatorClassName).newInstance();
                if (result instanceof ModuleActivator) {
                    bundleActivator = new ModuleActivatorBridge((ModuleActivator) result);
                    bundleActivator.start(getBundleContext());
                } else if (result instanceof BundleActivator) {
                    bundleActivator = (BundleActivator) result;
                    bundleActivator.start(getBundleContext());
                } else {
                    throw new BundleException(bundleActivatorClassName + " is not an implementation of " + BundleActivator.class.getName());
                }
            }

            // If the BundleActivator is invalid or throws an exception then
            catch (Throwable th) {
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
                if (th instanceof BundleException)
                    throw (BundleException) th;

                throw new BundleException("Cannot start bundle: " + this, th);
            } finally {
                Thread.currentThread().setContextClassLoader(tccl);
            }
        }

        // #9 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while
        // the BundleActivator.start method was running, a BundleException is thrown
        if (getState() == UNINSTALLED)
            throw new BundleException("Bundle was uninstalled while activator was running: " + this);

        // #10 This bundle's state is set to ACTIVE.
        // #11 A bundle event of type BundleEvent.STARTED is fired
        changeState(ACTIVE);

        // Activate the service that represents bundle state ACTIVE
        getBundleManager().setServiceMode(getServiceName(ACTIVE), Mode.ACTIVE);

        log.infof("Bundle started: %s", this);
    }

    @Override
    void stopInternal(int options) throws BundleException {

        try {
            // #2 f this bundle is in the process of being activated or deactivated
            // then this method must wait for activation or deactivation to complete before continuing.
            // If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle was unable to be
            // stopped
            aquireActivationLock();

            // A concurrent thread may have uninstalled the bundle
            if (getState() == UNINSTALLED)
                return;

            // #3 If the STOP_TRANSIENT option is not set then then set this bundle's persistent autostart setting to Stopped.
            // When the Framework is restarted and this bundle's autostart setting is Stopped, this bundle must not be
            // automatically started.
            if ((options & STOP_TRANSIENT) == 0) {
                setPersistentlyStarted(false);
                setBundleActivationPolicyUsed(false);
            }

            // #4 If this bundle's state is not STARTING or ACTIVE then this method returns immediately
            int priorState = getState();
            if (priorState != STARTING && priorState != ACTIVE)
                return;

            // #5 This bundle's state is set to STOPPING
            // #6 A bundle event of type BundleEvent.STOPPING is fired
            changeState(STOPPING);

            // #7 If this bundle's state was ACTIVE prior to setting the state to STOPPING,
            // the BundleActivator.stop(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is
            // specified, is called.
            // If that method throws an exception, this method must continue to stop this bundle and a BundleException must be
            // thrown after completion
            // of the remaining steps.
            Throwable rethrow = null;
            if (priorState == ACTIVE) {
                if (bundleActivator != null) {
                    try {
                        if (bundleActivator instanceof ModuleActivatorBridge) {
                            bundleActivator.stop(getBundleContext());
                        } else {
                            bundleActivator.stop(getBundleContext());
                        }
                    } catch (Throwable t) {
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
            if (getState() == UNINSTALLED)
                throw new BundleException("Bundle uninstalled during activator stop: " + this);

            // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
            destroyBundleContext();

            // #12 This bundle's state is set to RESOLVED
            // #13 A bundle event of type BundleEvent.STOPPED is fired
            changeState(RESOLVED, BundleEvent.STOPPED);

            // Deactivate the service that represents bundle state ACTIVE
            getBundleManager().setServiceMode(getServiceName(ACTIVE), Mode.NEVER);

            log.infof("Bundle stopped: %s", this);

            if (rethrow != null)
                throw new BundleException("Error during stop of bundle: " + this, rethrow);
        } finally {
            releaseActivationLock();
        }
    }

    private void aquireActivationLock() throws BundleException {
        try {
            log.tracef("Aquire activation lock: %s", this);
            if (activationSemaphore.tryAcquire(10, TimeUnit.SECONDS) == false)
                throw new BundleException("Cannot acquire start/stop lock for: " + this);
        } catch (InterruptedException ex) {
            log.warnf("Interupted while trying to start/stop bundle: %s", this);
            return;
        }
    }

    private void releaseActivationLock() {
        log.tracef("Release activation lock: %s", this);
        activationSemaphore.release();
    }

    private void removeServicesAndListeners() {
        // Any services registered by this bundle must be unregistered.
        // Any services used by this bundle must be released.
        for (ServiceState serviceState : getRegisteredServicesInternal()) {
            serviceState.unregisterInternal();
        }

        // Any listeners registered by this bundle must be removed
        FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
        eventsPlugin.removeBundleListeners(this);
    }
}
