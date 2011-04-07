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

import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.DeployerService;
import org.jboss.osgi.framework.DeployerServiceProvider;

/**
 * An injection point for framework core services. Other services can depend on this.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Mar-2011
 */
final class CoreServices extends AbstractService<CoreServices> {

    // Provide logging
    static final Logger log = Logger.getLogger(CoreServices.class);

    private final InjectedValue<FrameworkCreate> injectedFramework = new InjectedValue<FrameworkCreate>();
    private final InjectedValue<DeployerService> injectedDeployerService = new InjectedValue<DeployerService>();
    private final InjectedValue<PackageAdminPlugin> injectedPackageAdmin = new InjectedValue<PackageAdminPlugin>();
    private final InjectedValue<StartLevelPlugin> injectedStartLevel = new InjectedValue<StartLevelPlugin>();
    private final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();

    static void addService(ServiceTarget serviceTarget) {
        CoreServices service = new CoreServices();
        ServiceBuilder<CoreServices> builder = serviceTarget.addService(Services.CORE_SERVICES, service);
        builder.addDependency(DeployerServiceProvider.SERVICE_NAME, DeployerService.class, service.injectedDeployerService);
        builder.addDependency(Services.FRAMEWORK_CREATE, FrameworkCreate.class, service.injectedFramework);
        builder.addDependency(Services.PACKAGE_ADMIN_PLUGIN, PackageAdminPlugin.class, service.injectedPackageAdmin);
        builder.addDependency(Services.START_LEVEL_PLUGIN, StartLevelPlugin.class, service.injectedStartLevel);
        builder.addDependency(Services.SYSTEM_BUNDLE, SystemBundleState.class, service.injectedSystemBundle);
        builder.addDependency(Services.URL_HANDLER_PLUGIN);
        builder.install();
    }

    private CoreServices() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.debugf("Starting: %s", context.getController().getName());
        getFrameworkState().injectedCoreServices.inject(this);
    }

    @Override
    public void stop(StopContext context) {
        log.debugf("Stopping: %s", context.getController().getName());
        getFrameworkState().injectedCoreServices.uninject();
    }

    @Override
    public CoreServices getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    DeployerService getDeployerService() {
        return injectedDeployerService.getValue();
    }

    FrameworkState getFrameworkState() {
        return injectedFramework.getValue().getFrameworkState();
    }

    PackageAdminPlugin getPackageAdmin() {
        return injectedPackageAdmin.getValue();
    }

    StartLevelPlugin getStartLevelPlugin() {
        return injectedStartLevel.getValue();
    }
    
    SystemBundleState getSystemBundle() {
        return injectedSystemBundle.getValue();
    }
}