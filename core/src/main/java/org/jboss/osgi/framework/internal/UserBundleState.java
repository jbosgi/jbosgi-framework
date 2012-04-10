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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BundleInstallProvider;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.spi.ConstantsHelper;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * This is the internal implementation of a Bundle based on a user {@link Deployment}.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
abstract class UserBundleState extends AbstractBundleState {

    private final Semaphore uninstallSemaphore = new Semaphore(1);

    private final ServiceName serviceName;
    private final List<UserBundleRevision> revisions = new CopyOnWriteArrayList<UserBundleRevision>();
    private Dictionary<String, String> headersOnUninstall;
    private BundleStorageState storageState;

    UserBundleState(FrameworkState frameworkState, long bundleId, Deployment dep) {
        super(frameworkState, bundleId, dep.getSymbolicName());
        this.serviceName = BundleManager.getServiceName(dep);
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
    BundleStorageState getBundleStorageState() {
        return storageState;
    }

    @Override
    public String getLocation() {
        return getCurrentBundleRevision().getLocation();
    }

    Deployment getDeployment() {
        return getCurrentBundleRevision().getDeployment();
    }

    RevisionContent getFirstContentRoot() {
        return getCurrentBundleRevision().getRootContent();
    }

    List<RevisionContent> getContentRoots() {
        return getCurrentBundleRevision().getContentList();
    }

    boolean isSingleton() {
        return getOSGiMetaData().isSingleton();
    }

    UserBundleRevision createRevision(Deployment deployment) throws BundleException {
        UserBundleRevision revision = createRevisionInternal(deployment);
        addRevision(revision);
        return revision;
    }

    abstract void initUserBundleState(OSGiMetaData metadata);

    abstract UserBundleRevision createRevisionInternal(Deployment dep) throws BundleException;

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
        return serviceName.append(ConstantsHelper.bundleState(state));
    }

    void addRevision(UserBundleRevision rev) {
        revisions.add(0, rev);
    }

    @Override
    UserBundleRevision getCurrentBundleRevision() {
        return revisions.get(0);
    }

    @Override
    List<AbstractBundleRevision> getAllBundleRevisions() {
        List<AbstractBundleRevision> result = new ArrayList<AbstractBundleRevision>(revisions);
        return Collections.unmodifiableList(result);
    }

    void clearOldRevisions() {
        UserBundleRevision rev = getCurrentBundleRevision();
        revisions.clear();
        revisions.add(rev);
    }

    @Override
    AbstractBundleRevision getBundleRevisionById(int revisionId) {
        for (AbstractBundleRevision rev : revisions) {
            if (rev.getRevisionId() == revisionId) {
                return rev;
            }
        }
        return null;
    }

    boolean aquireUninstallLock() {
        try {
            LOGGER.tracef("Aquire uninstall lock: %s", this);
            boolean result = uninstallSemaphore.tryAcquire(10, TimeUnit.SECONDS);
            if (result == false)
                LOGGER.errorCannotAquireUninstallLock(this);
            return result;
        } catch (InterruptedException ex) {
            LOGGER.debugf("Interupted while trying to uninstall bundle: %s", this);
            return false;
        }
    }

    void releaseUninstallLock() {
        LOGGER.tracef("Release uninstall lock: %s", this);
        uninstallSemaphore.release();
    }

    boolean hasActiveWires() {
        BundleWiring wiring = getCurrentBundleRevision().getWiring();
        return wiring != null ? wiring.isInUse() : false;
    }

    @Override
    void updateInternal(InputStream input) throws BundleException {
        // Not checking that the bundle is uninstalled as that already happened

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

        // Sent when the Framework detects that a bundle becomes unresolved; T
        // This could happen when the bundle is refreshed or updated.
        changeState(Bundle.INSTALLED, BundleEvent.UNRESOLVED);

        // Deactivate the service that represents bundle state RESOLVED
        getBundleManager().setServiceMode(getServiceName(RESOLVED), Mode.NEVER);

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

        FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
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
                rootFile = getFirstContentRoot().getVirtualFile();
            }
        }

        if (rootFile == null && input != null)
            rootFile = AbstractVFS.toVirtualFile(input);

        BundleStorageState storageState = createStorageState(getLocation(), rootFile);
        try {
            DeploymentFactoryPlugin deploymentPlugin = getFrameworkState().getDeploymentFactoryPlugin();
            Deployment dep = deploymentPlugin.createDeployment(storageState);
            OSGiMetaData metadata = deploymentPlugin.createOSGiMetaData(dep);
            dep.addAttachment(OSGiMetaData.class, metadata);
            dep.addAttachment(Bundle.class, this);
            UserBundleRevision brev = createRevision(dep);
            XEnvironment env = getFrameworkState().getEnvironment();
            env.installResources(brev);
        } catch (BundleException ex) {
            throw ex;
        }
    }

    BundleStorageState createStorageState(Deployment dep) throws BundleException {
        // The storage state exists when we re-create the bundle from persistent storage
        storageState = dep.getAttachment(BundleStorageState.class);
        if (storageState == null) {
            String location = dep.getLocation();
            VirtualFile rootFile = dep.getRoot();
            try {
                BundleStoragePlugin storagePlugin = getFrameworkState().getBundleStoragePlugin();
                storageState = storagePlugin.createStorageState(getBundleId(), location, rootFile);
                dep.addAttachment(BundleStorageState.class, storageState);
            } catch (IOException ex) {
                throw MESSAGES.bundleCannotSetupStorage(ex, rootFile);
            }
        }
        return storageState;
    }

    BundleStorageState createStorageState(String location, VirtualFile rootFile) throws BundleException {
        BundleStorageState storageState;
        try {
            BundleStoragePlugin storagePlugin = getFrameworkState().getBundleStoragePlugin();
            storageState = storagePlugin.createStorageState(getBundleId(), location, rootFile);
        } catch (IOException ex) {
            throw MESSAGES.bundleCannotSetupStorage(ex, rootFile);
        }
        return storageState;
    }

    /**
     * This method gets called by {@link PackageAdmin} when the bundle needs to be refreshed,
     * this means that all the old revisions are thrown out.
     */
    void refresh() throws BundleException {
        assertNotUninstalled();
        if (isResolved() == false)
            throw MESSAGES.illegalStateRefreshUnresolvedBundle(this);

        // Remove the revisions from the environment
        ModuleManagerPlugin moduleManager = getFrameworkState().getModuleManagerPlugin();
        UserBundleRevision currentRev = getCurrentBundleRevision();
        for (AbstractBundleRevision brev : getAllBundleRevisions()) {

            XEnvironment env = getFrameworkState().getEnvironment();
            if (currentRev != brev)
                env.uninstallResources(brev);
            
            if (brev instanceof HostBundleRevision) {
            	HostBundleRevision hostRev = (HostBundleRevision) brev;
            	for (FragmentBundleRevision fragRev : hostRev.getAttachedFragments()) {
            		if (fragRev != fragRev.getBundleState().getCurrentBundleRevision()) {
                        env.uninstallResources(fragRev);
            		}
            	}
            }
            
            ModuleIdentifier identifier = brev.getModuleIdentifier();
            moduleManager.removeModule(identifier);
        }

        clearOldRevisions();

        FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
        eventsPlugin.fireBundleEvent(this, BundleEvent.UNRESOLVED);

        // Update the the current revision
        currentRev.refreshRevision();

        changeState(Bundle.INSTALLED);

        // Deactivate the service that represents bundle state RESOLVED
        getBundleManager().setServiceMode(getServiceName(RESOLVED), Mode.NEVER);
    }

    @Override
    void uninstallInternal() throws BundleException {
        // #1 If this bundle's state is UNINSTALLED then an IllegalStateException is thrown
        assertNotUninstalled();
        headersOnUninstall = getHeaders(null);

        Deployment deployment = getDeployment();

        // Uninstall through the {@link BundleInstallProvider}
        BundleInstallProvider installHandler = getCoreServices().getInstallHandler();
        installHandler.uninstallBundle(deployment);

        LOGGER.infoBundleUninstalled(this);
    }

    void removeServices() {
        LOGGER.debugf("Remove services for: %s", this);
        BundleManager bundleManager = getBundleManager();
        bundleManager.setServiceMode(getServiceName(ACTIVE), Mode.REMOVE);
        bundleManager.setServiceMode(getServiceName(RESOLVED), Mode.REMOVE);
        bundleManager.setServiceMode(getServiceName(INSTALLED), Mode.REMOVE);
    }
}
