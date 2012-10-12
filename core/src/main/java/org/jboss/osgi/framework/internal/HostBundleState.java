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
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.AbstractBundleState.BundleLock.Method;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.BundleLifecyclePlugin;
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
import org.osgi.service.startlevel.StartLevel;

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

    HostBundleState(FrameworkState frameworkState, HostBundleRevision brev, ServiceName serviceName) {
        super(frameworkState, brev, serviceName);

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

    int getStartLevel() {
        return getStorageState().getStartLevel();
    }

    void setStartLevel(int level) {
        LOGGER.debugf("Setting bundle start level %d for: %s", level, this);
        InternalStorageState storageState = getStorageState();
        storageState.setStartLevel(level);
    }

    boolean isPersistentlyStarted() {
        StorageState storageState = getStorageState();
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

    void startInternal(int options) throws BundleException {

        // #1 If this bundle is in the process of being activated or deactivated
        // then this method must wait for activation or deactivation to complete before continuing.
        // If this does not occur in a reasonable time, a BundleException is thrown
        aquireBundleLock(Method.START);
        alreadyStarting.set(true);

        try {
            // Assert the required start conditions
            assertStartConditions();

            LOGGER.debugf("Starting bundle: %s", this);

            // If the Framework's current start level is less than this bundle's start level
            if (startLevelValidForStart() == false) {
                // If the START_TRANSIENT option is set, then a BundleException is thrown
                // indicating this bundle cannot be started due to the Framework's current start level
                if ((options & START_TRANSIENT) != 0)
                    throw MESSAGES.cannotStartBundleDueToStartLevel();

                LOGGER.debugf("Start level [%d] not valid for: %s", getStartLevel(), this);

                // Set this bundle's autostart setting
                persistAutoStartSettings(options);
                return;
            }

            // #2 If this bundle's state is ACTIVE then this method returns immediately.
            if (getState() == ACTIVE)
                return;

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
                transitionToStarting(options);
            } else {
                transitionToActive(options);
            }
        } finally {
            alreadyStarting.set(false);
            releaseBundleLock(Method.START);
        }
    }

    @Override
    void stopInternal(int options) throws BundleException {
        BundleLifecyclePlugin lifecycle = getCoreServices().getBundleLifecyclePlugin();
        lifecycle.stop(this, options, DefaultBundleLifecycleHandler.INSTANCE);
    }

    void setPersistentlyStarted(boolean started) {
        InternalStorageState storageState = getStorageState();
        storageState.setPersistentlyStarted(started);
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

    private void transitionToStarting(int options) throws BundleException {
        // #5.1 If this bundle's state is STARTING then this method returns immediately.
        if (getState() == STARTING)
            return;

        // #5.2 This bundle's state is set to STARTING.
        // #5.3 A bundle event of type BundleEvent.LAZY_ACTIVATION is fired
        changeState(STARTING, BundleEvent.LAZY_ACTIVATION);
    }

    private void transitionToActive(int options) throws BundleException {
        BundleLifecyclePlugin lifecycle = getCoreServices().getBundleLifecyclePlugin();
        if (lifecycle instanceof DefaultBundleLifecyclePlugin) {
            lifecycle.start(this, options, DefaultBundleLifecycleHandler.INSTANCE);
        } else {
            // If we are not ussing the default impl, we cannot call out holding the lock
            releaseBundleLock(Method.START);
            try {
                lifecycle.start(this, options, DefaultBundleLifecycleHandler.INSTANCE);
            } finally {
                aquireBundleLock(Method.START);
            }
        }
    }

    private boolean startLevelValidForStart() {
        StartLevel startLevelPlugin = getCoreServices().getStartLevel();
        return getStartLevel() <= startLevelPlugin.getStartLevel();
    }

    private boolean isBundleActivationPolicyUsed() {
        StorageState storageState = getStorageState();
        return storageState.isBundleActivationPolicyUsed();
    }
}
