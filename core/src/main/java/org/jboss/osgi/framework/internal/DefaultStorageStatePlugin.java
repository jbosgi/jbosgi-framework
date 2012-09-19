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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.IntegrationService;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.framework.StorageStatePlugin;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.osgi.framework.BundleException;

/**
 * An implementation of a {@link StorageStatePlugin}
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Apr-2012
 */
final class DefaultStorageStatePlugin extends AbstractService<StorageStatePlugin> implements StorageStatePlugin {

    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();

    static void addService(ServiceTarget serviceTarget) {
        DefaultStorageStatePlugin service = new DefaultStorageStatePlugin();
        ServiceBuilder<StorageStatePlugin> builder = serviceTarget.addService(IntegrationService.STORAGE_STATE_PLUGIN, service);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, service.injectedDeploymentFactory);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultStorageStatePlugin() {
    }

    @Override
    public StorageStatePlugin getValue() {
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