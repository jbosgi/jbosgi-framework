/*
 * #%L
 * JBossOSGi Framework Core
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

import static org.jboss.osgi.framework.IntegrationServices.PERSISTENT_BUNDLES_PLUGIN;
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
import org.jboss.osgi.framework.PersistentBundlesPlugin;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.framework.StorageStatePlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * A service that provides persistent bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class DefaultPersistentBundlesPlugin extends AbstractPluginService<PersistentBundlesPlugin> implements PersistentBundlesPlugin {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<StorageStatePlugin> injectedStorageState = new InjectedValue<StorageStatePlugin>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(PERSISTENT_BUNDLES_PLUGIN) == null) {
            DefaultPersistentBundlesPlugin service = new DefaultPersistentBundlesPlugin();
            ServiceBuilder<PersistentBundlesPlugin> builder = serviceTarget.addService(PERSISTENT_BUNDLES_PLUGIN, service);
            builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
            builder.addDependency(Services.STORAGE_STATE_PLUGIN, StorageStatePlugin.class, service.injectedStorageState);
            builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
            builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, service.injectedDeploymentFactory);
            builder.addDependencies(Services.FRAMEWORK_CREATE, IntegrationServices.AUTOINSTALL_COMPLETE);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultPersistentBundlesPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);

        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        DeploymentFactoryPlugin deploymentPlugin = injectedDeploymentFactory.getValue();

        final StorageStatePlugin storageStatePlugin = injectedStorageState.getValue();
        final Set<StorageState> storageStates = new HashSet<StorageState>(storageStatePlugin.getStorageStates());

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
                    bundleManager.installBundle(dep, listener);
                } catch (BundleException ex) {
                    LOGGER.errorStateCannotInstallInitialBundle(ex, storageState.getLocation());
                }
            }
        }
    }

    @Override
    public PersistentBundlesPlugin getValue() throws IllegalStateException {
        return this;
    }
}