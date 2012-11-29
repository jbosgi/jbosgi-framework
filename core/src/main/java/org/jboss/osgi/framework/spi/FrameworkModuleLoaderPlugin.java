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

import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.osgi.framework.internal.FrameworkModuleLoaderImpl;

/**
 * Integration point for the {@link ModuleLoader}.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
public class FrameworkModuleLoaderPlugin extends AbstractIntegrationService<FrameworkModuleLoader> {

    public FrameworkModuleLoaderPlugin() {
        super(IntegrationServices.FRAMEWORK_MODULE_LOADER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkModuleLoader> builder) {
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected FrameworkModuleLoader createServiceValue(StartContext startContext) {
        ServiceContainer serviceContainer = startContext.getController().getServiceContainer();
        return new FrameworkModuleLoaderImpl(serviceContainer);
    }
}
