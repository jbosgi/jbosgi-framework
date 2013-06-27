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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger.Level;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.BundleLifecycle;
import org.jboss.osgi.framework.spi.BundleLifecycle.BundleRefreshPolicy;
import org.jboss.osgi.framework.spi.DeploymentProvider;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.ServiceState;
import org.jboss.osgi.framework.spi.StartLevelManager;
import org.jboss.osgi.framework.spi.StorageManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XWiringSupport;
import org.jboss.osgi.resolver.spi.AbstractBundleWiring;
import org.jboss.osgi.resolver.spi.ResolverHookException;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Wire;

/**
 * This is the internal implementation of a Bundle based on a user {@link Deployment}.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
class UserBundleState extends AbstractBundleState<UserBundleRevision> {

    private final AtomicBoolean alreadyStarting = new AtomicBoolean();
    private final AtomicBoolean awaitLazyActivation = new AtomicBoolean();
    private final AtomicInteger revisionIndex = new AtomicInteger();
    private final List<UserBundleRevision> revisions = new ArrayList<UserBundleRevision>();

    private BundleActivator bundleActivator;

    UserBundleState(FrameworkState frameworkState, UserBundleRevision brev) {
        super(frameworkState, brev, brev.getStorageState().getBundleId());
    }

    /**
     * Assert that the given bundle is an instance of {@link UserBundleState}
     */
    static UserBundleState assertBundleState(Bundle bundle) {
        bundle = AbstractBundleState.assertBundleState(bundle);
        assert bundle instanceof UserBundleState : "Not an UserBundleState: " + bundle;
        return (UserBundleState) bundle;
    }

    @Override
    public String getLocation() {
        return getBundleRevision().getLocation();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T adapt(Class<T> type) {
        T result = super.adapt(type);
        if (result == null) {
            if (type.isAssignableFrom(Deployment.class)) {
                result = (T) getDeployment();
            }
        }
        return result;
    }

    Deployment getDeployment() {
        return getBundleRevision().getDeployment();
    }

    @Override
    boolean isSingleton() {
        return getOSGiMetaData().isSingleton();
    }

    void initLazyActivation() {
        if (!isFragment()) {
            awaitLazyActivation.set(isActivationLazy());
        }
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
                getBundleManager().startBundleLifecycle(this, options);
            }
        }
    }

    void setBundleActivationPolicyUsed(boolean usePolicy) {
        StorageState storageState = getStorageState();
        storageState.setBundleActivationPolicyUsed(usePolicy);
    }

    boolean isAlreadyStarting() {
        return alreadyStarting.get();
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        // This method must continue to return Manifest header information while this bundle is in the UNINSTALLED state,
        // however the header values must only be available in the raw and default locale values
        Dictionary<String, String> headersOnUninstall = getBundleRevision().getHeadersOnUninstall();
        if (headersOnUninstall != null)
            return headersOnUninstall;

        return super.getHeaders(locale);
    }

    @Override
    BundleRevisions getBundleRevisions() {
        synchronized (revisions) {
            final Bundle bundle = this;
            return new BundleRevisions() {

                @Override
                public Bundle getBundle() {
                    return bundle;
                }

                @Override
                public List<BundleRevision> getRevisions() {
                    List<BundleRevision> bundleRevisions = new ArrayList<BundleRevision>(revisions);
                    return Collections.unmodifiableList(bundleRevisions);
                }

                @Override
                public String toString() {
                    return bundle + ": " + revisions;
                }
            };
        }
    }

    @Override
    void addBundleRevision(UserBundleRevision brev) {
        synchronized (revisions) {
            super.addBundleRevision(brev);
            if (!isFragment()) {
                ModuleManager moduleManager = getFrameworkState().getModuleManager();
                ModuleIdentifier moduleIdentifier = moduleManager.getModuleIdentifier(brev);
                brev.putAttachment(IntegrationConstants.MODULE_IDENTIFIER_KEY, moduleIdentifier);
            }
            revisionIndex.incrementAndGet();
            revisions.add(0, brev);
        }
    }

    int getRevisionIndex() {
        return revisionIndex.get();
    }

    List<XBundleRevision> getAllBundleRevisions() {
        synchronized (revisions) {
            List<XBundleRevision> result = new ArrayList<XBundleRevision>(revisions);
            return Collections.unmodifiableList(result);
        }
    }

    void removeRevision(UserBundleRevision rev) {
        synchronized (revisions) {
            revisions.remove(rev);
        }
    }

    @Override
    UserBundleRevision getBundleRevisionById(int revisionId) {
        synchronized (revisions) {
            for (UserBundleRevision rev : revisions) {
                if (rev.getRevisionId() == revisionId) {
                    return rev;
                }
            }
            return null;
        }
    }

    @Override
    void updateInternal(InputStream input) throws BundleException {

        LOGGER.debugf("Updating bundle: %s", this);

        boolean restart = false;

        if (isFragment() == false) {
            int state = getState();
            if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING) {
                // If this bundle's state is ACTIVE, STARTING or STOPPING, this bundle is stopped
                // If Bundle.stop throws an exception, the exception is rethrown terminating the update.
                getBundleManager().stopBundle(this, Bundle.STOP_TRANSIENT);
                if (state != Bundle.STOPPING)
                    restart = true;
            }
        }

        changeState(Bundle.INSTALLED, BundleEvent.UNRESOLVED);

        // If the Framework is unable to install the updated version of this bundle, the original
        // version of this bundle must be restored and a BundleException must be thrown after
        // completion of the remaining steps.
        UserBundleRevision currentRev = getBundleRevision();
        try {
            // Create the update revision
            createUpdateRevision(input);

            // Make the {@link BundleWiring} for the old {@link BundleRevision} uneffective
            currentRev.getWiringSupport().makeUneffective();
        } catch (Exception ex) {
            boolean isbe = (ex instanceof BundleException);
            BundleException be = isbe ? (BundleException) ex : MESSAGES.cannotUpdateBundle(ex, this);
            if (restart)
                getBundleManager().startBundle(this, Bundle.START_TRANSIENT);
            throw be;
        }

        FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
        eventsPlugin.fireBundleEvent(this, BundleEvent.UPDATED);

        if (restart) {
            // If this bundle's state was originally ACTIVE or STARTING, the updated bundle is started
            // If Bundle.start throws an exception, a Framework event of type FrameworkEvent.ERROR is fired
            try {
                getBundleManager().startBundle(this, Bundle.START_TRANSIENT);
            } catch (BundleException e) {
                eventsPlugin.fireFrameworkEvent(this, FrameworkEvent.ERROR, e);
            }
        }

        updateLastModified();

        LOGGER.infoBundleUpdated(this);
    }

    /**
     * Creates a new Bundle Revision when the bundle is updated. Multiple Bundle Revisions can co-exist at the same time.
     *
     * @param input The stream to create the bundle revision from or <tt>null</tt> if the new revision needs to be created from
     *              the same location as where the bundle was initially installed.
     * @throws Exception If the bundle cannot be read, or if the update attempt to change the BSN.
     */
    private UserBundleRevision createUpdateRevision(InputStream input) throws IOException, BundleException {

        String updateLocation = getOSGiMetaData().getHeader(Constants.BUNDLE_UPDATELOCATION);

        // If the specified InputStream is null, the Framework must create the InputStream from
        // which to read the updated bundle by interpreting, in an implementation dependent manner,
        // this bundle's Bundle-UpdateLocation Manifest header, if present, or this bundle's
        // original location.
        VirtualFile rootFile = null;
        if (input == null) {
            if (updateLocation != null) {
                URL updateURL = new URL(updateLocation);
                rootFile = AbstractVFS.toVirtualFile(updateURL);
            } else {
                rootFile = getDeployment().getRoot();
            }
        }

        if (rootFile == null && input != null)
            rootFile = AbstractVFS.toVirtualFile(input);

        StorageState storageState = createUpdateStorageState(getLocation(), rootFile);
        DeploymentProvider deploymentManager = getFrameworkState().getDeploymentProvider();
        Deployment dep = deploymentManager.createDeployment(storageState);
        OSGiMetaData metadata = deploymentManager.createOSGiMetaData(dep);
        dep.putAttachment(IntegrationConstants.OSGI_METADATA_KEY, metadata);
        dep.putAttachment(IntegrationConstants.BUNDLE_KEY, this);
        dep.setBundleUpdate(true);
        dep.setAutoStart(false);

        // Check for symbolic name, version uniqueness
        String symbolicName = metadata.getBundleSymbolicName();
        Version bundleVersion = metadata.getBundleVersion();
        BundleManagerPlugin bundleManager = getBundleManager();
        bundleManager.checkUniqunessPolicy(this, symbolicName, bundleVersion, CollisionHook.UPDATING);

        BundleContext syscontext = getFrameworkState().getSystemBundle().getBundleContext();
        BundleLifecycle bundleLifecycle = getFrameworkState().getCoreServices().getBundleLifecycle();
        XBundleRevision brev = bundleLifecycle.createBundleRevision(syscontext, dep);
        return UserBundleRevision.assertBundleRevision(brev);
    }

    private StorageState createUpdateStorageState(String location, VirtualFile rootFile) throws BundleException {
        StorageState storageState;
        try {
            int startLevel = adapt(BundleStartLevel.class).getStartLevel();
            StorageManager storageManager = getFrameworkState().getStorageManager();
            storageState = storageManager.createStorageState(getBundleId(), location, startLevel, rootFile);
        } catch (IOException ex) {
            throw MESSAGES.cannotSetupStorage(ex, rootFile);
        }
        return storageState;
    }

    /**
     * This method gets called by when the bundle needs to be refreshed,
     * this means that all the old revisions are thrown out.
     */
    void refresh() throws BundleException {
        assertNotUninstalled();

        LOGGER.debugf("Refreshing bundle: %s", this);

        // Get the {@link BundleRefreshPolicy}
        CoreServices coreServices = getFrameworkState().getCoreServices();
        BundleLifecycle bundleLifecycle = coreServices.getBundleLifecycle();
        BundleRefreshPolicy refreshPolicy = bundleLifecycle.getBundleRefreshPolicy();

        // Remove the {@link Module} and the associated ClassLoader
        BundleManagerPlugin bundleManager = getBundleManager();
        bundleManager.unresolveBundle(this);

        // Initialize bundle refresh
        UserBundleRevision currentRev = getBundleRevision();
        refreshPolicy.startBundleRefresh(this);
        try {
            // Remove the revisions from the environment
            for (XBundleRevision brev : getAllBundleRevisions()) {

                if (currentRev != brev) {
                    bundleManager.removeRevisionLifecycle(brev, 0);
                }

                // Cleanup stale fragment revisions
                if (brev instanceof HostBundleRevision) {
                    HostBundleRevision hostRev = (HostBundleRevision) brev;
                    for (FragmentBundleRevision fragRev : hostRev.getAttachedFragments()) {
                        if (fragRev != fragRev.getBundle().getBundleRevision()) {
                            bundleManager.removeRevisionLifecycle(fragRev, 0);
                        }
                    }
                }
            }

            awaitLazyActivation.set(false);
            revisionIndex.set(1);

            FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
            eventsPlugin.fireBundleEvent(this, BundleEvent.UNRESOLVED);

            refreshPolicy.refreshCurrentRevision(currentRev);

            changeState(Bundle.INSTALLED);
        } finally {
            refreshPolicy.endBundleRefresh(this);
        }

        LOGGER.infoBundleRefreshed(this);
    }

    @Override
    void uninstallInternal(int options) throws BundleException {

        int state = getState();
        if (state == Bundle.UNINSTALLED)
            return;

        LOGGER.debugf("Uninstalling bundle: %s", this);

        BundleManagerPlugin bundleManager = getBundleManager();
        getBundleRevision().setHeadersOnUninstall(getHeaders(null));

        // #2 If the bundle's state is ACTIVE, STARTING or STOPPING, the bundle is stopped
        if (isFragment() == false) {
            if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING) {
                try {
                    stopInternal(options);
                } catch (Exception ex) {
                    // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is fired
                    bundleManager.fireFrameworkError(this, "stopping bundle: " + this, ex);
                }
            }
        }

        // Check if the bundle has still active wires
        boolean activeWires = hasActiveWiresWhileUninstalling();
        if (activeWires == false)
            bundleManager.unresolveBundle(this);
        else
            LOGGER.debugf("Has active wires: %s", this);

        // Make the current {@link BundleWiring} uneffective and fire the UNRESOLVED event
        getBundleRevision().getWiringSupport().makeUneffective();
        FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
        eventsPlugin.fireBundleEvent(this, BundleEvent.UNRESOLVED);

        // #3 This bundle's state is set to UNINSTALLED
        changeState(Bundle.UNINSTALLED, 0);

        // #5 This bundle and any persistent storage area provided for this bundle by the Framework are removed
        if ((options & Bundle.STOP_TRANSIENT) == 0) {
            StorageManager storagePlugin = getFrameworkState().getStorageManager();
            storagePlugin.deleteStorageState(getStorageState());
        }

        // Remove the bundle from the {@link Environment}
        if (activeWires == false) {
            bundleManager.removeBundle(this, options);
        }

        // Remove other uninstalled bundles that now also are not in use any more
        Set<XBundle> uninstalled = bundleManager.getBundles(Bundle.UNINSTALLED);
        for (XBundle auxState : uninstalled) {
            if (auxState != this) {
                boolean bundleInUse = false;
                XBundleRevision brev = auxState.getBundleRevision();
                XWiringSupport wiringSupport = brev.getWiringSupport();
                if (!wiringSupport.isEffective()) {
                    BundleWiring bwiring = (BundleWiring) wiringSupport.getWiring(false);
                    if (bwiring != null && bwiring.isInUse()) {
                        bundleInUse = true;
                        break;
                    }
                }
                if (bundleInUse == false) {
                    UserBundleState auxUser = UserBundleState.assertBundleState(auxState);
                    bundleManager.unresolveBundle(auxUser);
                    bundleManager.removeBundle(auxUser, options);
                }
            }
        }

        // #4 A bundle event of type BundleEvent.UNINSTALLED is fired
        fireBundleEvent(BundleEvent.UNINSTALLED);

        LOGGER.infoBundleUninstalled(this);
    }

    @Override
    public boolean isFragment() {
        return getBundleRevision().isFragment();
    }

    @Override
    UserBundleContext createContextInternal() {
        return new UserBundleContext(this);
    }

    @Override
    void startInternal(int options) throws BundleException {

        // #1 If this bundle is in the process of being activated or deactivated
        // then this method must wait for activation or deactivation to complete before continuing.
        // If this does not occur in a reasonable time, a BundleException is thrown

        // We got the permit, now start
        try {
            alreadyStarting.set(true);
            startInternalNow(options);
        } finally {
            alreadyStarting.set(false);
        }
    }

    private void startInternalNow(int options) throws BundleException {

        // #2 If this bundle's state is ACTIVE then this method returns immediately.
        if (getState() == ACTIVE)
            return;

        LOGGER.debugf("Starting bundle: %s", this);

        // #3 Set this bundle's autostart setting
        persistAutoStartSettings(options);

        // If the Framework's current start level is less than this bundle's start level
        if (startLevelValidForStart() == false) {
            StartLevelManager plugin = getFrameworkState().getStartLevelManager();
            int frameworkState = getBundleManager().getSystemBundle().getState();
            Level level = (plugin.isFrameworkStartLevelChanging() || frameworkState != Bundle.ACTIVE) ? Level.DEBUG : Level.INFO;
            LOGGER.log(level, MESSAGES.bundleStartLevelNotValid(getBundleStartLevel(), plugin.getFrameworkStartLevel(), this));
            return;
        }

        // #4 If this bundle's state is not RESOLVED, an attempt is made to resolve this bundle.
        // If the Framework cannot resolve this bundle, a BundleException is thrown.
        if (ensureResolved(true) == false) {
            Exception resex = getLastResolverException();
            int type = (resex instanceof ResolverHookException ? BundleException.REJECTED_BY_HOOK : BundleException.RESOLVE_ERROR);
            throw new BundleException(MESSAGES.cannotResolveBundle(this), type, resex);
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
                bundleActivator = getDeployment().getAttachment(IntegrationConstants.BUNDLE_ACTIVATOR_KEY);
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
                removeServicesAndListeners();

                // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
                destroyBundleContext();

                // #8.6 This bundle's state is set to RESOLVED
                // #8.7 A bundle event of type BundleEvent.STOPPED is fired
                changeState(Bundle.RESOLVED);

                // #8.8 A BundleException is then thrown
                if (th instanceof BundleException)
                    throw (BundleException) th;

                throw new BundleException(MESSAGES.cannotStartBundle(this), BundleException.ACTIVATOR_ERROR, th);
            }
        }

        // #9 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while
        // the BundleActivator.start method was running, a BundleException is thrown
        if (getState() == Bundle.UNINSTALLED)
            throw MESSAGES.uninstalledDuringActivatorStart(this);

        // #10 This bundle's state is set to ACTIVE.
        // #11 A bundle event of type BundleEvent.STARTED is fired
        changeState(Bundle.ACTIVE);

        LOGGER.infoBundleStarted(this);
    }

    @Override
    void stopInternal(int options) throws BundleException {

        // #2 If this bundle is in the process of being activated or deactivated
        // then this method must wait for activation or deactivation to complete before continuing.
        // If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle was unable to be
        // stopped

        // We got the permit, now stop
        stopInternalNow(options);
    }

    private void stopInternalNow(int options) throws BundleException {

        LOGGER.debugf("Stopping bundle: %s", this);

        int priorState = getState();

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
        removeServicesAndListeners();

        // #11 If this bundle's state is UNINSTALLED, because this bundle was uninstalled while the
        // BundleActivator.stop method was running, a BundleException must be thrown
        if (getState() == Bundle.UNINSTALLED)
            throw MESSAGES.uninstalledDuringActivatorStop(this);

        // The BundleContext object is valid during STARTING, STOPPING, and ACTIVE
        destroyBundleContext();

        // #12 This bundle's state is set to RESOLVED
        // #13 A bundle event of type BundleEvent.STOPPED is fired
        changeState(Bundle.RESOLVED, BundleEvent.STOPPED);

        if (rethrow != null) {
            throw MESSAGES.cannotStopBundle(rethrow, this);
        }

        LOGGER.infoBundleStopped(this);
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
    @SuppressWarnings("deprecation")
    void assertStartConditions(int options) throws BundleException {
        super.assertStartConditions(options);

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

        // If the Framework's current start level is less than this bundle's start level
        if (startLevelValidForStart() == false) {
            // If the START_TRANSIENT option is set, then a BundleException is thrown
            // indicating this bundle cannot be started due to the Framework's current start level
            if ((options & START_TRANSIENT) != 0)
                throw MESSAGES.cannotStartBundleDueToStartLevel();

            // Set this bundle's autostart setting
            persistAutoStartSettings(options);
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
        StartLevelManager startLevelPlugin = getFrameworkState().getStartLevelManager();
        return startLevelPlugin.getBundleStartLevel(this);
    }

    void setPersistentlyStarted(boolean started) {
        StartLevelManager startLevelPlugin = getFrameworkState().getStartLevelManager();
        startLevelPlugin.setBundlePersistentlyStarted(this, started);
    }

    private boolean startLevelValidForStart() {
        StartLevelManager startLevelPlugin = getFrameworkState().getStartLevelManager();
        return startLevelPlugin.getBundleStartLevel(this) <= startLevelPlugin.getFrameworkStartLevel();
    }

    private boolean isBundleActivationPolicyUsed() {
        StorageState storageState = getStorageState();
        return storageState.isBundleActivationPolicyUsed();
    }

    private void removeServicesAndListeners() {
        // Any services registered by this bundle must be unregistered.
        // Any services used by this bundle must be released.
        for (ServiceState<?> serviceState : getRegisteredServicesInternal()) {
            serviceState.unregisterInternal();
        }

        // Any listeners registered by this bundle must be removed
        FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
        eventsPlugin.removeBundleListeners(this);
    }

    private boolean hasActiveWiresWhileUninstalling() {
        BundleWiring wiring = getBundleWiring();
        return (wiring != null ? ((AbstractBundleWiring) wiring).isInUseForUninstall() : false);
    }
}
