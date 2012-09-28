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

import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.framework.BundleLifecyclePlugin;
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.internal.AbstractBundleState.BundleLock.Method;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;

/**
 * The default implementation for bundle lifecycle operations.
 *
 * @author thomas.diesler@jboss.com
 * @since 26-Sep-2012
 */
class DefaultBundleLifecycleHandler implements BundleLifecyclePlugin.DefaultHandler {

    static DefaultBundleLifecycleHandler INSTANCE = new DefaultBundleLifecycleHandler();

    private DefaultBundleLifecycleHandler() {
    }

    @Override
    public void install(BundleManager bundleManager, Deployment dep) throws BundleException {
        bundleManager.installBundle(dep, null);
    }

    @Override
    public void start(XBundle bundle, int options) throws BundleException {
        HostBundleState hostState = HostBundleState.assertBundleState(bundle);
        hostState.aquireBundleLock(Method.START);
        try {
            // #6 This bundle's state is set to STARTING.
            // #7 A bundle event of type BundleEvent.STARTING is fired.
            try {
                hostState.changeState(HostBundleState.STARTING);
            } catch (LifecycleInterceptorException ex) {
                throw MESSAGES.cannotTransitionToStarting(ex, bundle);
            }

            // #8 The BundleActivator.start(BundleContext) method of this bundle is called
            String className = hostState.getOSGiMetaData().getBundleActivator();
            if (className != null) {
                try {
                    hostState.bundleActivator = hostState.getDeployment().getAttachment(BundleActivator.class);
                    if (hostState.bundleActivator == null) {
                        Object result = hostState.loadClass(className).newInstance();
                        if (result instanceof BundleActivator) {
                            hostState.bundleActivator = (BundleActivator) result;
                        } else {
                            throw MESSAGES.invalidBundleActivator(className);
                        }
                    }
                    if (hostState.bundleActivator != null) {
                        hostState.bundleActivator.start(hostState.getBundleContext());
                    }
                }

                // If the BundleActivator is invalid or throws an exception then
                catch (Throwable th) {
                    // #8.1 This bundle's state is set to STOPPING
                    // #8.2 A bundle event of type BundleEvent.STOPPING is fired
                    hostState.changeState(HostBundleState.STOPPING);

                    // #8.3 Any services registered by this bundle must be unregistered.
                    // #8.4 Any services used by this bundle must be released.
                    // #8.5 Any listeners registered by this bundle must be removed.
                    hostState.removeServicesAndListeners();

                    // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
                    hostState.destroyBundleContext();

                    // #8.6 This bundle's state is set to RESOLVED
                    // #8.7 A bundle event of type BundleEvent.STOPPED is fired
                    hostState.changeState(HostBundleState.RESOLVED);

                    // #8.8 A BundleException is then thrown
                    if (th instanceof BundleException)
                        throw (BundleException) th;

                    throw MESSAGES.cannotStartBundle(th, bundle);
                }
            }

            // #9 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while
            // the BundleActivator.start method was running, a BundleException is thrown
            if (hostState.getState() == HostBundleState.UNINSTALLED)
                throw MESSAGES.uninstalledDuringActivatorStart(bundle);

            // #10 This bundle's state is set to ACTIVE.
            // #11 A bundle event of type BundleEvent.STARTED is fired
            hostState.changeState(HostBundleState.ACTIVE);

            // Activate the service that represents bundle state ACTIVE
            hostState.getBundleManager().setServiceMode(hostState.getServiceName(HostBundleState.ACTIVE), Mode.ACTIVE);

            LOGGER.infoBundleStarted(bundle);
        } finally {
            hostState.releaseBundleLock(Method.START);
        }
    }

    @Override
    public void stop(XBundle bundle, int options) throws BundleException {

        // #2 If this bundle is in the process of being activated or deactivated
        // then this method must wait for activation or deactivation to complete before continuing.
        // If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle was unable to be
        // stopped
        HostBundleState hostState = HostBundleState.assertBundleState(bundle);
        hostState.aquireBundleLock(Method.STOP);

        try {

            // A concurrent thread may have uninstalled the bundle
            if (hostState.getState() == HostBundleState.UNINSTALLED)
                return;

            // #3 If the STOP_TRANSIENT option is not set then then set this bundle's persistent autostart setting to Stopped.
            // When the Framework is restarted and this bundle's autostart setting is Stopped, this bundle must not be
            // automatically started.
            if ((options & HostBundleState.STOP_TRANSIENT) == 0) {
                hostState.setPersistentlyStarted(false);
                hostState.setBundleActivationPolicyUsed(false);
            }

            // #4 If this bundle's state is not STARTING or ACTIVE then this method returns immediately
            int priorState = hostState.getState();
            if (priorState != HostBundleState.STARTING && priorState != HostBundleState.ACTIVE)
                return;

            // #5 This bundle's state is set to STOPPING
            // #6 A bundle event of type BundleEvent.STOPPING is fired
            hostState.changeState(HostBundleState.STOPPING);

            // #7 If this bundle's state was ACTIVE prior to setting the state to STOPPING,
            // the BundleActivator.stop(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is
            // specified, is called.
            // If that method throws an exception, this method must continue to stop this bundle and a BundleException must be
            // thrown after completion
            // of the remaining steps.
            Throwable rethrow = null;
            if (priorState == HostBundleState.ACTIVE) {
                if (hostState.bundleActivator != null) {
                    try {
                        hostState.bundleActivator.stop(hostState.getBundleContext());
                    } catch (Throwable t) {
                        rethrow = t;
                    }
                }
            }

            // #8 Any services registered by this bundle must be unregistered.
            // #9 Any services used by this bundle must be released.
            // #10 Any listeners registered by this bundle must be removed.
            hostState.removeServicesAndListeners();

            // #11 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while the
            // BundleActivator.stop method was running, a BundleException must be thrown
            if (hostState.getState() == HostBundleState.UNINSTALLED)
                throw MESSAGES.uninstalledDuringActivatorStop(bundle);

            // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
            hostState.destroyBundleContext();

            // #12 This bundle's state is set to RESOLVED
            // #13 A bundle event of type BundleEvent.STOPPED is fired
            hostState.changeState(HostBundleState.RESOLVED, BundleEvent.STOPPED);

            // Deactivate the service that represents bundle state ACTIVE
            hostState.getBundleManager().setServiceMode(hostState.getServiceName(HostBundleState.ACTIVE), Mode.NEVER);

            LOGGER.infoBundleStopped(bundle);

            if (rethrow != null)
                throw MESSAGES.errorDuringActivatorStop(rethrow, bundle);
        } finally {
            hostState.releaseBundleLock(Method.STOP);
        }
    }

    @Override
    public void uninstall(XBundle bundle) {
        BundleManager bundleManager = bundle.adapt(BundleManager.class);
        Deployment dep = bundle.adapt(Deployment.class);
        bundleManager.uninstallBundle(dep);
    }
}