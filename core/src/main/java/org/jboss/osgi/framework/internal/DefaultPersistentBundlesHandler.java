/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import static org.jboss.osgi.framework.IntegrationServices.PERSISTENT_BUNDLES_HANDLER;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.PersistentBundlesComplete;
import org.jboss.osgi.framework.PersistentBundlesHandler;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.framework.StorageStateProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * A service that provides persistent bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class DefaultPersistentBundlesHandler extends AbstractPluginService<PersistentBundlesHandler> implements PersistentBundlesHandler {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<StorageStateProvider> injectedStorageProvider = new InjectedValue<StorageStateProvider>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();
    private ServiceTarget serviceTarget;

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(PERSISTENT_BUNDLES_HANDLER) == null) {
            DefaultPersistentBundlesHandler service = new DefaultPersistentBundlesHandler();
            ServiceBuilder<PersistentBundlesHandler> builder = serviceTarget.addService(PERSISTENT_BUNDLES_HANDLER, service);
            builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
            builder.addDependency(Services.STORAGE_STATE_PROVIDER, StorageStateProvider.class, service.injectedStorageProvider);
            builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
            builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, service.injectedDeploymentFactory);
            builder.addDependencies(Services.FRAMEWORK_CREATE, IntegrationServices.AUTOINSTALL_COMPLETE);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultPersistentBundlesHandler() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        serviceTarget = context.getChildTarget();

        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        DeploymentFactoryPlugin deploymentPlugin = injectedDeploymentFactory.getValue();

        final StorageStateProvider storageStateProvider = injectedStorageProvider.getValue();
        final Set<StorageState> storageStates = new HashSet<StorageState>(storageStateProvider.getStorageStates());

        // Reduce the set by the bundles that are already installed
        Iterator<StorageState> iterator = storageStates.iterator();
        while (iterator.hasNext()) {
            StorageState storageState = iterator.next();
            if (bundleManager.getBundleById(storageState.getBundleId()) != null) {
                iterator.remove();
            }
        }

        // Create the COMPLETE service that listens on the bundle INSTALL services
        PersistentBundlesComplete installComplete = new PersistentBundlesComplete() {
            @Override
            protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
                return storageStates.size() == trackedServices.size();
            }
        };

        LOGGER.debugf("Installing persistent bundle states: %s", storageStates);
        ServiceBuilder<Void> builder = installComplete.install(context.getChildTarget());
        if (storageStates.size() == 0) {
            builder.install();
        } else {
            // Install the persisted bundles
            ServiceListener<Bundle> listener = installComplete.getListener();
            for (StorageState storageState : storageStates) {
                try {
                    Deployment dep = deploymentPlugin.createDeployment(storageState);
                    bundleManager.installBundle(serviceTarget, dep, listener);
                } catch (BundleException ex) {
                    LOGGER.errorStateCannotInstallInitialBundle(ex, storageState.getLocation());
                }
            }
        }
    }

    @Override
    public PersistentBundlesHandler getValue() throws IllegalStateException {
        return this;
    }
}