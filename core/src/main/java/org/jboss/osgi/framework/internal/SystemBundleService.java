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
import org.jboss.osgi.framework.FrameworkModuleProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XModule;
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

    private final InjectedValue<SystemPackagesPlugin> injectedSystemPackages = new InjectedValue<SystemPackagesPlugin>();
    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<ResolverPlugin> injectedResolverPlugin = new InjectedValue<ResolverPlugin>();

    static void addService(ServiceTarget serviceTarget, FrameworkState frameworkState) {
        SystemBundleState bundleState = new SystemBundleState(frameworkState);
        SystemBundleService service = new SystemBundleService(bundleState);
        ServiceBuilder<SystemBundleState> builder = serviceTarget.addService(Services.SYSTEM_BUNDLE, service);
        builder.addDependency(Services.FRAMEWORK_MODULE_PROVIDER, FrameworkModuleProvider.class, bundleState.injectedModuleProvider);
        builder.addDependency(InternalServices.SYSTEM_PACKAGES_PLUGIN, SystemPackagesPlugin.class, service.injectedSystemPackages);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
        builder.addDependency(InternalServices.RESOLVER_PLUGIN, ResolverPlugin.class, service.injectedResolverPlugin);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }
    
    private SystemBundleService(SystemBundleState bundleState) {
        super(bundleState);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        try {
            BundleManager bundleManager = getBundleManager();
            SystemBundleState bundleState = getBundleState();
            bundleState.changeState(STARTING);
            OSGiMetaData metadata = createOSGiMetaData();
            XModule resModule = bundleState.createResolverModule(metadata);
            bundleState.createBundleRevision(metadata, resModule);
            bundleState.createBundleContext();
            bundleState.createStorageState(injectedBundleStorage.getValue());
            injectedResolverPlugin.getValue().addModule(resModule);
            bundleManager.injectedSystemBundle.inject(bundleState);
            bundleManager.addBundle(bundleState);
        } catch (BundleException ex) {
            throw new StartException(ex);
        }
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        BundleManager bundleManager = getBundleManager();
        bundleManager.injectedSystemBundle.uninject();
    }

    private OSGiMetaData createOSGiMetaData() {
        // Initialize the OSGiMetaData
        OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder(getSymbolicName(), getVersion());
        SystemPackagesPlugin systemPackages = injectedSystemPackages.getValue();

        List<String> exportedPackages = new ArrayList<String>();
        exportedPackages.addAll(systemPackages.getSystemPackages());
        exportedPackages.addAll(systemPackages.getFrameworkPackages());

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