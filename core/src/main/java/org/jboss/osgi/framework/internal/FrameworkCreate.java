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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.XEnvironment;
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

    static FrameworkState addService(ServiceTarget serviceTarget, BundleManager bundleManager) {
        FrameworkState frameworkState = new FrameworkState(bundleManager);
        FrameworkCreate service = new FrameworkCreate(frameworkState);
        ServiceBuilder<FrameworkState> builder = serviceTarget.addService(Services.FRAMEWORK_CREATE, service);
        builder.addDependency(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, DeploymentFactoryPlugin.class, frameworkState.injectedDeploymentFactory);
        builder.addDependency(InternalServices.BUNDLE_STORAGE_PLUGIN, BundleStoragePlugin.class, frameworkState.injectedBundleStorage);
        builder.addDependency(InternalServices.FRAMEWORK_EVENTS_PLUGIN, FrameworkEventsPlugin.class, frameworkState.injectedFrameworkEvents);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, frameworkState.injectedModuleManager);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, frameworkState.injectedNativeCode);
        builder.addDependency(InternalServices.SERVICE_MANAGER_PLUGIN, ServiceManagerPlugin.class, frameworkState.injectedServiceManager);
        builder.addDependency(Services.SYSTEM_BUNDLE, SystemBundleState.class, frameworkState.injectedSystemBundle);
        builder.addDependency(InternalServices.RESOLVER_PLUGIN, ResolverPlugin.class, frameworkState.injectedResolverPlugin);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, frameworkState.injectedEnvironment);
        builder.addDependency(Services.STORAGE_STATE_PROVIDER);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
        return frameworkState;
    }

    private FrameworkCreate(FrameworkState frameworkState) {
        this.frameworkState = frameworkState;
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        getBundleManager().injectedFramework.inject(frameworkState);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        getBundleManager().injectedFramework.uninject();
    }

    @Override
    public FrameworkState getValue() {
        return frameworkState;
    }
}