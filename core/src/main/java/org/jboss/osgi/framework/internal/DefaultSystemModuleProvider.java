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
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemModuleProvider;

/**
 * The system module provider plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
final class DefaultSystemModuleProvider extends AbstractPluginService<Module> implements SystemModuleProvider {

    private static final ModuleIdentifier SYSTEM_MODULE_IDENTIFIER = ModuleIdentifier.create(Constants.JBOSGI_PREFIX + ".system");

    // Provide logging
    final Logger log = Logger.getLogger(DefaultSystemModuleProvider.class);

    private final InjectedValue<SystemPackagesPlugin> injectedSystemPackages = new InjectedValue<SystemPackagesPlugin>();

    private Module systemModule;

    static void addService(ServiceTarget serviceTarget) {
        DefaultSystemModuleProvider service = new DefaultSystemModuleProvider();
        ServiceBuilder<Module> builder = serviceTarget.addService(Services.SYSTEM_MODULE_PROVIDER, service);
        builder.addDependency(InternalServices.SYSTEM_PACKAGES_PLUGIN, SystemPackagesPlugin.class, service.injectedSystemPackages);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultSystemModuleProvider() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        systemModule = createSystemModule();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        systemModule = null;
    }

    @Override
    public Module getValue() {
        return systemModule;
    }

    Module getSystemModule() {
        return systemModule;
    }

    private Module createSystemModule() {
        ModuleSpec.Builder specBuilder = ModuleSpec.build(SYSTEM_MODULE_IDENTIFIER);
        ModuleLoader systemLoader = Module.getBootModuleLoader();
        ModuleIdentifier identifier = Module.getSystemModule().getIdentifier();
        PathFilter systemFilter = injectedSystemPackages.getValue().getSystemPackageFilter();
        specBuilder.addDependency(DependencySpec.createModuleDependencySpec(systemFilter, PathFilters.acceptAll(), systemLoader, identifier, false));

        try {
            final ModuleSpec moduleSpec = specBuilder.create();
            ModuleLoader moduleLoader = new ModuleLoader() {

                @Override
                protected ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException {
                    return (moduleSpec.getModuleIdentifier().equals(identifier) ? moduleSpec : null);
                }

                @Override
                public String toString() {
                    return getClass().getSimpleName();
                }
            };
            return moduleLoader.loadModule(specBuilder.getIdentifier());
        } catch (ModuleLoadException ex) {
            throw new IllegalStateException(ex);
        }
    }
}