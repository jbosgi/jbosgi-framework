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
package org.jboss.osgi.framework.plugin.internal;

import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;
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
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.OSGiModuleLoader;
import org.jboss.osgi.framework.loading.SystemBundleModuleClassLoader;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.SystemModuleProviderPlugin;
import org.jboss.osgi.framework.plugin.SystemPackagesPlugin;

/**
 * The system module provider plugin.
 * 
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
public class SystemModuleProviderPluginImpl extends AbstractPlugin implements SystemModuleProviderPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(SystemModuleProviderPluginImpl.class);

    private OSGiModuleLoader moduleLoader;
    private Module frameworkModule;
    private Module systemModule;

    public SystemModuleProviderPluginImpl(BundleManager bundleManager) {
        super(bundleManager);
    }

    @Override
    public void initPlugin() {
        ModuleManagerPlugin moduleManager = getBundleManager().getPlugin(ModuleManagerPlugin.class);
        moduleLoader = moduleManager.getModuleLoader();
        systemModule = createSystemModule();
        frameworkModule = createFrameworkModule();
    }
    
    @Override
    public void destroyPlugin() {
        frameworkModule = null;
        systemModule = null;
    }

    @Override
    public Module getSystemModule() {
        return systemModule;
    }

    @Override
    public Module getFrameworkModule() {
        return frameworkModule;
    }

    @Override
    public Module createSystemModule() {
        if (systemModule != null)
            throw new IllegalStateException("System module already created");
        ModuleSpec.Builder specBuilder = ModuleSpec.build(ModuleIdentifier.create(Constants.JBOSGI_PREFIX + ".system"));
        ModuleLoader systemLoader = Module.getSystemModuleLoader();
        ModuleIdentifier identifier = Module.getSystemModule().getIdentifier();
        PathFilter systemFilter = getBundleManager().getPlugin(SystemPackagesPlugin.class).getSystemPackageFilter();
        specBuilder.addDependency(DependencySpec.createModuleDependencySpec(systemFilter, PathFilters.acceptAll(), systemLoader, identifier, false));

        ModuleSpec moduleSpec = specBuilder.create();
        moduleLoader.addModule(getBundleManager().getSystemBundle().getCurrentRevision(), moduleSpec);
        try {
            systemModule = moduleLoader.loadModule(specBuilder.getIdentifier());
            return systemModule; 
        } catch (ModuleLoadException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public Module createFrameworkModule() {
        if (frameworkModule != null)
            throw new IllegalStateException("Framework module already created");
        
        ModuleIdentifier systemIdentifier = getSystemModule().getIdentifier();
        ModuleSpec.Builder specBuilder = ModuleSpec.build(ModuleIdentifier.create(Constants.JBOSGI_PREFIX + ".framework"));
        specBuilder.addDependency(DependencySpec.createModuleDependencySpec(PathFilters.acceptAll(), PathFilters.acceptAll(), moduleLoader, systemIdentifier, false));

        SystemPackagesPlugin plugin = getBundleManager().getPlugin(SystemPackagesPlugin.class);
        PathFilter frameworkFilter = plugin.getFrameworkPackageFilter();
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
            public List<Resource> loadResourceLocal(String name) {
                return Collections.emptyList();
            }

            @Override
            public Resource loadResourceLocal(String root, String name) {
                return null;
            }
        };
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec(frameworkFilter, PathFilters.acceptAll(), localLoader, plugin.getFrameworkPackagePaths()));
        specBuilder.setModuleClassLoaderFactory(new SystemBundleModuleClassLoader.Factory(getBundleManager().getSystemBundle()));

        ModuleSpec moduleSpec = specBuilder.create();
        moduleLoader.addModule(getBundleManager().getSystemBundle().getCurrentRevision(), moduleSpec);
        try {
            frameworkModule = moduleLoader.loadModule(specBuilder.getIdentifier());
            return frameworkModule; 
        } catch (ModuleLoadException ex) {
            throw new IllegalStateException(ex);
        }
    }
}