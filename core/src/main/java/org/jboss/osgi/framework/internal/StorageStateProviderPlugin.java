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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.framework.StorageStateProvider;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.osgi.framework.BundleException;

/**
 * An implementation of a {@link StorageStateProvider}
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Apr-2012
 */
final class StorageStateProviderPlugin extends AbstractPluginService<StorageStateProvider> implements StorageStateProvider {

    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();

    static void addService(ServiceTarget serviceTarget) {
        StorageStateProviderPlugin service = new StorageStateProviderPlugin();
        ServiceBuilder<StorageStateProvider> builder = serviceTarget.addService(Services.STORAGE_STATE_PROVIDER, service);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, service.injectedDeploymentFactory);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private StorageStateProviderPlugin() {
    }

    @Override
    public StorageStateProvider getValue() {
        return this;
    }

    @Override
    public Set<StorageState> getStorageStates() {
        BundleStoragePlugin bundleStorage = injectedBundleStorage.getValue();
        Set<InternalStorageState> storageStates = bundleStorage.getBundleStorageStates();
        return Collections.unmodifiableSet(new HashSet<StorageState>(storageStates));
    }

    @Override
    public StorageState getByLocation(String location) {
        BundleStoragePlugin bundleStorage = injectedBundleStorage.getValue();
        return bundleStorage.getStorageState(location);
    }

    @Override
    public Deployment createDeployment(StorageState storageState) throws BundleException {
        return injectedDeploymentFactory.getValue().createDeployment(storageState);
    }
}