/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.StartLevelPlugin;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;

/**
 * Represents the INSTALLED state of a host bundle.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
final class HostBundleState extends UserBundleState {

    private final AtomicBoolean alreadyStarting = new AtomicBoolean();
    private final AtomicBoolean awaitLazyActivation = new AtomicBoolean();
    BundleActivator bundleActivator;

    HostBundleState(FrameworkState frameworkState, HostBundleRevision brev, ServiceName serviceName, ServiceTarget serviceTarget) {
        super(frameworkState, brev, serviceName, serviceTarget);

        // Assign the {@link ModuleIdentifier}
        ModuleManagerPlugin moduleManager = frameworkState.getModuleManagerPlugin();
        ModuleIdentifier moduleIdentifier = moduleManager.getModuleIdentifier(brev);
        brev.addAttachment(ModuleIdentifier.class, moduleIdentifier);
    }

    static HostBundleState assertBundleState(Bundle bundle) {
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        assert bundleState instanceof HostBundleState : "Not a HostBundleState: " + bundleState;
        return (HostBundleState) bundleState;
    }

    // Invalid discovery of Bundle.getBundleContext() method
    // http://issues.ops4j.org/browse/PAXSB-44
    @Override
    public BundleContext getBundleContext() {
        return super.getBundleContext();
    }

    @Override
    public boolean isFragment() {
        return false;
    }

    @Override
    public HostBundleRevision getBundleRevision() {
        return (HostBundleRevision) super.getBundleRevision();
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

    @Override
    void initLazyActivation() {
        awaitLazyActivation.set(isActivationLazy());
    }

    @Override
    HostBundleContext createContextInternal() {
        return new HostBundleContext(this);
    }

    @Override
    HostBundleRevision createUpdateRevision(Deployment dep, OSGiMetaData metadata, InternalStorageState storageState) throws BundleException {
        return new HostBundleRevision(getFrameworkState(), dep, metadata, storageState);
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
                LOGGER.debugf("Lazy activation of: %s", this);
                startInternal(options);
            }
        }
    }

    void setBundleActivationPolicyUsed(boolean usePolicy) {
        InternalStorageState storageState = getStorageState();
        storageState.setBundleActivationPolicyUsed(usePolicy);
    }

    boolean isAlreadyStarting() {
        return alreadyStarting.get();
    }

    Set<UserBundleState> getDependentBundles() {
        Set<UserBundleState> result = new HashSet<UserBundleState>();
        if (isResolved() == true) {
            BundleWiring wiring = getBundleRevision().getWiring();
            List<Wire> wires = wiring.getRequiredResourceWires(null);
            for (Wire wire : wires) {
                BundleRevision brev = (BundleRevision) wire.getProvider();
                Bundle bundle = brev.getBundle();
                if (bundle instanceof UserBundleState)
                    result.add((UserBundleState) bundle);
            }
        }
        return result;
    }

    @Override
    void startInternal(int options) throws BundleException {

        // #1 If this bundle is in the process of being activated or deactivated
        // then this method must wait for activation or deactivation to complete before continuing.
        // If this does not occur in a reasonable time, a BundleException is thrown
        LockContext lockContext = null;
        LockManager lockManager = getFrameworkState().getLockManager();
        try {
            lockContext = lockManager.lockItems(Method.START, this);

            // We got the permit, now start
            try {
                alreadyStarting.set(true);
                startInternalNow(options);
            } finally {
                alreadyStarting.set(false);
            }

        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    private void startInternalNow(int options) throws BundleException {

        // Assert the required start conditions
        assertStartConditions();

        // #2 If this bundle's state is ACTIVE then this method returns immediately.
        if (getState() == ACTIVE)
            return;

        LOGGER.debugf("Starting bundle: %s", this);

        // If the Framework's current start level is less than this bundle's start level
        if (startLevelValidForStart() == false) {
            // If the START_TRANSIENT option is set, then a BundleException is thrown
            // indicating this bundle cannot be started due to the Framework's current start level
            if ((options & START_TRANSIENT) != 0)
                throw MESSAGES.cannotStartBundleDueToStartLevel();

            LOGGER.debugf("Start level [%d] not valid for: %s", getBundleStartLevel(), this);

            // Set this bundle's autostart setting
            persistAutoStartSettings(options);
            return;
        }

        // #3 Set this bundle's autostart setting
        persistAutoStartSettings(options);

        // #4 If this bundle's state is not RESOLVED, an attempt is made to resolve this bundle.
        // If the Framework cannot resolve this bundle, a BundleException is thrown.
        if (ensureResolved(true) == false) {
            ResolutionException resex = getLastResolutionException();
            throw MESSAGES.cannotResolveBundle(resex, this);
        }

        // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
        if (getBundleContextInternal() == null)
            createBundleContext();

        // #5 If the START_ACTIVATION_POLICY option is set and this bundle's declared activation policy is lazy
        boolean useActivationPolicy = (options & START_ACTIVATION_POLICY) != 0;
        if (awaitLazyActivation.get() == true && useActivationPolicy == true) {

            // #5.1 If this bundle's state is STARTING then this method returns immediately.
            if (getState() == STARTING)
                return;

            // #5.2 This bundle's state is set to STARTING.
            // #5.3 A bundle event of type BundleEvent.LAZY_ACTIVATION is fired
            changeState(STARTING, BundleEvent.LAZY_ACTIVATION);
            return;
        }

        // #6 This bundle's state is set to STARTING.
        // #7 A bundle event of type BundleEvent.STARTING is fired.
        try {
            changeState(Bundle.STARTING);
        } catch (LifecycleInterceptorException ex) {
            throw MESSAGES.cannotTransitionToStarting(ex, this);
        }

        // #8 The BundleActivator.start(BundleContext) method of this bundle is called
        String className = getOSGiMetaData().getBundleActivator();
        if (className != null) {
            try {
                bundleActivator = getDeployment().getAttachment(BundleActivator.class);
                if (bundleActivator == null) {
                    Object result = loadClass(className).newInstance();
                    if (result instanceof BundleActivator) {
                        bundleActivator = (BundleActivator) result;
                    } else {
                        throw MESSAGES.invalidBundleActivator(className);
                    }
                }
                if (bundleActivator != null) {
                    bundleActivator.start(getBundleContext());
                }
            }

            // If the BundleActivator is invalid or throws an exception then
            catch (Throwable th) {
                // #8.1 This bundle's state is set to STOPPING
                // #8.2 A bundle event of type BundleEvent.STOPPING is fired
                changeState(Bundle.STOPPING);

                // #8.3 Any services registered by this bundle must be unregistered.
                // #8.4 Any services used by this bundle must be released.
                // #8.5 Any listeners registered by this bundle must be removed.
                removeServicesAndListeners(this);

                // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
                destroyBundleContext();

                // #8.6 This bundle's state is set to RESOLVED
                // #8.7 A bundle event of type BundleEvent.STOPPED is fired
                changeState(Bundle.RESOLVED);

                // #8.8 A BundleException is then thrown
                if (th instanceof BundleException)
                    throw (BundleException) th;

                throw MESSAGES.cannotStartBundle(th, this);
            }
        }

        // #9 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while
        // the BundleActivator.start method was running, a BundleException is thrown
        if (getState() == Bundle.UNINSTALLED)
            throw MESSAGES.uninstalledDuringActivatorStart(this);

        // #10 This bundle's state is set to ACTIVE.
        // #11 A bundle event of type BundleEvent.STARTED is fired
        changeState(Bundle.ACTIVE);

        // Activate the service that represents bundle state ACTIVE
        getBundleManager().setServiceMode(getServiceName(Bundle.ACTIVE), Mode.ACTIVE);

        LOGGER.infoBundleStarted(this);
    }

    @Override
    void stopInternal(int options) throws BundleException {
        // #2 If this bundle is in the process of being activated or deactivated
        // then this method must wait for activation or deactivation to complete before continuing.
        // If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle was unable to be
        // stopped
        LockContext lockContext = null;
        LockManager lockManager = getFrameworkState().getLockManager();
        try {
            lockContext = lockManager.lockItems(Method.STOP, this);
            
            // We got the permit, now stop
            stopInternalNow(options);

        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    private void stopInternalNow(int options) throws BundleException {

        int priorState = getState();

        LockContext lockContext = null;
        LockManager lockManager = getFrameworkState().getLockManager();
        try {
            LockManager.LockableItem[] lockItems = new LockableItem[] { this };
            lockContext = lockManager.lockItems(Method.STOP, lockItems);

            // #3 If the STOP_TRANSIENT option is not set then then set this bundle's persistent autostart setting to Stopped.
            // When the Framework is restarted and this bundle's autostart setting is Stopped, this bundle must not be
            // automatically started.
            if ((options & Bundle.STOP_TRANSIENT) == 0) {
                setPersistentlyStarted(false);
                setBundleActivationPolicyUsed(false);
            }

            // #4 If this bundle's state is not STARTING or ACTIVE then this method returns immediately
            if (priorState != Bundle.STARTING && priorState != Bundle.ACTIVE)
                return;

            // #5 This bundle's state is set to STOPPING
            // #6 A bundle event of type BundleEvent.STOPPING is fired
            changeState(Bundle.STOPPING);
        } finally {
            lockManager.unlockItems(lockContext);
        }

        // #7 If this bundle's state was ACTIVE prior to setting the state to STOPPING,
        // the BundleActivator.stop(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, 
        // if one is specified, is called. If that method throws an exception, this method must continue to stop 
        // this bundle and a BundleException must be thrown after completion of the remaining steps.
        Throwable rethrow = null;
        if (priorState == Bundle.ACTIVE) {
            if (bundleActivator != null) {
                try {
                    bundleActivator.stop(getBundleContext());
                } catch (Throwable t) {
                    rethrow = t;
                }
            }
        }

        // #8 Any services registered by this bundle must be unregistered.
        // #9 Any services used by this bundle must be released.
        // #10 Any listeners registered by this bundle must be removed.
        removeServicesAndListeners(this);

        // #11 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while the
        // BundleActivator.stop method was running, a BundleException must be thrown
        if (getState() == Bundle.UNINSTALLED)
            throw MESSAGES.uninstalledDuringActivatorStop(this);

        // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
        destroyBundleContext();

        // #12 This bundle's state is set to RESOLVED
        // #13 A bundle event of type BundleEvent.STOPPED is fired
        changeState(Bundle.RESOLVED, BundleEvent.STOPPED);

        // Deactivate the service that represents bundle state ACTIVE
        getBundleManager().setServiceMode(getServiceName(Bundle.ACTIVE), Mode.NEVER);

        if (rethrow != null) {
            throw MESSAGES.cannotStopBundle(rethrow, this);
        } else {
            LOGGER.infoBundleStopped(this);
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
                throw MESSAGES.unsupportedExecutionEnvironment(requiredEnvs, availableEnvs);
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

    private int getBundleStartLevel() {
        StartLevelPlugin startLevelPlugin = getCoreServices().getStartLevelPlugin();
        return startLevelPlugin.getBundleStartLevel(this);
    }

    void setPersistentlyStarted(boolean started) {
        StartLevelPlugin startLevelPlugin = getCoreServices().getStartLevelPlugin();
        startLevelPlugin.setBundlePersistentlyStarted(this, started);
    }

    private boolean startLevelValidForStart() {
        StartLevelPlugin startLevelPlugin = getCoreServices().getStartLevelPlugin();
        return startLevelPlugin.getBundleStartLevel(this) <= startLevelPlugin.getStartLevel();
    }

    private boolean isBundleActivationPolicyUsed() {
        StorageState storageState = getStorageState();
        return storageState.isBundleActivationPolicyUsed();
    }

    private void removeServicesAndListeners(HostBundleState hostState) {
        // Any services registered by this bundle must be unregistered.
        // Any services used by this bundle must be released.
        for (ServiceState serviceState : hostState.getRegisteredServicesInternal()) {
            serviceState.unregisterInternal();
        }

        // Any listeners registered by this bundle must be removed
        FrameworkEventsPlugin eventsPlugin = hostState.getFrameworkState().getFrameworkEventsPlugin();
        eventsPlugin.removeBundleListeners(hostState);
    }
}
