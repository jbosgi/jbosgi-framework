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
import static org.jboss.osgi.framework.internal.InternalConstants.REVISION_IDENTIFIER_KEY;
import static org.jboss.osgi.framework.spi.IntegrationConstants.OSGI_METADATA_KEY;
import static org.jboss.osgi.framework.spi.IntegrationConstants.STORAGE_STATE_KEY;

import java.io.IOException;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Substate;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.NativeCode;
import org.jboss.osgi.framework.spi.StorageManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.XResource.State;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;

/**
 * Service associated with a user installed {@link XBundleRevision}
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Mar-2013
 */
abstract class UserBundleRevisionService<R extends UserBundleRevision> extends AbstractBundleRevisionService<R> {

    private final Deployment deployment;
    private final ServiceName serviceName;
    private final BundleContext targetContext;
    private R bundleRevision;

    UserBundleRevisionService(FrameworkState frameworkState, BundleContext targetContext, Deployment deployment) {
        super(frameworkState);
        this.deployment = deployment;
        this.targetContext = targetContext;
        this.serviceName = getBundleManager().getServiceName(deployment);
    }

    ServiceController<R> install(ServiceTarget serviceTarget, ServiceListener<XBundleRevision> listener) {
        ServiceBuilder<R> builder = serviceTarget.addService(serviceName, this);
        addServiceDependencies(builder);
        if (listener != null) {
            builder.addListener(listener);
        }
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<R> builder) {
        builder.addDependency(IntegrationServices.FRAMEWORK_CORE_SERVICES);
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        LOGGER.debugf("Creating %s %s", getClass().getSimpleName(), serviceName);
        StorageState storageState = null;
        try {
            Deployment dep = deployment;
            RevisionIdentifier revIdentifier = dep.getAttachment(REVISION_IDENTIFIER_KEY);
            storageState = createStorageState(dep, revIdentifier);
            OSGiMetaData metadata = dep.getAttachment(OSGI_METADATA_KEY);
            ServiceName serviceName = startContext.getController().getName();
            bundleRevision = createBundleRevision(dep, storageState, serviceName, startContext.getChildTarget());
            bundleRevision.putAttachment(XResource.RESOURCE_IDENTIFIER_KEY, revIdentifier.getRevisionId());
            validateBundleRevision(bundleRevision, metadata);
            processNativeCode(bundleRevision, metadata, dep);
            XBundle bundle = (XBundle) dep.getAttachment(IntegrationConstants.BUNDLE_KEY);
            if (bundle == null) {
                bundle = createBundleState(bundleRevision);
                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                userBundle.addBundleRevision(bundleRevision);
                dep.putAttachment(IntegrationConstants.BUNDLE_KEY, userBundle);
                userBundle.initLazyActivation();
                installBundleRevision(bundleRevision);
                userBundle.changeState(Bundle.INSTALLED, 0);
                LOGGER.infoBundleInstalled(bundle);
                FrameworkEvents events = getFrameworkState().getFrameworkEvents();
                events.fireBundleEvent(targetContext, bundle, BundleEvent.INSTALLED);
            } else {
                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                userBundle.addBundleRevision(bundleRevision);
                installBundleRevision(bundleRevision);
            }
        } catch (BundleException ex) {
            if (storageState != null) {
                StorageManager storagePlugin = getFrameworkState().getStorageManager();
                storagePlugin.deleteStorageState(storageState);
            }
            throw new StartException(ex);
        }
    }

    @Override
    public void stop(StopContext stopContext) {
        XBundle bundle = bundleRevision.getBundle();
        if (uninstallRequired(bundle)) {
            try {
                boolean serverShutdown = getServerShutdown();
                int options = getUninstallOptions(serverShutdown);
                getBundleManager().uninstallBundle(bundle, options);
            } catch (BundleException ex) {
                LOGGER.debugf(ex, "Cannot uninstall bundle: %s", bundle);
            }
        } else if (bundleRevision.getState() != State.UNINSTALLED) {
            int options = InternalConstants.UNINSTALL_INTERNAL;
            getBundleManager().removeRevision(bundleRevision, options);
        }
    }

    private boolean uninstallRequired(XBundle bundle) {

        // No uninstall if the bundle is already uninstalled
        if (bundle.getState() == Bundle.UNINSTALLED || bundleRevision.getState() == State.UNINSTALLED)
            return false;

        // No uninstall if this is not the current revision
        if (bundle.getBundleRevision() != bundleRevision)
            return false;

        // No uninstall if the revision service goes down because of a bundle refresh
        Method method = bundle.getAttachment(InternalConstants.LOCK_METHOD_KEY);
        if (method == Method.REFRESH)
            return false;

        return true;
    }

    private int getUninstallOptions(boolean serverShutdown) {
        return InternalConstants.UNINSTALL_INTERNAL + (serverShutdown ? Bundle.STOP_TRANSIENT : 0);
    }

    private boolean getServerShutdown() {
        BundleManagerPlugin bundleManager = getBundleManager();
        int managerState = bundleManager.getManagerState();
        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        Substate managerServiceState = serviceContainer.getRequiredService(Services.BUNDLE_MANAGER).getSubstate();
        boolean stopping = managerState == Bundle.STOPPING || managerState == Bundle.RESOLVED || managerServiceState == Substate.STOP_REQUESTED;
        return stopping;
    }

    abstract R createBundleRevision(Deployment deployment, StorageState storageState, ServiceName serviceName, ServiceTarget serviceTarget) throws BundleException;

    UserBundleState createBundleState(UserBundleRevision revision) {
        return new UserBundleState(getFrameworkState(), revision);
    }

    private StorageState createStorageState(Deployment dep, RevisionIdentifier revIdentifier) throws BundleException {
        // The storage state exists when we re-create the bundle from persistent storage
        StorageState storageState = dep.getAttachment(STORAGE_STATE_KEY);
        if (storageState == null) {
            String location = dep.getLocation();
            VirtualFile rootFile = dep.getRoot();
            try {
                Integer startlevel = dep.getStartLevel();
                StorageManager storageManager = getFrameworkState().getStorageManager();
                storageState = storageManager.createStorageState(revIdentifier.getRevisionId(), location, startlevel, rootFile);
                dep.putAttachment(STORAGE_STATE_KEY, storageState);
            } catch (IOException ex) {
                throw MESSAGES.cannotSetupStorage(ex, rootFile);
            }
        }
        return storageState;
    }

    private void installBundleRevision(R brev) throws BundleException {
        XEnvironment env = getFrameworkState().getEnvironment();
        env.installResources(new XResource[] { brev });
    }

    private void validateBundleRevision(R bundleRevision, OSGiMetaData metadata) throws BundleException {
        if (metadata.getBundleManifestVersion() > 1) {
            new BundleRevisionValidatorR4().validateBundleRevision(bundleRevision, metadata);
        } else {
            new BundleRevisionValidatorR3().validateBundleRevision(bundleRevision, metadata);
        }
    }

    // Process the Bundle-NativeCode header if there is one
    private void processNativeCode(R bundleRevision, OSGiMetaData metadata, Deployment dep) {
        if (metadata.getBundleNativeCode() != null) {
            NativeCode nativeCodePlugin = getFrameworkState().getNativeCode();
            nativeCodePlugin.deployNativeCode(dep);
        }
    }

    @Override
    R getBundleRevision() {
        return bundleRevision;
    }
}
