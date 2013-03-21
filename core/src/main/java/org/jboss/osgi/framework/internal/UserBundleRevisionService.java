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
import static org.jboss.osgi.framework.spi.IntegrationServices.BUNDLE_BASE_NAME;

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
import org.jboss.osgi.framework.spi.BundleStorage;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.NativeCode;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
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

        int revindex = 0;
        if (deployment.isBundleUpdate()) {
            UserBundleState userBundle = UserBundleState.assertBundleState(deployment.getAttachment(Bundle.class));
            revindex = userBundle.getRevisionIndex();
        }
        RevisionIdentifier revIdentifier = deployment.getAttachment(RevisionIdentifier.class);
        Long bundleId = revIdentifier.getBundleIndex();
        String bsname = deployment.getSymbolicName();
        String version = deployment.getVersion();
        String revisionid = "bid" + bundleId + "rev" + revindex;
        serviceName = ServiceName.of(BUNDLE_BASE_NAME, "" + bsname, "" + version, revisionid);
    }

    ServiceController<R> install(ServiceTarget serviceTarget, ServiceListener<XBundleRevision> listener) {
        LOGGER.debugf("Installing %s %s", getClass().getSimpleName(), serviceName);
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
        StorageState storageState = null;
        try {
            Deployment dep = deployment;
            RevisionIdentifier revIdentifier = dep.getAttachment(RevisionIdentifier.class);
            storageState = createStorageState(dep, revIdentifier);
            OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
            ServiceName serviceName = startContext.getController().getName();
            bundleRevision = createBundleRevision(dep, metadata, storageState, serviceName, startContext.getChildTarget());
            bundleRevision.addAttachment(Long.class, revIdentifier.getRevisionIndex());
            bundleRevision.addAttachment(ServiceName.class, serviceName);
            validateBundleRevision(bundleRevision, metadata);
            processNativeCode(bundleRevision, metadata, dep);
            XBundle bundle = (XBundle) dep.getAttachment(Bundle.class);
            if (bundle == null) {
                bundle = createBundleState(bundleRevision);
                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                dep.addAttachment(Bundle.class, userBundle);
                userBundle.initLazyActivation();
                userBundle.changeState(Bundle.INSTALLED, 0);
                LOGGER.infoBundleInstalled(userBundle);
                // For the event type INSTALLED, this is the bundle whose context was used to install the bundle
                XBundle origin = (XBundle) targetContext.getBundle();
                FrameworkEvents events = getFrameworkState().getFrameworkEvents();
                events.fireBundleEvent(origin, bundle, BundleEvent.INSTALLED);
            } else {
                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                userBundle.addBundleRevision(bundleRevision);
            }
            bundleRevision.addAttachment(Bundle.class, bundle);
            installBundleRevision(bundleRevision);
        } catch (BundleException ex) {
            if (storageState != null) {
                BundleStorage storagePlugin = getFrameworkState().getBundleStorage();
                storagePlugin.deleteStorageState(storageState);
            }
            throw new StartException(ex);
        }
    }

    @Override
    public void stop(StopContext context) {
        XBundle bundle = bundleRevision.getBundle();
        if (bundle.getBundleRevision() == bundleRevision && bundle.getState() != Bundle.UNINSTALLED) {
            try {
                BundleManagerPlugin bundleManager = getBundleManager();
                ServiceContainer serviceContainer = bundleManager.getServiceContainer();
                ServiceController<?> controller = serviceContainer.getRequiredService(Services.BUNDLE_MANAGER);
                int managerState = bundleManager.getManagerState();
                Substate substate = controller.getSubstate();
                boolean stopping = managerState == Bundle.STOPPING || managerState == Bundle.RESOLVED || substate == Substate.STOP_REQUESTED;
                bundleManager.uninstallBundle(bundle, stopping ? Bundle.STOP_TRANSIENT : 0);
            } catch (BundleException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    abstract R createBundleRevision(Deployment deployment, OSGiMetaData metadata, StorageState storageState, ServiceName serviceName, ServiceTarget serviceTarget) throws BundleException;

    UserBundleState createBundleState(UserBundleRevision revision) {
        return new UserBundleState(getFrameworkState(), revision);
    }

    private StorageState createStorageState(Deployment dep, RevisionIdentifier revIdentifier) throws BundleException {
        // The storage state exists when we re-create the bundle from persistent storage
        StorageState storageState = dep.getAttachment(StorageState.class);
        if (storageState == null) {
            String location = dep.getLocation();
            VirtualFile rootFile = dep.getRoot();
            try {
                BundleStorage storagePlugin = getFrameworkState().getBundleStorage();
                Integer startlevel = dep.getStartLevel();
                if (startlevel == null) {
                    startlevel = getFrameworkState().getStartLevelSupport().getInitialBundleStartLevel();
                }
                storageState = storagePlugin.createStorageState(revIdentifier.getBundleIndex(), location, startlevel, rootFile);
                dep.addAttachment(StorageState.class, storageState);
            } catch (IOException ex) {
                throw MESSAGES.cannotSetupStorage(ex, rootFile);
            }
        }
        return storageState;
    }

    private void installBundleRevision(R bundleRevision) throws BundleException {
        XEnvironment env = getFrameworkState().getEnvironment();
        env.installResources(bundleRevision);
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
