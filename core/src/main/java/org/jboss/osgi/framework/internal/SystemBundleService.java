/*
 * #%L
 * JBossOSGi Framework Core
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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemPathsProvider;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XEnvironment;
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

    private final InjectedValue<SystemPathsProvider> injectedSystemPaths = new InjectedValue<SystemPathsProvider>();
    private final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    private final InjectedValue<XEnvironment> injectedEnvironmentPlugin = new InjectedValue<XEnvironment>();
    private SystemBundleState bundleState;

    static void addService(ServiceTarget serviceTarget, FrameworkState frameworkState) {
        SystemBundleService service = new SystemBundleService(frameworkState);
        ServiceBuilder<SystemBundleState> builder = serviceTarget.addService(Services.SYSTEM_BUNDLE, service);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, service.injectedEnvironmentPlugin);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS_PROVIDER, SystemPathsProvider.class, service.injectedSystemPaths);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, service.injectedBundleStorage);
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
            OSGiMetaData metadata = createOSGiMetaData();
            SystemBundleRevision revision = new SystemBundleRevision(getFrameworkState(), metadata);
            bundleState = createBundleState(revision);
            bundleState.changeState(Bundle.STARTING);
            addToEnvironment(revision);
            bundleState.createBundleContext();
            bundleState.createStorageState(injectedBundleStorage.getValue());
            BundleManagerPlugin bundleManager = getBundleManager();
            bundleManager.injectedSystemBundle.inject(bundleState);
        } catch (BundleException ex) {
            throw new StartException(ex);
        }
    }

    SystemBundleState createBundleState(SystemBundleRevision revision) {
        return new SystemBundleState(getFrameworkState(), revision);
    }

    @Override
    SystemBundleState getBundleState() {
        return bundleState;
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        BundleManagerPlugin bundleManager = getBundleManager();
        bundleManager.injectedSystemBundle.uninject();
    }

    private OSGiMetaData createOSGiMetaData() {

        // Initialize the OSGiMetaData
        OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder(Constants.SYSTEM_BUNDLE_SYMBOLICNAME, Version.emptyVersion);
        SystemPathsProvider systemPackages = injectedSystemPaths.getValue();

        List<String> exportedPackages = new ArrayList<String>();
        exportedPackages.addAll(systemPackages.getSystemPackages());

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

    private void addToEnvironment(SystemBundleRevision sysrev) {
        XEnvironment env = injectedEnvironmentPlugin.getValue();
        env.installResources(sysrev);
    }
}