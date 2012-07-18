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
package org.jboss.osgi.framework.internal;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.BundleInstallPlugin;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemServicesPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * An injection point for framework core services. Other services can depend on this.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Mar-2011
 */
final class FrameworkCoreServices extends AbstractPluginService<FrameworkCoreServices> {

    private final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
    private final InjectedValue<BundleInstallPlugin> injectedBundleInstall = new InjectedValue<BundleInstallPlugin>();
    private final InjectedValue<LifecycleInterceptorPlugin> injectedLifecycleInterceptor = new InjectedValue<LifecycleInterceptorPlugin>();
    private final InjectedValue<PackageAdmin> injectedPackageAdmin = new InjectedValue<PackageAdmin>();
    private final InjectedValue<StartLevelPlugin> injectedStartLevel = new InjectedValue<StartLevelPlugin>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private final InjectedValue<SystemServicesPlugin> injectedSystemServices = new InjectedValue<SystemServicesPlugin>();

    static void addService(ServiceTarget serviceTarget) {
        FrameworkCoreServices service = new FrameworkCoreServices();
        ServiceBuilder<FrameworkCoreServices> builder = serviceTarget.addService(InternalServices.FRAMEWORK_CORE_SERVICES, service);
        builder.addDependency(IntegrationServices.BUNDLE_INSTALL_PLUGIN, BundleInstallPlugin.class, service.injectedBundleInstall);
        builder.addDependency(Services.FRAMEWORK_CREATE, FrameworkState.class, service.injectedFramework);
        builder.addDependency(InternalServices.LIFECYCLE_INTERCEPTOR_PLUGIN, LifecycleInterceptorPlugin.class, service.injectedLifecycleInterceptor);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, service.injectedPackageAdmin);
        builder.addDependency(Services.START_LEVEL, StartLevelPlugin.class, service.injectedStartLevel);
        builder.addDependency(Services.SYSTEM_CONTEXT, BundleContext.class, service.injectedSystemContext);
        builder.addDependency(IntegrationServices.SYSTEM_SERVICES_PLUGIN, SystemServicesPlugin.class, service.injectedSystemServices);
        builder.addDependencies(InternalServices.URL_HANDLER_PLUGIN);
        builder.setInitialMode(Mode.PASSIVE);
        builder.install();
    }

    private FrameworkCoreServices() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        BundleContext systemContext = injectedSystemContext.getValue();
        SystemServicesPlugin systemServices = injectedSystemServices.getValue();
        systemServices.registerSystemServices(systemContext);
        getFrameworkState().injectedCoreServices.inject(this);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        getFrameworkState().injectedCoreServices.uninject();
    }

    @Override
    public FrameworkCoreServices getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    BundleInstallPlugin getInstallHandler() {
        return injectedBundleInstall.getValue();
    }

    FrameworkState getFrameworkState() {
        return injectedFramework.getValue();
    }

    LifecycleInterceptorPlugin getLifecycleInterceptorPlugin() {
        return injectedLifecycleInterceptor.getValue();
    }

    PackageAdmin getPackageAdmin() {
        return injectedPackageAdmin.getValue();
    }

    StartLevelPlugin getStartLevel() {
        return injectedStartLevel.getValue();
    }
}
