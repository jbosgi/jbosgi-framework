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
package org.jboss.osgi.msc.bundle;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.AssertionSetting;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.Module.Flag;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleContentLoader;
import org.jboss.modules.ModuleContentLoader.Builder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleLoaderSpec;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.SystemModuleClassLoader;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.msc.loading.VirtualFileResourceLoader;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * Build the {@link ModuleSpec} from {@link OSGiMetaData}.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class ModuleManager extends ModuleLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(ModuleManager.class);

   // the bundle manager
   private BundleManager bundleManager;
   // The loaded modules
   private Map<ModuleIdentifier, Module> modules = Collections.synchronizedMap(new LinkedHashMap<ModuleIdentifier, Module>());
   // The registered module specs
   private Map<ModuleIdentifier, ModuleSpec> moduleSpecs = Collections.synchronizedMap(new LinkedHashMap<ModuleIdentifier, ModuleSpec>());
   // The framework module
   private Module frameworkModule;

   public ModuleManager(BundleManager bundleManager)
   {
      if (bundleManager == null)
         throw new IllegalArgumentException("Null bundleManager");
      
      this.bundleManager = bundleManager;
      
      // Make sure this ModuleLoader is used
      // This also registers the URLStreamHandlerFactory
      final ModuleLoader moduleLoader = this;
      Module.setModuleLoaderSelector(new ModuleLoaderSelector()
      {
         @Override
         public ModuleLoader getCurrentLoader()
         {
            return moduleLoader;
         }
      });
   }

   public static ModuleIdentifier getModuleIdentifier(XModule resModule)
   {
      String name = resModule.getName();
      Version version = resModule.getVersion();
      return new ModuleIdentifier("" + resModule.getModuleId(), name, version.toString());
   }

   public static long getModuleId(ModuleIdentifier identifier)
   {
      String moduleId = identifier.getGroup();
      return Long.parseLong(moduleId);
   }

   public Module getModule(ModuleIdentifier identifier) 
   {
      Module module = modules.get(identifier);
      return module;
   }

   @Override
   public Module findModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      Module module = modules.get(identifier);
      if (module == null)
      {
         ModuleSpec moduleSpec = moduleSpecs.remove(identifier);
         if (moduleSpec != null)
         {
            module = createModule(moduleSpec, true);
         }
      }
      return module;
   }

   public ModuleSpec createModuleSpec(XModule resModule, VirtualFile rootFile)
   {
      ModuleIdentifier identifier = getModuleIdentifier(resModule);
      ModuleSpec moduleSpec = new ModuleSpec(identifier);

      // Add the framework module as required dependency
      List<DependencySpec> dependencies = moduleSpec.getDependencies();
      DependencySpec frameworkDependency = new DependencySpec();
      frameworkDependency.setModuleIdentifier(frameworkModule.getIdentifier());
      frameworkDependency.setExport(true);
      dependencies.add(frameworkDependency);

      // Add the exported packages as paths
      Set<String> paths = new HashSet<String>(Collections.singleton("/"));
      for (XPackageCapability cap : resModule.getPackageCapabilities())
         paths.add(cap.getName().replace('.', '/'));

      // Add the bundle's {@link ResourceLoader}
      Builder builder = ModuleContentLoader.build();
      builder.add(identifier.toString(), new VirtualFileResourceLoader(rootFile, paths));
      moduleSpec.setContentLoader(builder.create());
      
      // For every {@link XWire} add a dependency on the exporter
      List<XWire> wires = resModule.getWires();
      if (wires != null)
      {
         for (XWire wire : wires)
         {
            XModule importer = wire.getImporter();
            XModule exporter = wire.getExporter();
            if (exporter != importer)
            {
               ModuleIdentifier depId = getModuleIdentifier(exporter);
               DependencySpec dep = new DependencySpec();
               dep.setModuleIdentifier(depId);
               dep.setExport(true);
               dependencies.add(dep);
            }
         }
      }

      log.debug("Created ModuleSpec: " + identifier);
      moduleSpecs.put(identifier, moduleSpec);
      return moduleSpec;
   }

   public Module createFrameworkModule(final XModule resModule) 
   {
      if (frameworkModule != null)
         throw new IllegalStateException("Framework module already created");

      final ModuleLoader moduleLoader = this;
      ModuleIdentifier identifier = getModuleIdentifier(resModule);
      frameworkModule = new Module(identifier, new ModuleLoaderSpec()
      {
         @Override
         public ModuleLoader getModuleLoader(Module module)
         {
            return moduleLoader;
         }

         @Override
         public ModuleClassLoader getModuleClassLoader(Module module)
         {
            SystemModuleClassLoader smcl = new SystemModuleClassLoader(module, Collections.<Flag> emptySet(), AssertionSetting.INHERIT)
            {
               @Override
               protected Set<String> getExportedPaths()
               {
                  Set<String> exportedPaths = super.getExportedPaths();
                  for (XPackageCapability cap : resModule.getPackageCapabilities())
                     exportedPaths.add(cap.getName());
                  return exportedPaths;
               }
            };
            return smcl;
         }
      });
      modules.put(identifier, frameworkModule);
      return frameworkModule;
   }

   public Module createModule(ModuleSpec moduleSpec, boolean resolveBundle) throws ModuleLoadException
   {
      Module module = defineModule(moduleSpec);
      ModuleIdentifier identifier = module.getIdentifier();
      modules.put(identifier, module);
      
      // Change the bundle state to RESOLVED 
      if (resolveBundle == true)
      {
         long moduleId = getModuleId(identifier);
         AbstractBundle bundleState = bundleManager.getBundleById(moduleId);
         bundleState.changeState(Bundle.RESOLVED);
      }
      
      return module;
   }
}
