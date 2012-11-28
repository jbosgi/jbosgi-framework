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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_LOCATION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.BundleStorage;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.framework.spi.SystemPaths;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * A plugin for the system bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class SystemBundlePlugin extends AbstractIntegrationService<XBundle> {

    private final FrameworkState frameworkState;
    private SystemBundleState bundleState;

    SystemBundlePlugin(FrameworkState frameworkState) {
        super(IntegrationServices.SYSTEM_BUNDLE_INTERNAL);
        this.frameworkState = frameworkState;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<XBundle> builder) {
        builder.addDependency(IntegrationServices.ENVIRONMENT, XEnvironment.class, frameworkState.injectedEnvironment);
        builder.addDependency(IntegrationServices.BUNDLE_STORAGE, BundleStorage.class, frameworkState.injectedBundleStorage);
        builder.addDependency(IntegrationServices.LOCK_MANAGER, LockManager.class, frameworkState.injectedLockManager);
        builder.addDependency(IntegrationServices.MODULE_MANGER, ModuleManager.class, frameworkState.injectedModuleManager);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS, SystemPaths.class, frameworkState.injectedSystemPaths);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        StorageState storageState = null;
        BundleManagerImpl bundleManager = frameworkState.getBundleManager();
        try {
            OSGiMetaData metadata = createMetaData();
            storageState = createStorageState();
            SystemBundleRevision brev = new SystemBundleRevision(frameworkState, metadata, storageState);
            bundleState = new SystemBundleState(frameworkState, brev);
            bundleState.changeState(Bundle.STARTING);
            addToEnvironment(brev);
            bundleState.createBundleContext();
            bundleManager.injectedSystemBundle.inject(bundleState);
        } catch (BundleException ex) {
            if (storageState != null) {
                BundleStorage storagePlugin = frameworkState.getBundleStorage();
                storagePlugin.deleteStorageState(storageState);
            }
            throw new StartException(ex);
        }
    }

    @Override
    public void stop(StopContext context) {
        BundleManagerImpl bundleManager = frameworkState.getBundleManager();
        bundleManager.injectedSystemBundle.uninject();
    }

    @Override
    public XBundle getValue() throws IllegalStateException {
        return bundleState;
    }

    private StorageState createStorageState() {
        try {
            BundleStorage storagePlugin = frameworkState.getBundleStorage();
            return storagePlugin.createStorageState(0, SYSTEM_BUNDLE_LOCATION, 0, null);
        } catch (IOException ex) {
            throw MESSAGES.illegalStateCannotCreateSystemBundleStorage(ex);
        }
    }

    private OSGiMetaData createMetaData() {

        // Initialize the OSGiMetaData
        OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder(Constants.SYSTEM_BUNDLE_SYMBOLICNAME, Version.emptyVersion);
        SystemPaths systemPackages = frameworkState.getSystemPathsPlugin();

        List<String> exportedPackages = new ArrayList<String>();
        exportedPackages.addAll(systemPackages.getSystemPackages());

        // Construct framework capabilities from system packages
        for (String packageSpec : exportedPackages) {
            builder.addExportPackages(packageSpec);
        }
        return builder.getOSGiMetaData();
    }

    private void addToEnvironment(SystemBundleRevision sysrev) {
        XEnvironment env = frameworkState.getEnvironment();
        env.installResources(sysrev);
    }
}
