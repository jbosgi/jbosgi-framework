package org.jboss.osgi.framework.internal;
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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.IntegrationService;
import org.jboss.osgi.framework.Services;
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

    static void addService(ServiceTarget serviceTarget) {
        FrameworkInit service = new FrameworkInit();
        ServiceBuilder<FrameworkState> builder = serviceTarget.addService(InternalServices.FRAMEWORK_STATE_INIT, service);
        builder.addDependency(InternalServices.FRAMEWORK_STATE_CREATE, FrameworkState.class, service.injectedFramework);
        builder.addDependencies(InternalServices.FRAMEWORK_CORE_SERVICES);
        builder.addDependencies(IntegrationService.BOOTSTRAP_BUNDLES_INSTALL, IntegrationService.BOOTSTRAP_BUNDLES_COMPLETE);
        builder.addDependencies(IntegrationService.PERSISTENT_BUNDLES_INSTALL, IntegrationService.PERSISTENT_BUNDLES_COMPLETE);
        builder.setInitialMode(Mode.ON_DEMAND);

        // Add the public FRAMEWORK_INIT service that provides the BundleContext
        FrameworkInitialized.addService(serviceTarget);

        builder.install();
    }

    private FrameworkInit() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        LOGGER.debugf("OSGi Framework initialized");
    }

    @Override
    public FrameworkState getValue() {
        return injectedFramework.getValue();
    }

    static class FrameworkInitialized extends AbstractService<BundleContext> {

        final InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

        static void addService(ServiceTarget serviceTarget) {
            FrameworkInitialized service = new FrameworkInitialized();
            ServiceBuilder<BundleContext> builder = serviceTarget.addService(Services.FRAMEWORK_INIT, service);
            builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, service.injectedBundleContext);
            builder.addDependency(InternalServices.FRAMEWORK_STATE_INIT);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }

        @Override
        public BundleContext getValue() throws IllegalStateException {
            return injectedBundleContext.getValue();
        }
    }
}