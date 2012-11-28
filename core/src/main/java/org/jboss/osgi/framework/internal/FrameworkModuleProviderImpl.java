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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

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
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.BundleReferenceClassLoader;
import org.jboss.osgi.framework.spi.FrameworkModuleProvider;
import org.jboss.osgi.framework.spi.SystemPaths;
import org.jboss.osgi.resolver.XBundle;

/**
 * The system module provider plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
public class FrameworkModuleProviderImpl implements FrameworkModuleProvider {

    private static final ModuleIdentifier FRAMEWORK_MODULE_IDENTIFIER = ModuleIdentifier.create(Constants.JBOSGI_PREFIX + ".framework");
    private final BundleManagerImpl bundleManager;
    private final SystemPaths systemPaths;
    private Module frameworkModule;

    public FrameworkModuleProviderImpl(BundleManager bundleManager, SystemPaths systemPaths) {
        this.bundleManager = (BundleManagerImpl) bundleManager;
        this.systemPaths = systemPaths;
    }

    @Override
    public XBundle getSystemBundle() {
        return bundleManager.getSystemBundle();
    }
    
    @Override
    public Module getFrameworkModule() {
        if (frameworkModule == null) {
            frameworkModule = createFrameworkModule();
        }
        return frameworkModule;
    }

    @Override
    public Module createFrameworkModule() {

        ModuleSpec.Builder specBuilder = ModuleSpec.build(FRAMEWORK_MODULE_IDENTIFIER);
        Set<String> bootPaths = systemPaths.getBootDelegationPaths();
        PathFilter bootFilter = systemPaths.getBootDelegationFilter();
        PathFilter acceptAll = PathFilters.acceptAll();
        specBuilder.addDependency(DependencySpec.createSystemDependencySpec(bootFilter, acceptAll, bootPaths));

        final ClassLoader classLoader = BundleManagerImpl.class.getClassLoader();
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
        Set<String> paths = systemPaths.getSystemPaths();
        PathFilter filter = systemPaths.getSystemFilter();
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec(filter, PathFilters.acceptAll(), localLoader, paths));
        specBuilder.setModuleClassLoaderFactory(new BundleReferenceClassLoader.Factory<XBundle>(getSystemBundle()));

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