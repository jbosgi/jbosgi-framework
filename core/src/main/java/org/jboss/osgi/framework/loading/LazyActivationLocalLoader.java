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

import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.PathFilter;
import org.jboss.modules.PathFilters;
import org.jboss.modules.Resource;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.HostBundle;
import org.jboss.osgi.framework.bundle.OSGiModuleLoader;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;

/**
 * A local loader that activates the associated host bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Dec-2010
 */
public class LazyActivationLocalLoader implements LocalLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(LazyActivationLocalLoader.class);

   private final HostBundle hostBundle;
   private final ModuleManagerPlugin moduleManager;
   private final ModuleIdentifier identifier;

   // The dependencies excluding the local dependency
   private final List<DependencySpec> moduleDependencies;
   private PathFilter lazyPackagesFilter;
   
   public LazyActivationLocalLoader(HostBundle hostBundle, ModuleIdentifier identifier, List<DependencySpec> moduleDependencies)
   {
      this.moduleDependencies = moduleDependencies;
      this.hostBundle = hostBundle;
      this.identifier = identifier;

      BundleManager bundleManager = hostBundle.getBundleManager();
      moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
      lazyPackagesFilter = hostBundle.getLazyPackagesFilter();
   }

   @Override
   public Class<?> loadClassLocal(String className, boolean resolve)
   {
      String pathForClassName = getPathForClassName(className);
      if (lazyPackagesFilter.accept(pathForClassName))
      {
         log.debugf("Trigger lazy activation on class load: %s", className);
         try
         {
            lazyPackagesFilter = PathFilters.rejectAll();
            Module module = moduleManager.getModule(identifier);
            OSGiModuleLoader moduleLoader = moduleManager.getModuleLoader();
            List<DependencySpec> dependencies = new ArrayList<DependencySpec>(moduleDependencies);
            dependencies.add(DependencySpec.createLocalDependencySpec());
            moduleLoader.setAndRelinkDependencies(module, dependencies);
            Class<?> definedClass = module.getClassLoader().loadClass(className);
            
            hostBundle.activateOnClassLoad(definedClass);
            
            return definedClass;
         }
         catch (ModuleLoadException ex)
         {
            throw new IllegalStateException(ex);
         }
         catch (ClassNotFoundException ex)
         {
            // ignore
         }
      }

      return null;
   }

   private String getPathForClassName(String className)
   {
      if (className.endsWith(".class"))
         className = className.substring(0, className.length() - 6);
      className = className.substring(0, className.lastIndexOf('.'));
      className = className.replace('.', '/');
      return className;
   }

   @Override
   public List<Resource> loadResourceLocal(String name)
   {
      return Collections.emptyList();
   }

   @Override
   public Resource loadResourceLocal(String root, String name)
   {
      return null;
   }
}
