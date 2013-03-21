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
package org.jboss.osgi.framework.spi;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorService;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.LifecycleInterceptorServiceImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * A plugin that manages bundle lifecycle interceptors.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public class LifecycleInterceptorPlugin extends AbstractIntegrationService<LifecycleInterceptorService> {

    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private ServiceRegistration<LifecycleInterceptorService> registration;

    public LifecycleInterceptorPlugin() {
        super(IntegrationServices.LIFECYCLE_INTERCEPTOR_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<LifecycleInterceptorService> builder) {
        builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, injectedSystemContext);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        super.start(startContext);
        BundleContext systemContext = injectedSystemContext.getValue();
        registration = systemContext.registerService(LifecycleInterceptorService.class, getValue(), null);
        getValue().start(systemContext);
    }

    @Override
    protected LifecycleInterceptorService createServiceValue(StartContext startContext) throws StartException {
        BundleContext systemContext = injectedSystemContext.getValue();
        return new LifecycleInterceptorServiceImpl(systemContext);
    }

    @Override
    public void stop(StopContext context) {
    	getValue().stop();
        registration.unregister();
    }
}