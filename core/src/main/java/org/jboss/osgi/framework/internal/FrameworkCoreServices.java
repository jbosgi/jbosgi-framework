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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.BundleInstallProvider;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemServicesProvider;
import org.osgi.framework.BundleContext;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * An injection point for framework core services. Other services can depend on this.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Mar-2011
 */
public final class FrameworkCoreServices extends AbstractService<FrameworkCoreServices> {

    private final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
    private final InjectedValue<BundleInstallProvider> injectedInstallProvider = new InjectedValue<BundleInstallProvider>();
    private final InjectedValue<LifecycleInterceptorPlugin> injectedLifecycleInterceptor = new InjectedValue<LifecycleInterceptorPlugin>();
    private final InjectedValue<PackageAdmin> injectedPackageAdmin = new InjectedValue<PackageAdmin>();
    private final InjectedValue<StartLevelPlugin> injectedStartLevel = new InjectedValue<StartLevelPlugin>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private final InjectedValue<SystemServicesProvider> injectedServicesProvider = new InjectedValue<SystemServicesProvider>();

    static void addService(ServiceTarget serviceTarget) {
        FrameworkCoreServices service = new FrameworkCoreServices();
        ServiceBuilder<FrameworkCoreServices> builder = serviceTarget.addService(Services.FRAMEWORK_CORE_SERVICES, service);
        builder.addDependency(IntegrationServices.BUNDLE_INSTALL_PROVIDER, BundleInstallProvider.class, service.injectedInstallProvider);
        builder.addDependency(Services.FRAMEWORK_CREATE, FrameworkState.class, service.injectedFramework);
        builder.addDependency(InternalServices.LIFECYCLE_INTERCEPTOR_PLUGIN, LifecycleInterceptorPlugin.class, service.injectedLifecycleInterceptor);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, service.injectedPackageAdmin);
        builder.addDependency(Services.START_LEVEL, StartLevelPlugin.class, service.injectedStartLevel);
        builder.addDependency(Services.SYSTEM_CONTEXT, BundleContext.class, service.injectedSystemContext);
        builder.addDependency(IntegrationServices.SYSTEM_SERVICES_PROVIDER, SystemServicesProvider.class, service.injectedServicesProvider);
        builder.addDependencies(InternalServices.URL_HANDLER_PLUGIN, InternalServices.WEBXML_VERIFIER_PLUGIN);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private FrameworkCoreServices() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        LOGGER.debugf("Starting: %s", context.getController().getName());
        BundleContext systemContext = injectedSystemContext.getValue();
        SystemServicesProvider servicesProvider = injectedServicesProvider.getValue();
        servicesProvider.registerSystemServices(systemContext);
        getFrameworkState().injectedCoreServices.inject(this);
    }

    @Override
    public void stop(StopContext context) {
        LOGGER.debugf("Stopping: %s", context.getController().getName());
        getFrameworkState().injectedCoreServices.uninject();
    }

    @Override
    public FrameworkCoreServices getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    BundleInstallProvider getInstallHandler() {
        return injectedInstallProvider.getValue();
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

    StartLevelPlugin getStartLevelPlugin() {
        return injectedStartLevel.getValue();
    }
}