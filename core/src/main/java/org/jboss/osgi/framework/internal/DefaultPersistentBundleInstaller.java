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

import static org.jboss.osgi.framework.IntegrationServices.PERSISTENT_BUNDLE_INSTALLER;
import static org.jboss.osgi.framework.IntegrationServices.PERSISTENT_BUNDLE_INSTALLER_COMPLETE;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.PersistentBundleInstaller;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.framework.StorageStateProvider;
import org.osgi.framework.BundleException;

/**
 * A service that provides persistent bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class DefaultPersistentBundleInstaller extends AbstractPluginService<PersistentBundleInstaller> implements PersistentBundleInstaller {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<StorageStateProvider> injectedStorageProvider = new InjectedValue<StorageStateProvider>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();
    private final Map<ServiceName, Deployment> installedServices = new HashMap<ServiceName, Deployment>();

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(PERSISTENT_BUNDLE_INSTALLER) == null) {
            DefaultPersistentBundleInstaller service = new DefaultPersistentBundleInstaller();
            ServiceBuilder<PersistentBundleInstaller> builder = serviceTarget.addService(PERSISTENT_BUNDLE_INSTALLER, service);
            builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
            builder.addDependency(Services.STORAGE_STATE_PROVIDER, StorageStateProvider.class, service.injectedStorageProvider);
            builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
            builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, service.injectedDeploymentFactory);
            builder.addDependency(Services.FRAMEWORK_CORE_SERVICES);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultPersistentBundleInstaller() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        ServiceTarget serviceTarget = context.getChildTarget();
        BundleManager bundleManager = injectedBundleManager.getValue();
        DeploymentFactoryPlugin deploymentPlugin = injectedDeploymentFactory.getValue();

        // Install the persisted bundles
        StorageStateProvider storageStateProvider = injectedStorageProvider.getValue();
        List<StorageState> storageStates = storageStateProvider.getStorageStates();
        for (StorageState storageState : storageStates) {
            long bundleId = storageState.getBundleId();
            if (bundleId != 0) {
                try {
                    Deployment dep = deploymentPlugin.createDeployment(storageState);
                    ServiceName serviceName = bundleManager.installBundle(serviceTarget, dep);
                    installedServices.put(serviceName, dep);
                } catch (BundleException ex) {
                    throw new StartException(ex);
                }
            }
        }

        // Install a service that has a dependency on all persistent bundle INSTALLED services
        ServiceBuilder<Void> builder = serviceTarget.addService(PERSISTENT_BUNDLE_INSTALLER_COMPLETE, new AbstractService<Void>() {
            public void start(StartContext context) throws StartException {
                LOGGER.debugf("Persistent bundles installed");
            }
        });
        builder.addDependencies(installedServices.keySet());
        builder.install();
    }

    @Override
    public PersistentBundleInstaller getValue() throws IllegalStateException {
        return this;
    }

    @Override
    public Map<ServiceName, Deployment> getInstalledServices() {
        return Collections.unmodifiableMap(installedServices);
    }
}