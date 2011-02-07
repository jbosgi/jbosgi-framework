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

import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.OSGiModuleLoader;
import org.jboss.osgi.framework.bundle.SystemBundle;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.SystemModuleProviderPlugin;
import org.jboss.osgi.framework.plugin.SystemPackagesPlugin;

/**
 * An abstract system module provider plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
public abstract class AbstractSystemModuleProviderPlugin extends AbstractPlugin implements SystemModuleProviderPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(AbstractSystemModuleProviderPlugin.class);

    private Module systemModule;

    public AbstractSystemModuleProviderPlugin(BundleManager bundleManager) {
        super(bundleManager);
    }

    @Override
    public void destroyPlugin() {
        systemModule = null;
    }

    @Override
    public Module getSystemModule() {
        return systemModule;
    }

    @Override
    public Module createSystemModule(OSGiModuleLoader moduleLoader, SystemBundle systemBundle) {
        if (systemModule != null)
            throw new IllegalStateException("System module already created");
        ModuleSpec.Builder specBuilder = ModuleSpec.build(ModuleIdentifier.create(Constants.JBOSGI_PREFIX + ".system"));
        ModuleLoader systemLoader = Module.getSystemModuleLoader();
        ModuleIdentifier identifier = Module.getSystemModule().getIdentifier();
        PathFilter systemFilter = getBundleManager().getPlugin(SystemPackagesPlugin.class).getSystemPackageFilter();
        specBuilder.addDependency(DependencySpec.createModuleDependencySpec(systemFilter, PathFilters.acceptAll(), systemLoader, identifier, false));

        ModuleSpec moduleSpec = specBuilder.create();
        moduleLoader.addModule(systemBundle.getCurrentRevision(), moduleSpec);
        try {
            systemModule = moduleLoader.loadModule(specBuilder.getIdentifier());
            return systemModule;
        } catch (ModuleLoadException ex) {
            throw new IllegalStateException(ex);
        }
    }
}