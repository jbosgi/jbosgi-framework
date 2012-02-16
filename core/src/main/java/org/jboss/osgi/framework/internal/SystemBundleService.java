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

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.EnvironmentPlugin;
import org.jboss.osgi.framework.FrameworkModuleProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemPathsProvider;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XModule;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Represents the ACTIVE state of the system bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class SystemBundleService extends AbstractBundleService<SystemBundleState> {

    // Provide logging
    static final Logger log = Logger.getLogger(SystemBundleService.class);

    private final InjectedValue<SystemPathsProvider> injectedSystemPaths = new InjectedValue<SystemPathsProvider>();
    private final InjectedValue<FrameworkModuleProvider> injectedModuleProvider = new InjectedValue<FrameworkModuleProvider>();
    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<LegacyResolverPlugin> injectedLegacyResolver = new InjectedValue<LegacyResolverPlugin>();
    private final InjectedValue<EnvironmentPlugin> injectedEnvironmentPlugin = new InjectedValue<EnvironmentPlugin>();
    private SystemBundleState bundleState;

    static void addService(ServiceTarget serviceTarget, FrameworkState frameworkState) {
        SystemBundleService service = new SystemBundleService(frameworkState);
        ServiceBuilder<SystemBundleState> builder = serviceTarget.addService(Services.SYSTEM_BUNDLE, service);
        builder.addDependency(Services.ENVIRONMENT_PLUGIN, EnvironmentPlugin.class, service.injectedEnvironmentPlugin);
        builder.addDependency(Services.FRAMEWORK_MODULE_PROVIDER, FrameworkModuleProvider.class, service.injectedModuleProvider);
        builder.addDependency(Services.SYSTEM_PATHS_PROVIDER, SystemPathsProvider.class, service.injectedSystemPaths);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
        builder.addDependency(InternalServices.LEGACY_RESOLVER_PLUGIN, LegacyResolverPlugin.class, service.injectedLegacyResolver);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private SystemBundleService(FrameworkState frameworkState) {
        super(frameworkState);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        try {
            bundleState = createBundleState();
            bundleState.changeState(Bundle.STARTING);
            OSGiMetaData metadata = createOSGiMetaData();
            XModule resModule = bundleState.createResolverModule(metadata);
            SystemBundleRevision sysrev = bundleState.createBundleRevision(metadata, resModule);
            injectedEnvironmentPlugin.getValue().installResources(sysrev);
            bundleState.createBundleContext();
            bundleState.createStorageState(injectedBundleStorage.getValue());
            injectedLegacyResolver.getValue().addModule(resModule);
            BundleManager bundleManager = getBundleManager();
            bundleManager.injectedSystemBundle.inject(bundleState);
            bundleManager.addBundle(bundleState);
        } catch (BundleException ex) {
            throw new StartException(ex);
        }
    }

    SystemBundleState createBundleState() {
        return new SystemBundleState(getFrameworkState(), injectedModuleProvider.getValue());
    }

    @Override
    SystemBundleState getBundleState() {
        return bundleState;
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        BundleManager bundleManager = getBundleManager();
        bundleManager.injectedSystemBundle.uninject();
    }

    private OSGiMetaData createOSGiMetaData() {

        // Initialize the OSGiMetaData
        SystemBundleState bundleState = getBundleState();
        OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder(bundleState.getSymbolicName(), bundleState.getVersion());
        SystemPathsProvider systemPackages = injectedSystemPaths.getValue();

        List<String> exportedPackages = new ArrayList<String>();
        exportedPackages.addAll(systemPackages.getSystemPackages());

        if (exportedPackages.isEmpty() == true)
            throw new IllegalStateException("Framework system packages not available");

        // Construct framework capabilities from system packages
        for (String packageSpec : exportedPackages) {
            String packname = packageSpec;
            Version version = Version.emptyVersion;

            int versionIndex = packname.indexOf(";version=");
            if (versionIndex > 0) {
                packname = packageSpec.substring(0, versionIndex);
                version = Version.parseVersion(packageSpec.substring(versionIndex + 9));
            }

            builder.addExportPackages(packname + ";version=" + version);
        }
        return builder.getOSGiMetaData();
    }
}