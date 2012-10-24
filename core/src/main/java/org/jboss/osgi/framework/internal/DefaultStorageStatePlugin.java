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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.framework.spi.StorageStatePlugin;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * An implementation of a {@link StorageStatePlugin}
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Apr-2012
 */
final class DefaultStorageStatePlugin extends AbstractIntegrationService<StorageStatePlugin> implements StorageStatePlugin {

    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();

    DefaultStorageStatePlugin() {
        super(IntegrationServices.STORAGE_STATE_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<StorageStatePlugin> builder) {
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, injectedBundleStorage);
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, injectedDeploymentFactory);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.setInitialMode(Mode.ON_DEMAND);
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
    public StorageState getStorageState(String location) {
        BundleStoragePlugin bundleStorage = injectedBundleStorage.getValue();
        return bundleStorage.getStorageState(location);
    }

    @Override
    public StorageState createStorageState(String location, int startlevel, VirtualFile rootFile) throws IOException {
        BundleStoragePlugin bundleStorage = injectedBundleStorage.getValue();
        Long bundleId = injectedEnvironment.getValue().nextResourceIdentifier(null, location);
        return bundleStorage.createStorageState(bundleId, location, startlevel, rootFile);
    }
}