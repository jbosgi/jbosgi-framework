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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.ModuleLoaderPlugin;
import org.jboss.osgi.framework.spi.SystemPathsPlugin;
import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;

/**
 * A service that represents the CREATED state of the {@link Framework}.
 *
 * When this services has started, the system bundle context is availbale as
 * well as the basic infrastructure to register OSGi services.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class FrameworkCreate extends AbstractFrameworkService {

    private final FrameworkState frameworkState;

    FrameworkCreate(FrameworkState frameworkState) {
        super(InternalServices.FRAMEWORK_STATE_CREATE);
        this.frameworkState = frameworkState;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkState> builder) {
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, frameworkState.injectedDeploymentFactory);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, frameworkState.injectedBundleStorage);
        builder.addDependency(InternalServices.FRAMEWORK_EVENTS_PLUGIN, FrameworkEventsPlugin.class, frameworkState.injectedFrameworkEvents);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, frameworkState.injectedModuleManager);
        builder.addDependency(IntegrationServices.MODULE_LOADER_PLUGIN, ModuleLoaderPlugin.class, frameworkState.injectedModuleLoader);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, frameworkState.injectedNativeCode);
        builder.addDependency(InternalServices.SERVICE_MANAGER_PLUGIN, ServiceManagerPlugin.class, frameworkState.injectedServiceManager);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS_PLUGIN, SystemPathsPlugin.class, frameworkState.injectedSystemPaths);
        builder.addDependency(InternalServices.SYSTEM_BUNDLE, SystemBundleState.class, frameworkState.injectedSystemBundle);
        builder.addDependency(InternalServices.LOCK_MANAGER_PLUGIN, LockManager.class, frameworkState.injectedLockManager);
        builder.addDependency(Services.RESOLVER, ResolverPlugin.class, frameworkState.injectedResolverPlugin);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, frameworkState.injectedEnvironment);
        builder.addDependency(IntegrationServices.STORAGE_STATE_PLUGIN);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        getBundleManager().injectedFramework.inject(frameworkState);
    }

    @Override
    public void stop(StopContext context) {
        getBundleManager().injectedFramework.uninject();
    }

    @Override
    public FrameworkState getValue() {
        return frameworkState;
    }

    static class FrameworkCreated extends AbstractIntegrationService<BundleContext> {

        final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
        private final Mode initialMode;

        FrameworkCreated(Mode initialMode) {
            super(Services.FRAMEWORK_CREATE);
            this.initialMode = initialMode;
        }

        @Override
        protected void addServiceDependencies(ServiceBuilder<BundleContext> builder) {
            builder.addDependency(InternalServices.FRAMEWORK_STATE_CREATE, FrameworkState.class, injectedFramework);
            builder.setInitialMode(initialMode);
        }

        @Override
        public BundleContext getValue() throws IllegalStateException {
            FrameworkState frameworkState = injectedFramework.getValue();
            SystemBundleState systemBundle = frameworkState.getSystemBundle();
            return systemBundle.getBundleContext();
        }
    }
}
