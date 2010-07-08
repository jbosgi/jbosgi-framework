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
package org.jboss.osgi.container.bundle;

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
import org.jboss.osgi.container.loading.VirtualFileResourceLoader;
import org.jboss.osgi.metadata.OSGiMetaData;
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
   // The modules
   private Map<ModuleIdentifier, ModuleHolder> modules = Collections.synchronizedMap(new LinkedHashMap<ModuleIdentifier, ModuleHolder>());
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

   /**
    * Construct the ModuleIdentifier from a resolver module 
    */
   public static ModuleIdentifier getModuleIdentifier(XModule resModule)
   {
      String name = resModule.getName();
      Version version = resModule.getVersion();
      return new ModuleIdentifier("" + resModule.getModuleId(), name, version.toString());
   }

   /**
    * Get the moduleId, which is also the bundleId from a module identifier 
    */
   public static long getModuleId(ModuleIdentifier identifier)
   {
      String moduleId = identifier.getGroup();
      return Long.parseLong(moduleId);
   }

   /**
    * Get the module for a given identifier
    * @return The module or null
    */
   public Module getModule(ModuleIdentifier identifier) 
   {
      ModuleHolder holder = modules.get(identifier);
      return holder != null ? holder.getModule() : null;
   }

   /**
    * Find the module in this {@link ModuleLoader}.
    * 
    * If the module has not been defined yet and a {@link ModuleSpec} is registered
    * under the given identifier, it creates the module from the spec. 
    */
   @Override
   public Module findModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      ModuleHolder holder = modules.get(identifier);
      if (holder == null)
         return null;
      
      Module module = holder.getModule();
      if (module == null)
      {
         ModuleSpec moduleSpec = holder.getModuleSpec();
         module = createModule(moduleSpec, true);
      }
      return module;
   }

   /**
    * Create a {@link ModuleSpec} from the given resolver module definition
    */
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
      Set<String> paths = new HashSet<String>();
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
      modules.put(identifier, new ModuleHolder(moduleSpec));
      return moduleSpec;
   }

   /**
    * Create the {@link Framework} module from the give resolver module definition.
    */
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
                  Set<String> exportedPaths = new HashSet<String>();
                  for (XPackageCapability cap : resModule.getPackageCapabilities())
                     exportedPaths.add(cap.getName().replace('.', '/'));
                  return exportedPaths;
               }
            };
            return smcl;
         }
      });
      modules.put(identifier, new ModuleHolder(frameworkModule));
      return frameworkModule;
   }

   /**
    * Create a {@link Module} from the given {@link ModuleSpec}
    * Optionally, change the associated bundle state to RESOLVED
    */
   public Module createModule(ModuleSpec moduleSpec, boolean resolveBundle) throws ModuleLoadException
   {
      Module module = defineModule(moduleSpec);
      ModuleIdentifier identifier = module.getIdentifier();
      
      // Change the bundle state to RESOLVED 
      if (resolveBundle == true)
      {
         long moduleId = getModuleId(identifier);
         AbstractBundle bundleState = bundleManager.getBundleById(moduleId);
         bundleState.changeState(Bundle.RESOLVED);
      }
      
      modules.put(identifier, new ModuleHolder(module));
      return module;
   }

   /**
    * A holder for the {@link ModuleSpec}  @{link Module} tuple
    */
   class ModuleHolder 
   {
      private ModuleSpec moduleSpec;
      private Module module;
      
      ModuleHolder(ModuleSpec moduleSpec)
      {
         this.moduleSpec = moduleSpec;
      }

      ModuleHolder(Module module)
      {
         this.module = module;
      }

      ModuleSpec getModuleSpec()
      {
         return moduleSpec;
      }

      Module getModule()
      {
         return module;
      }
   }
}
