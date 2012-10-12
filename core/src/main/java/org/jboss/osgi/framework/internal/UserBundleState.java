package org.jboss.osgi.framework.internal;
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

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.BundleLifecyclePlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundleRevision;
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

    private final List<UserBundleRevision> revisions = new CopyOnWriteArrayList<UserBundleRevision>();
    private final ServiceName serviceName;

    private Dictionary<String, String> headersOnUninstall;

    UserBundleState(FrameworkState frameworkState, UserBundleRevision brev, ServiceName serviceName) {
        super(frameworkState, brev, brev.getStorageState().getBundleId());
        this.serviceName = serviceName;
        addBundleRevision(brev);
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

    Deployment getDeployment() {
        return getBundleRevision().getDeployment();
    }

    RevisionContent getFirstContentRoot() {
        return getBundleRevision().getRootContent();
    }

    List<RevisionContent> getContentRoots() {
        return getBundleRevision().getContentList();
    }

    boolean isSingleton() {
        return getOSGiMetaData().isSingleton();
    }

    abstract void initLazyActivation();

    abstract UserBundleRevision createUpdateRevision(Deployment dep, OSGiMetaData metadata, InternalStorageState storageState) throws BundleException;

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

    void addBundleRevision(BundleStateRevision rev) {
        super.addBundleRevision(rev);
        revisions.add(0, (UserBundleRevision) rev);
    }

    @Override
    public UserBundleRevision getBundleRevision() {
        return (UserBundleRevision) super.getBundleRevision();
    }

    @Override
    public List<XBundleRevision> getAllBundleRevisions() {
        List<XBundleRevision> result = new ArrayList<XBundleRevision>(revisions);
        return Collections.unmodifiableList(result);
    }

    void clearOldRevisions() {
        UserBundleRevision rev = getBundleRevision();
        revisions.clear();
        revisions.add(rev);
    }

    @Override
    BundleStateRevision getBundleRevisionById(int revisionId) {
        for (BundleStateRevision rev : revisions) {
            if (rev.getRevisionId() == revisionId) {
                return rev;
            }
        }
        return null;
    }

    boolean hasActiveWires() {
        BundleWiring wiring = getBundleRevision().getWiring();
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

        // Sent when the Framework detects that a bundle becomes unresolved
        // This could happen when the bundle is refreshed or updated.
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

        BundleStoragePlugin storagePlugin = getFrameworkState().getBundleStoragePlugin();
        InternalStorageState storageState = createStorageState(storagePlugin, getLocation(), rootFile);
        try {
            DeploymentFactoryPlugin deploymentPlugin = getFrameworkState().getDeploymentFactoryPlugin();
            Deployment dep = deploymentPlugin.createDeployment(storageState);
            OSGiMetaData metadata = deploymentPlugin.createOSGiMetaData(dep);
            dep.addAttachment(OSGiMetaData.class, metadata);
            dep.addAttachment(Bundle.class, this);
            UserBundleRevision updateRevision = createUpdateRevision(dep, metadata, storageState);
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

    private InternalStorageState createStorageState(BundleStoragePlugin storagePlugin, String location, VirtualFile rootFile) throws BundleException {
        InternalStorageState storageState;
        try {
            int startlevel = getCoreServices().getStartLevel().getInitialBundleStartLevel();
            storageState = storagePlugin.createStorageState(getBundleId(), location, startlevel, rootFile);
        } catch (IOException ex) {
            throw MESSAGES.cannotSetupStorage(ex, rootFile);
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
        UserBundleRevision currentRev = getBundleRevision();
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
            }

            ModuleIdentifier identifier = brev.getModuleIdentifier();
            moduleManager.removeModule(brev, identifier);
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
        headersOnUninstall = getHeaders(null);
        BundleLifecyclePlugin lifecyclePlugin = getCoreServices().getBundleLifecyclePlugin();
        lifecyclePlugin.uninstall(this, DefaultBundleLifecycleHandler.INSTANCE);
    }

    void removeServices() {
        LOGGER.debugf("Remove services for: %s", this);
        BundleManagerPlugin bundleManager = getBundleManager();
        bundleManager.setServiceMode(getServiceName(ACTIVE), Mode.REMOVE);
        bundleManager.setServiceMode(getServiceName(RESOLVED), Mode.REMOVE);
        bundleManager.setServiceMode(getServiceName(INSTALLED), Mode.REMOVE);
    }
}
