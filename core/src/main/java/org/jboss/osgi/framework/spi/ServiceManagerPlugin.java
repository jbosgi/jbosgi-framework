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
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.ServiceManagerImpl;

/**
 * A plugin that manages OSGi services
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class ServiceManagerPlugin extends AbstractIntegrationService<ServiceManager> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<FrameworkEvents> injectedFrameworkEvents = new InjectedValue<FrameworkEvents>();
    private final InjectedValue<ModuleManager> injectedModuleManager = new InjectedValue<ModuleManager>();

    public ServiceManagerPlugin() {
        super(IntegrationServices.SERVICE_MANAGER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<ServiceManager> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(IntegrationServices.FRAMEWORK_EVENTS, FrameworkEvents.class, injectedFrameworkEvents);
        builder.addDependency(IntegrationServices.MODULE_MANGER, ModuleManager.class, injectedModuleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected ServiceManager createServiceValue(StartContext startContext) throws StartException {
        FrameworkEvents events = injectedFrameworkEvents.getValue();
        return new ServiceManagerImpl(events);
    }
}