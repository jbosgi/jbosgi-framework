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
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorService;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.BundleLifecycle;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.osgi.framework.BundleContext;

/**
 * An injection point for framework core services. Other services can depend on this.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Mar-2011
 */
final class CoreServices extends AbstractIntegrationService<CoreServices> {

    private final InjectedValue<FrameworkState> injectedFrameworkState = new InjectedValue<FrameworkState>();
    private final InjectedValue<BundleLifecycle> injectedBundleLifecycle = new InjectedValue<BundleLifecycle>();
    private final InjectedValue<LifecycleInterceptorService> injectedLifecycleInterceptor = new InjectedValue<LifecycleInterceptorService>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();

    CoreServices() {
        super(IntegrationServices.FRAMEWORK_CORE_SERVICES);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<CoreServices> builder) {
        builder.addDependency(IntegrationServices.BUNDLE_LIFECYCLE_PLUGIN, BundleLifecycle.class, injectedBundleLifecycle);
        builder.addDependency(IntegrationServices.FRAMEWORK_CREATE_INTERNAL, FrameworkState.class, injectedFrameworkState);
        builder.addDependency(IntegrationServices.LIFECYCLE_INTERCEPTOR_PLUGIN, LifecycleInterceptorService.class, injectedLifecycleInterceptor);
        builder.addDependency(IntegrationServices.SYSTEM_CONTEXT_INTERNAL, BundleContext.class, injectedSystemContext);
        builder.addDependency(IntegrationServices.DEPRECATED_PACKAGE_ADMIN_PLUGIN);
        builder.addDependency(IntegrationServices.DEPRECATED_START_LEVEL_PLUGIN);
        builder.addDependency(IntegrationServices.SYSTEM_SERVICES_PLUGIN);
        builder.addDependency(IntegrationServices.URL_HANDLER_PLUGIN);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        super.start(startContext);
        FrameworkState frameworkState = injectedFrameworkState.getValue();
        frameworkState.injectedCoreServices.inject(getValue());
    }

    @Override
    protected CoreServices createServiceValue(StartContext startContext) throws StartException {
        return this;
    }

    @Override
    public void stop(StopContext context) {
        FrameworkState frameworkState = injectedFrameworkState.getValue();
        frameworkState.injectedCoreServices.uninject();
    }

    BundleLifecycle getBundleLifecycle() {
        return injectedBundleLifecycle.getValue();
    }

    LifecycleInterceptorService getLifecycleInterceptorService() {
        return injectedLifecycleInterceptor.getValue();
    }
}
