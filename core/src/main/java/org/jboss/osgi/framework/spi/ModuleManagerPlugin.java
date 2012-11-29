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
import org.jboss.osgi.framework.internal.ModuleManagerImpl;
import org.jboss.osgi.resolver.XEnvironment;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
public class ModuleManagerPlugin extends AbstractIntegrationService<ModuleManager> {

    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<SystemPaths> injectedSystemPaths = new InjectedValue<SystemPaths>();
    private final InjectedValue<FrameworkModuleProvider> injectedFrameworkModule = new InjectedValue<FrameworkModuleProvider>();
    private final InjectedValue<FrameworkModuleLoader> injectedModuleLoader = new InjectedValue<FrameworkModuleLoader>();

    public ModuleManagerPlugin() {
        super(IntegrationServices.MODULE_MANGER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<ModuleManager> builder) {
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_LOADER, FrameworkModuleLoader.class, injectedModuleLoader);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_PROVIDER, FrameworkModuleProvider.class, injectedFrameworkModule);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS, SystemPaths.class, injectedSystemPaths);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected ModuleManager createServiceValue(StartContext startContext) throws StartException {
        FrameworkModuleProvider moduleProvider = injectedFrameworkModule.getValue();
        XEnvironment env = injectedEnvironment.getValue();
        SystemPaths syspaths = injectedSystemPaths.getValue();
        FrameworkModuleLoader moduleLoader = injectedModuleLoader.getValue();
        return new ModuleManagerImpl(env, syspaths, moduleProvider, moduleLoader);
    }
}
