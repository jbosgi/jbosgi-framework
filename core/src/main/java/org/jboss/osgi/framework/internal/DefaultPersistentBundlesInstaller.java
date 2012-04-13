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

import static org.jboss.osgi.framework.IntegrationServices.PERSISTENT_BUNDLES_INSTALLER_COMPLETE;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.PersistentBundlesInstaller;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.BundleException;

/**
 * A service that provides persistent bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class DefaultPersistentBundlesInstaller extends AbstractPluginService<PersistentBundlesInstaller> implements PersistentBundlesInstaller {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<BundleStorageProvider> injectedBundleStorage = new InjectedValue<BundleStorageProvider>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();
    private final Map<ServiceName, Deployment> installedServices = new HashMap<ServiceName, Deployment>();

    static void addService(ServiceTarget serviceTarget) {
        DefaultPersistentBundlesInstaller service = new DefaultPersistentBundlesInstaller();
        ServiceBuilder<PersistentBundlesInstaller> builder = serviceTarget.addService(IntegrationServices.PERSISTENT_BUNDLES_INSTALLER, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStorageProvider.class, service.injectedBundleStorage);
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, service.injectedDeploymentFactory);
        builder.addDependency(InternalServices.CORE_SERVICES);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultPersistentBundlesInstaller() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        try {
            ServiceTarget serviceTarget = context.getChildTarget();
            BundleManager bundleManager = injectedBundleManager.getValue();
            BundleStorageProvider storagePlugin = injectedBundleStorage.getValue();
            DeploymentFactoryPlugin deploymentPlugin = injectedDeploymentFactory.getValue();

            // Install the persisted bundles
            try {
                List<BundleStorageState> storageStates = storagePlugin.getBundleStorageStates();
                for (BundleStorageState storageState : storageStates) {
                    long bundleId = storageState.getBundleId();
                    if (bundleId != 0) {
                        try {
                            Deployment dep = deploymentPlugin.createDeployment(storageState);
                            ServiceName serviceName = bundleManager.installBundle(serviceTarget, dep);
                            installedServices.put(serviceName, dep);
                        } catch (BundleException ex) {
                            LOGGER.errorCannotInstallPersistentBundlle(ex, storageState);
                        }
                    }
                }
            } catch (IOException ex) {
                throw MESSAGES.bundleCannotInstallPersistedBundles(ex);
            }

            // Install a service that has a dependency on all pending bundle INSTALLED services
            final PersistentBundlesInstaller persistentBundlesInstaller = this;
            ServiceBuilder<PersistentBundlesInstaller> builder = serviceTarget.addService(PERSISTENT_BUNDLES_INSTALLER_COMPLETE, new AbstractService<PersistentBundlesInstaller>() {
                public void start(StartContext context) throws StartException {
                    LOGGER.debugf("Persistent bundles installed");
                }
                public PersistentBundlesInstaller getValue() throws IllegalStateException {
                    return persistentBundlesInstaller;
                }
            });
            builder.addDependencies(installedServices.keySet());
            builder.install();
        } catch (BundleException ex) {
            throw new StartException(ex);
        }
    }

    @Override
    public PersistentBundlesInstaller getValue() throws IllegalStateException {
        return this;
    }

    public Map<ServiceName, Deployment> getInstalledServices() {
        return Collections.unmodifiableMap(installedServices);
    }
}