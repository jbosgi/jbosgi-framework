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

import static org.jboss.osgi.framework.IntegrationServices.BOOTSTRAP_BUNDLES_COMPLETE;
import static org.jboss.osgi.framework.IntegrationServices.BOOTSTRAP_BUNDLES_INSTALL;
import static org.jboss.osgi.framework.IntegrationServices.PERSISTENT_BUNDLES;
import static org.jboss.osgi.framework.IntegrationServices.PERSISTENT_BUNDLES_INSTALL;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BootstrapBundlesInstall;
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
class DefaultPersistentBundlesInstall extends BootstrapBundlesInstall<Void> {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<StorageStatePlugin> injectedStoragePlugin = new InjectedValue<StorageStatePlugin>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(PERSISTENT_BUNDLES_INSTALL) == null) {
            new DefaultPersistentBundlesInstall(PERSISTENT_BUNDLES).install(serviceTarget);
        }
    }

    private DefaultPersistentBundlesInstall(ServiceName baseName) {
        super(baseName);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<Void> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, injectedBundleManager);
        builder.addDependency(Services.STORAGE_STATE_PLUGIN, StorageStatePlugin.class, injectedStoragePlugin);
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, injectedDeploymentFactory);
        builder.addDependencies(BOOTSTRAP_BUNDLES_INSTALL, BOOTSTRAP_BUNDLES_COMPLETE);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);

        final BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        final DeploymentFactoryPlugin deploymentPlugin = injectedDeploymentFactory.getValue();

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
        installBootstrapBundles(context.getChildTarget(), deployments);
    }
}