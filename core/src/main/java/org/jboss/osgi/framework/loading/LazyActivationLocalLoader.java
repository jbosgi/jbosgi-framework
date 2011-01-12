/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.osgi.framework.loading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.PathFilter;
import org.jboss.modules.Resource;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.HostBundle;
import org.jboss.osgi.framework.bundle.OSGiModuleLoader;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.osgi.framework.BundleException;

/**
 * A local loader that activates the associated host bundle.
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Dec-2010
 */
public class LazyActivationLocalLoader implements LocalLoader {

    // Provide logging
    private static final Logger log = Logger.getLogger(LazyActivationLocalLoader.class);

    private final HostBundle hostBundle;
    private final ModuleManagerPlugin moduleManager;
    private final ModuleIdentifier identifier;

    // The dependencies excluding the local dependency
    private final List<DependencySpec> moduleDependencies;
    private final PathFilter lazyPackagesFilter;
    private final AtomicBoolean relinkModule = new AtomicBoolean(true);
    private final AtomicBoolean activateHost = new AtomicBoolean(true);

    public LazyActivationLocalLoader(HostBundle hostBundle, ModuleIdentifier identifier, List<DependencySpec> moduleDependencies, PathFilter lazyFilter) {
        this.moduleDependencies = moduleDependencies;
        this.hostBundle = hostBundle;
        this.identifier = identifier;

        BundleManager bundleManager = hostBundle.getBundleManager();
        moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
        lazyPackagesFilter = lazyFilter;
    }

    @Override
    public Class<?> loadClassLocal(String className, boolean resolve) {
        Class<?> definedClass = null;

        String pathForClassName = getPathForClassName(className);
        if (lazyPackagesFilter.accept(pathForClassName)) {
            // Relink the module and load the requested class
            try {
                Module module = moduleManager.getModule(identifier);
                if (relinkModule.getAndSet(false)) {
                    log.debugf("Relinking [%s] on class load: %s", identifier, className);
                    OSGiModuleLoader moduleLoader = moduleManager.getModuleLoader();
                    List<DependencySpec> dependencies = new ArrayList<DependencySpec>(moduleDependencies);
                    dependencies.add(DependencySpec.createLocalDependencySpec());
                    moduleLoader.setAndRelinkDependencies(module, dependencies);
                }
                definedClass = module.getClassLoader().loadClass(className);
            } catch (ModuleLoadException ex) {
                throw new IllegalStateException(ex);
            } catch (ClassNotFoundException ex) {
                return null;
            }

            // Activate the bundle lazily
            if (activateHost.getAndSet(false)) {
                try {
                    log.debugf("Lazy activation [%s] on class load: %s", identifier, className);
                    hostBundle.activateOnClassLoad(definedClass);
                } catch (BundleException ex) {
                    log.errorf(ex, "Cannot activate lazily: %s", hostBundle);
                }
            }
        }
        return definedClass;
    }

    private String getPathForClassName(String className) {
        if (className.endsWith(".class"))
            className = className.substring(0, className.length() - 6);
        className = className.substring(0, className.lastIndexOf('.'));
        className = className.replace('.', '/');
        return className;
    }

    @Override
    public List<Resource> loadResourceLocal(String name) {
        return Collections.emptyList();
    }

    @Override
    public Resource loadResourceLocal(String root, String name) {
        return null;
    }
}
