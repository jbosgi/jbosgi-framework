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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.BundleStorage;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.NativeCode;
import org.jboss.osgi.framework.spi.ServiceTracker.SynchronousListenerServiceWrapper;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;

/**
 * Represents the INSTALLED state of a user bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class UserBundleInstalledService<B extends UserBundleState, R extends UserBundleRevision> extends AbstractBundleService<B> {

    private final Deployment initialDeployment;
    private B bundleState;

    UserBundleInstalledService(FrameworkState frameworkState, Deployment deployment) {
        super(frameworkState);
        this.initialDeployment = deployment;
    }

    ServiceName install(ServiceTarget serviceTarget, ServiceListener<XBundle> listener) {
        ServiceName serviceName = getBundleManager().getServiceName(initialDeployment, Bundle.INSTALLED);
        LOGGER.debugf("Installing %s %s", getClass().getSimpleName(), serviceName);
        ServiceBuilder<B> builder = serviceTarget.addService(serviceName, new SynchronousListenerServiceWrapper<B>(this));
        addServiceDependencies(builder);
        if (listener != null) {
            builder.addListener(listener);
        }
        return builder.install().getName();
    }

    protected void addServiceDependencies(ServiceBuilder<B> builder) {
        builder.addDependency(IntegrationServices.FRAMEWORK_CORE_SERVICES);
    }

    @Override
    public void start(StartContext context) throws StartException {
        StorageState storageState = null;
        try {
            Deployment dep = initialDeployment;
            Long bundleId = dep.getAttachment(Long.class);
            storageState = createStorageState(dep, bundleId);
            OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
            R brev = createBundleRevision(dep, metadata, storageState);
            brev.addAttachment(Long.class, bundleId);
            ServiceName serviceName = context.getController().getName().getParent();
            bundleState = createBundleState(brev, serviceName, context.getChildTarget());
            dep.addAttachment(Bundle.class, bundleState);
            bundleState.initLazyActivation();
            validateBundle(bundleState, metadata);
            processNativeCode(bundleState, dep);
            installBundle(bundleState);
            bundleState.fireBundleEvent(BundleEvent.INSTALLED);
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
        if (getBundleState().getState() != Bundle.UNINSTALLED) {
            try {
                getBundleManager().uninstallBundle(getBundleState(), Bundle.STOP_TRANSIENT);
            } catch (BundleException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    abstract R createBundleRevision(Deployment deployment, OSGiMetaData metadata, StorageState storageState) throws BundleException;

    abstract B createBundleState(R revision, ServiceName serviceName, ServiceTarget serviceTarget) throws BundleException;

    StorageState createStorageState(Deployment dep, Long bundleId) throws BundleException {
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
                storageState = storagePlugin.createStorageState(bundleId, location, startlevel, rootFile);
                dep.addAttachment(StorageState.class, storageState);
            } catch (IOException ex) {
                throw MESSAGES.cannotSetupStorage(ex, rootFile);
            }
        }
        return storageState;
    }

    @Override
    B getBundleState() {
        return bundleState;
    }

    private void installBundle(UserBundleState userBundle) throws BundleException {

        // [TODO] Add singleton support this to XBundle
        // it is not ok to simple not add it to the Environment
        boolean addToEnvironment = true;
        if (userBundle.isSingleton()) {
            String symbolicName = userBundle.getSymbolicName();
            for (XBundle aux : getBundleManager().getBundles(symbolicName, null)) {
                if (aux != userBundle && isSingleton(aux)) {
                    LOGGER.infoNoResolvableSingleton(userBundle);
                    addToEnvironment = false;
                    break;
                }
            }
        }

        if (addToEnvironment) {
            XEnvironment env = getFrameworkState().getEnvironment();
            env.installResources(userBundle.getBundleRevision());
        }

        userBundle.changeState(Bundle.INSTALLED, 0);
        LOGGER.infoBundleInstalled(userBundle);
    }

    // [TODO] Add singleton support this to XBundle
    private boolean isSingleton(XBundle userBundle) {
        if (userBundle instanceof UserBundleState) {
            UserBundleState bundleState = (UserBundleState) userBundle;
            return bundleState.isSingleton();
        }
        return false;
    }

    private void validateBundle(UserBundleState userBundle, OSGiMetaData metadata) throws BundleException {
        if (metadata.getBundleManifestVersion() > 1) {
            new BundleValidatorR4().validateBundle(userBundle, metadata);
        } else {
            new BundleValidatorR3().validateBundle(userBundle, metadata);
        }
    }

    // Process the Bundle-NativeCode header if there is one
    private void processNativeCode(UserBundleState userBundle, Deployment dep) {
        OSGiMetaData metadata = userBundle.getOSGiMetaData();
        if (metadata.getBundleNativeCode() != null) {
            FrameworkState frameworkState = userBundle.getFrameworkState();
            NativeCode nativeCodePlugin = frameworkState.getNativeCode();
            nativeCodePlugin.deployNativeCode(dep);
        }
    }
}
