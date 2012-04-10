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

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.FrameworkModuleProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemPathsProvider;
import org.osgi.framework.Bundle;

/**
 * The system module provider plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
final class DefaultFrameworkModuleProvider extends AbstractPluginService<FrameworkModuleProvider> implements FrameworkModuleProvider {

    private static final ModuleIdentifier FRAMEWORK_MODULE_IDENTIFIER = ModuleIdentifier.create(Constants.JBOSGI_PREFIX + ".framework");
    private final InjectedValue<SystemPathsProvider> injectedSystemPaths = new InjectedValue<SystemPathsProvider>();

    private Module frameworkModule;

    static void addService(ServiceTarget serviceTarget) {
        DefaultFrameworkModuleProvider service = new DefaultFrameworkModuleProvider();
        ServiceBuilder<FrameworkModuleProvider> builder = serviceTarget.addService(Services.FRAMEWORK_MODULE_PROVIDER, service);
        builder.addDependency(Services.SYSTEM_PATHS_PROVIDER, SystemPathsProvider.class, service.injectedSystemPaths);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultFrameworkModuleProvider() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        frameworkModule = null;
    }

    @Override
    public FrameworkModuleProvider getValue() {
        return this;
    }

    @Override
    public Module getFrameworkModule(Bundle bundle) {
        if (frameworkModule == null) {
            SystemBundleState systemBundle = (SystemBundleState) bundle;
            frameworkModule = createFrameworkModule(systemBundle);
        }
        return frameworkModule;
    }

    private Module createFrameworkModule(SystemBundleState systemBundle) {

        ModuleSpec.Builder specBuilder = ModuleSpec.build(FRAMEWORK_MODULE_IDENTIFIER);
        SystemPathsProvider plugin = injectedSystemPaths.getValue();
        Set<String> bootPaths = plugin.getBootDelegationPaths();
        PathFilter bootFilter = plugin.getBootDelegationFilter();
        PathFilter acceptAll = PathFilters.acceptAll();
        specBuilder.addDependency(DependencySpec.createSystemDependencySpec(bootFilter, acceptAll, bootPaths));

        final ClassLoader classLoader = BundleManager.class.getClassLoader();
        LocalLoader localLoader = new LocalLoader() {

            @Override
            public Class<?> loadClassLocal(String name, boolean resolve) {
                try {
                    return classLoader.loadClass(name);
                } catch (ClassNotFoundException ex) {
                    return null;
                }
            }

            @Override
            public Package loadPackageLocal(String name) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Resource> loadResourceLocal(String name) {
                return Collections.emptyList();
            }
        };
        Set<String> systemPaths = plugin.getSystemPaths();
        PathFilter systemFilter = plugin.getSystemFilter();
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec(systemFilter, PathFilters.acceptAll(), localLoader, systemPaths));
        specBuilder.setModuleClassLoaderFactory(new BundleReferenceClassLoader.Factory<SystemBundleState>(systemBundle));

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
            throw MESSAGES.illegalStateCannotCreateFrameworkModule(ex);
        }
    }
}