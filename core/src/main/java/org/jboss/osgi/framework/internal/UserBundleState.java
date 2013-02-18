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
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.Set;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.BundleStorage;
import org.jboss.osgi.framework.spi.DeploymentProvider;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.spi.AbstractBundleWiring;
import org.jboss.osgi.spi.ConstantsHelper;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;
import org.osgi.framework.wiring.BundleWiring;

/**
 * This is the internal implementation of a Bundle based on a user {@link Deployment}.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
abstract class UserBundleState<R extends UserBundleRevision> extends AbstractBundleState<R> {

    private final List<R> revisions = new ArrayList<R>();
    private final ServiceTarget serviceTarget;
    private final ServiceName serviceName;

    private Dictionary<String, String> headersOnUninstall;

    UserBundleState(FrameworkState frameworkState, R brev, ServiceName serviceName, ServiceTarget serviceTarget) {
        super(frameworkState, brev, brev.getStorageState().getBundleId());
        this.serviceTarget = serviceTarget;
        this.serviceName = serviceName;
        addBundleRevision(brev);
    }

    /**
     * Assert that the given bundle is an instance of {@link UserBundleState}
     */
    static UserBundleState<?> assertBundleState(Bundle bundle) {
        bundle = AbstractBundleState.assertBundleState(bundle);
        assert bundle instanceof UserBundleState : "Not an UserBundleState: " + bundle;
        return (UserBundleState<?>) bundle;
    }

    @Override
    public String getLocation() {
        return getBundleRevision().getLocation();
    }

    Deployment getDeployment() {
        return getBundleRevision().getDeployment();
    }

    @Override
    boolean isSingleton() {
        return getOSGiMetaData().isSingleton();
    }

    ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    abstract void initLazyActivation();

    abstract R createUpdateRevision(Deployment dep, OSGiMetaData metadata, StorageState storageState) throws BundleException;

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

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        // This method must continue to return Manifest header information while this bundle is in the UNINSTALLED state,
        // however the header values must only be available in the raw and default locale values
        if (headersOnUninstall != null)
            return headersOnUninstall;

        return super.getHeaders(locale);
    }

    @Override
    ServiceName getServiceName(int state) {
        if (state == 0) {
            return serviceName;
        } else if (state == Bundle.INSTALLED || state == Bundle.RESOLVED || state == Bundle.ACTIVE) {
            return serviceName.append(ConstantsHelper.bundleState(state));
        } else {
            return null;
        }
    }

    @Override
    BundleRevisions getBundleRevisions() {
        synchronized (revisions) {
            final Bundle bundle = this;
            final List<BundleRevision> bundleRevisions = new ArrayList<BundleRevision>(revisions.size());
            for (XBundleRevision rev : revisions) {
                bundleRevisions.add(rev);
            }
            return new BundleRevisions() {

                @Override
                public Bundle getBundle() {
                    return bundle;
                }

                @Override
                public List<BundleRevision> getRevisions() {
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
    void addBundleRevision(R rev) {
        synchronized (revisions) {
            super.addBundleRevision(rev);
            revisions.add(0, rev);
        }
    }

    @Override
    public R getBundleRevision() {
        return super.getBundleRevision();
    }

    @Override
    public List<XBundleRevision> getAllBundleRevisions() {
        synchronized (revisions) {
            List<XBundleRevision> result = new ArrayList<XBundleRevision>(revisions);
            return Collections.unmodifiableList(result);
        }
    }

    void clearOldRevisions() {
        synchronized (revisions) {
            R rev = getBundleRevision();
            revisions.clear();
            revisions.add(rev);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    R getBundleRevisionById(int revisionId) {
        synchronized (revisions) {
            for (XBundleRevision rev : revisions) {
                R stateRev = (R) rev;
                if (stateRev.getRevisionId() == revisionId) {
                    return stateRev;
                }
            }
            return null;
        }
    }

    @Override
    void updateInternal(InputStream input) throws BundleException {
        LockContext lockContext = null;
        LockManager lockManager = getFrameworkState().getLockManager();
        try {
            lockContext = lockManager.lockItems(Method.UPDATE, this);

            // We got the permit, now update
            updateInternalNow(input);

        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    private void updateInternalNow(InputStream input) throws BundleException {

        boolean restart = false;
        if (isFragment() == false) {
            int state = getState();
            if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING) {
                // If this bundle's state is ACTIVE, STARTING or STOPPING, this bundle is stopped as described in the
                // Bundle.stop method.
                // If Bundle.stop throws an exception, the exception is rethrown terminating the update.
                stopInternal(Bundle.STOP_TRANSIENT);
                if (state != Bundle.STOPPING)
                    restart = true;
            }
        }

        removeResolvedService();
        changeState(Bundle.INSTALLED, BundleEvent.UNRESOLVED);

        try {
            // If the Framework is unable to install the updated version of this bundle, the original
            // version of this bundle must be restored and a BundleException must be thrown after
            // completion of the remaining steps.
            createUpdateRevision(input);
        } catch (BundleException ex) {
            if (restart)
                startInternal(Bundle.START_TRANSIENT);
            throw ex;
        } catch (Exception ex) {
            BundleException be = new BundleException("Problem updating bundle");
            be.initCause(ex);
            if (restart)
                startInternal(Bundle.START_TRANSIENT);
            throw be;
        }

        FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
        eventsPlugin.fireBundleEvent(this, BundleEvent.UPDATED);
        if (restart) {
            // If this bundle's state was originally ACTIVE or STARTING, the updated bundle is started as described in the
            // Bundle.start method.
            // If Bundle.start throws an exception, a Framework event of type FrameworkEvent.ERROR is fired containing the
            // exception
            try {
                startInternal(Bundle.START_TRANSIENT);
            } catch (BundleException e) {
                eventsPlugin.fireFrameworkEvent(this, FrameworkEvent.ERROR, e);
            }
        }

        LOGGER.infoBundleUpdated(this);
        updateLastModified();
    }

    /**
     * Creates a new Bundle Revision when the bundle is updated. Multiple Bundle Revisions can co-exist at the same time.
     *
     * @param input The stream to create the bundle revision from or <tt>null</tt> if the new revision needs to be created from
     *              the same location as where the bundle was initially installed.
     * @throws Exception If the bundle cannot be read, or if the update attempt to change the BSN.
     */
    private void createUpdateRevision(InputStream input) throws Exception {
        VirtualFile rootFile = null;

        // If the specified InputStream is null, the Framework must create the InputStream from
        // which to read the updated bundle by interpreting, in an implementation dependent manner,
        // this bundle's Bundle-UpdateLocation Manifest header, if present, or this bundle's
        // original location.
        if (input == null) {
            String updateLocation = getOSGiMetaData().getHeader(Constants.BUNDLE_UPDATELOCATION);
            if (updateLocation != null) {
                URL updateURL = new URL(updateLocation);
                rootFile = AbstractVFS.toVirtualFile(updateURL);
            } else {
                rootFile = getDeployment().getRoot();
            }
        }

        if (rootFile == null && input != null)
            rootFile = AbstractVFS.toVirtualFile(input);

        BundleStorage storagePlugin = getFrameworkState().getBundleStorage();
        StorageState storageState = createStorageState(storagePlugin, getLocation(), rootFile);
        try {
            DeploymentProvider deploymentPlugin = getFrameworkState().getDeploymentProvider();
            Deployment dep = deploymentPlugin.createDeployment(storageState);
            OSGiMetaData metadata = deploymentPlugin.createOSGiMetaData(dep);
            dep.addAttachment(OSGiMetaData.class, metadata);
            dep.addAttachment(Bundle.class, this);

            // Check for symbolic name, version uniqueness
            String symbolicName = metadata.getBundleSymbolicName();
            Version bundleVersion = metadata.getBundleVersion();
            getBundleManager().checkUniqunessPolicy(this, symbolicName, bundleVersion, CollisionHook.UPDATING);

            R updateRevision = createUpdateRevision(dep, metadata, storageState);
            addBundleRevision(updateRevision);
            XEnvironment env = getFrameworkState().getEnvironment();
            env.installResources(updateRevision);
        } catch (BundleException ex) {
            storagePlugin.deleteStorageState(storageState);
            throw ex;
        } catch (RuntimeException ex) {
            storagePlugin.deleteStorageState(storageState);
            throw ex;
        }
    }

    private StorageState createStorageState(BundleStorage storagePlugin, String location, VirtualFile rootFile) throws BundleException {
        StorageState storageState;
        try {
            int startlevel = getFrameworkState().getStartLevelSupport().getInitialBundleStartLevel();
            storageState = storagePlugin.createStorageState(getBundleId(), location, startlevel, rootFile);
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
        if (isResolved() == false)
            throw MESSAGES.illegalStateRefreshUnresolvedBundle(this);

        // Remove the revisions from the environment
        ModuleManager moduleManager = getFrameworkState().getModuleManager();
        R currentRev = getBundleRevision();
        for (XBundleRevision brev : getAllBundleRevisions()) {

            UserBundleRevision userRev = UserBundleRevision.assertBundleRevision(brev);
            XEnvironment env = getFrameworkState().getEnvironment();
            if (currentRev != brev) {
                env.uninstallResources(brev);
                userRev.close();
            }

            if (brev instanceof HostBundleRevision) {
                HostBundleRevision hostRev = (HostBundleRevision) brev;
                for (FragmentBundleRevision fragRev : hostRev.getAttachedFragments()) {
                    if (fragRev != fragRev.getBundle().getBundleRevision()) {
                        env.uninstallResources(fragRev);
                        fragRev.close();
                    }
                }

                ModuleIdentifier identifier = brev.getModuleIdentifier();
                moduleManager.removeModule(brev, identifier);
            }
        }

        removeResolvedService();

        clearOldRevisions();

        FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
        eventsPlugin.fireBundleEvent(this, BundleEvent.UNRESOLVED);

        // Update the the current revision
        currentRev.refreshRevision();

        changeState(Bundle.INSTALLED);
    }

    void removeResolvedService() {
        ServiceName resolvedName = getServiceName(RESOLVED);
        getBundleManagerPlugin().setServiceMode(resolvedName, Mode.REMOVE);
    }

    @Override
    void uninstallInternal(int options) {
        headersOnUninstall = getHeaders(null);

        LockContext lockContext = null;
        LockManager lockManager = getFrameworkState().getLockManager();
        try {
            FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
            lockContext = lockManager.lockItems(Method.UNINSTALL, wireLock, this);

            // We got the permit, now uninstall
            uninstallInternalNow(options);
        } finally {
            lockManager.unlockItems(lockContext);
        }

        // Remove the Bundle INSTALL service after we changed the state
        LOGGER.debugf("Remove service for: %s", this);
        getBundleManagerPlugin().setServiceMode(getServiceName(Bundle.INSTALLED), Mode.REMOVE);
    }

    void uninstallInternalNow(int options) {

        int state = getState();
        if (state == Bundle.UNINSTALLED)
            return;

        // #2 If the bundle's state is ACTIVE, STARTING or STOPPING, the bundle is stopped
        if (isFragment() == false) {
            if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING) {
                try {
                    stopInternal(options);
                } catch (Exception ex) {
                    // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is fired
                    getBundleManagerPlugin().fireFrameworkError(this, "stopping bundle: " + this, ex);
                }
            }
        }

        // Check if the bundle has still active wires
        boolean activeWires = hasActiveWiresWhileUninstalling();
        if (activeWires == false) {
            getBundleManagerPlugin().unresolveBundle(this);
        }

        // #3 This bundle's state is set to UNINSTALLED
        changeState(Bundle.UNINSTALLED, 0);

        // Check if the bundle has still active wires
        if (activeWires == false) {
            // #5 This bundle and any persistent storage area provided for this bundle by the Framework are removed
            getBundleManagerPlugin().removeBundle(this, options);
        }

        // Remove other uninstalled bundles that now also have no active wires any more
        Set<XBundle> uninstalled = getBundleManager().getBundles(Bundle.UNINSTALLED);
        for (Bundle auxState : uninstalled) {
            UserBundleState<?> auxUser = UserBundleState.assertBundleState(auxState);
            if (auxUser.hasActiveWiresWhileUninstalling() == false) {
                getBundleManagerPlugin().removeBundle(auxUser, options);
            }
        }

        // #4 A bundle event of type BundleEvent.UNINSTALLED is fired
        fireBundleEvent(BundleEvent.UNINSTALLED);

        LOGGER.infoBundleUninstalled(this);
    }

    private boolean hasActiveWiresWhileUninstalling() {
        BundleWiring wiring = adapt(BundleWiring.class);
        return wiring != null ? ((AbstractBundleWiring) wiring).isInUseForUninstall() : false;
    }
}
