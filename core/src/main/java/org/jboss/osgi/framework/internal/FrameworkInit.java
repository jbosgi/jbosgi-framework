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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;

/**
 * A service that represents the INIT state of the {@link Framework}.
 *
 *  See {@link Framework#init()} for details.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class FrameworkInit extends AbstractFrameworkService {

    private final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
    private final Mode initialMode;

    FrameworkInit(Mode initialMode) {
        super(IntegrationServices.FRAMEWORK_INIT_INTERNAL);
        this.initialMode = initialMode;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkState> builder) {
        builder.addDependency(IntegrationServices.FRAMEWORK_CREATE_INTERNAL, FrameworkState.class, injectedFramework);
        builder.addDependencies(IntegrationServices.FRAMEWORK_CORE_SERVICES);
        builder.addDependencies(IntegrationServices.BOOTSTRAP_BUNDLES_INSTALL, IntegrationServices.BOOTSTRAP_BUNDLES_COMPLETE);
        builder.addDependencies(IntegrationServices.PERSISTENT_BUNDLES_INSTALL, IntegrationServices.PERSISTENT_BUNDLES_COMPLETE);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected FrameworkState createServiceValue(StartContext startContext) throws StartException {
        LOGGER.debugf("OSGi Framework initialized");
        return injectedFramework.getValue();
    }

    @Override
    public ServiceController<FrameworkState> install(ServiceTarget serviceTarget, ServiceListener<Object> listener) {
        ServiceController<FrameworkState> controller = super.install(serviceTarget, listener);
        new FrameworkInitialized().install(serviceTarget, listener);
        new SystemContextService().install(serviceTarget, listener);
        new SystemBundleService().install(serviceTarget, listener);
        return controller;
    }

    private class FrameworkInitialized extends AbstractIntegrationService<BundleContext> {

        private final InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

        private FrameworkInitialized() {
            super(Services.FRAMEWORK_INIT);
        }

        @Override
        protected void addServiceDependencies(ServiceBuilder<BundleContext> builder) {
            builder.addDependency(IntegrationServices.SYSTEM_CONTEXT_INTERNAL, BundleContext.class, injectedBundleContext);
            builder.addDependency(IntegrationServices.FRAMEWORK_INIT_INTERNAL);
            builder.setInitialMode(initialMode);
        }

        @Override
        protected BundleContext createServiceValue(StartContext startContext) throws StartException {
            return injectedBundleContext.getValue();
        }
    }

    private class SystemContextService extends AbstractIntegrationService<BundleContext> {

        private final InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

        private SystemContextService() {
            super(Services.SYSTEM_CONTEXT);
        }

        @Override
        protected void addServiceDependencies(ServiceBuilder<BundleContext> builder) {
            builder.addDependency(Services.FRAMEWORK_INIT, BundleContext.class, injectedBundleContext);
            builder.setInitialMode(initialMode);
        }

        @Override
        protected BundleContext createServiceValue(StartContext startContext) throws StartException {
            return injectedBundleContext.getValue();
        }
    }


    private class SystemBundleService extends AbstractIntegrationService<Bundle> {

        private final InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

        private SystemBundleService() {
            super(Services.SYSTEM_BUNDLE);
        }

        @Override
        protected void addServiceDependencies(ServiceBuilder<Bundle> builder) {
            builder.addDependency(Services.FRAMEWORK_INIT, BundleContext.class, injectedBundleContext);
            builder.setInitialMode(initialMode);
        }

        @Override
        protected Bundle createServiceValue(StartContext startContext) throws StartException {
            return injectedBundleContext.getValue().getBundle();
        }
    }
}