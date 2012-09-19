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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BootstrapBundlesInstall;
import org.jboss.osgi.framework.IntegrationService;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.framework.StorageStatePlugin;
import org.osgi.framework.BundleException;

/**
 * A service that provides persistent bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class DefaultPersistentBundlesInstall extends BootstrapBundlesInstall<Void> implements IntegrationService<Void> {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<StorageStatePlugin> injectedStoragePlugin = new InjectedValue<StorageStatePlugin>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();

    DefaultPersistentBundlesInstall() {
        super(IntegrationService.PERSISTENT_BUNDLES);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<Void> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, injectedBundleManager);
        builder.addDependency(IntegrationService.STORAGE_STATE_PLUGIN, StorageStatePlugin.class, injectedStoragePlugin);
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, injectedDeploymentFactory);
        builder.addDependencies(IntegrationService.BOOTSTRAP_BUNDLES_COMPLETE);
    }

    @Override
    public void start(StartContext context) throws StartException {

        final BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        final DeploymentFactoryPlugin deploymentPlugin = injectedDeploymentFactory.getValue();
        final ServiceTarget serviceTarget = context.getChildTarget();

        final StorageStatePlugin storageStatePlugin = injectedStoragePlugin.getValue();
        final Set<StorageState> storageStates = new HashSet<StorageState>(storageStatePlugin.getStorageStates());

        // Reduce the set by the bundles that are already installed
        Iterator<StorageState> iterator = storageStates.iterator();
        while (iterator.hasNext()) {
            StorageState storageState = iterator.next();
            if (bundleManager.getBundleById(storageState.getBundleId()) != null) {
                iterator.remove();
            }
        }

        List<Deployment> deployments = new ArrayList<Deployment>();
        for (StorageState storageState : storageStates) {
            try {
                Deployment dep = deploymentPlugin.createDeployment(storageState);
                deployments.add(dep);
            } catch (BundleException ex) {
                LOGGER.errorStateCannotInstallInitialBundle(ex, storageState.getLocation());
            }
        }

        // Install the bundles from the given locations
        installBootstrapBundles(serviceTarget, deployments);
    }
}