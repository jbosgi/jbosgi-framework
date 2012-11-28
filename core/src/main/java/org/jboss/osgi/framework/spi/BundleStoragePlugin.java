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
package org.jboss.osgi.framework.spi;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.BundleStorageImpl;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * A simple implementation of a BundleStorage
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class BundleStoragePlugin extends AbstractIntegrationService<BundleStorage> implements BundleStorage {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final boolean firstInit;
    private BundleStorage bundleStorage;

    public BundleStoragePlugin(boolean firstInit) {
        super(IntegrationServices.BUNDLE_STORAGE);
        this.firstInit = firstInit;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleStorage> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        bundleStorage = new BundleStorageImpl(bundleManager);
        try {
            initialize(bundleManager.getProperties(), firstInit);
        } catch (IOException ex) {
            throw new StartException(ex);
        }
    }

    @Override
    public BundleStorage getValue() {
        return this;
    }

    @Override
    public void initialize(Map<String, Object> props, boolean firstInit) throws IOException {
        bundleStorage.initialize(props, firstInit);
    }

    @Override
    public StorageState createStorageState(long bundleId, String location, int startlevel, VirtualFile rootFile) throws IOException {
        return bundleStorage.createStorageState(bundleId, location, startlevel, rootFile);
    }

    @Override
    public void deleteStorageState(StorageState storageState) {
        bundleStorage.deleteStorageState(storageState);
    }

    @Override
    public Set<StorageState> getStorageStates() {
        return bundleStorage.getStorageStates();
    }

    @Override
    public StorageState getStorageState(String location) {
        return bundleStorage.getStorageState(location);
    }

    @Override
    public File getStorageDir(long bundleId) {
        return bundleStorage.getStorageDir(bundleId);
    }

    @Override
    public File getStorageArea() {
        return bundleStorage.getStorageArea();
    }

    @Override
    public File getDataFile(long bundleId, String filename) {
        return bundleStorage.getDataFile(bundleId, filename);
    }
}