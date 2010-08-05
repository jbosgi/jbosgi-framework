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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.loading.FrameworkModuleClassLoader;
import org.jboss.osgi.container.loading.JBossLoggingModuleLogger;
import org.jboss.osgi.container.loading.OSGiModuleClassLoader;
import org.jboss.osgi.container.loading.VirtualFileResourceLoader;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequireBundleRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.application.Framework;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * Build the {@link ModuleSpec} from {@link OSGiMetaData}.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class ModuleManager extends ModuleLoader
{
   // Multiple revisions of the same bundle can co-exist, when a bundle has been updated but not yet refreshed.
   // This marker is appended to the version to make each revision unique.
   private static final String REVISION_MARKER = "-rev";

   // Provide logging
   private static final Logger log = Logger.getLogger(ModuleManager.class);

   // the bundle manager
   private BundleManager bundleManager;
   // The framework module identifier
   private ModuleIdentifier frameworkIdentifier;
   // The modules that are registered with this {@link ModuleLoader}
   private Map<ModuleIdentifier, ModuleHolder> modules = Collections.synchronizedMap(new LinkedHashMap<ModuleIdentifier, ModuleHolder>());

   public ModuleManager(BundleManager bundleManager)
   {
      if (bundleManager == null)
         throw new IllegalArgumentException("Null bundleManager");

      this.bundleManager = bundleManager;

      // Set the {@link ModuleLogger}
      Module.setModuleLogger(new JBossLoggingModuleLogger(Logger.getLogger(ModuleClassLoader.class)));
      
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
    * Return the module identifier for a given XModule. The associated revision of the bundle 
    * is also taken into account.
    * @param resModule the resolver module to obtain the identifier for
    * @return the Module Identifier
    */
   public static ModuleIdentifier getModuleIdentifier(XModule resModule)
   {
      ModuleIdentifier id = resModule.getAttachment(ModuleIdentifier.class);
      if (id != null)
         return id;

      ModuleSpec moduleSpec = resModule.getAttachment(ModuleSpec.class);
      if (moduleSpec != null)
         return moduleSpec.getIdentifier();

      BundleRevision bundleRevision = resModule.getAttachment(BundleRevision.class);
      if (bundleRevision == null)
         return null;

      return getModuleIdentifier(resModule, bundleRevision.getRevision());
   }

   private static ModuleIdentifier getModuleIdentifier(XModule resModule, int revision)
   {
      long moduleId = resModule.getModuleId();
      String artifact = resModule.getName();
      Version version = resModule.getVersion();

      // Modules can define their dependencies on bundles by prefixing the artifact name with 'xservice'
      // For ordinary modules we encode the bundleId in the group name to make sure the identifier is unique.
      // When the underlying ModuleLoader supports unloading of modules we can hopefully create stable module identifiers
      // that do not contain the bundleId, which is inherently unstable accross framework restarts.

      // [TODO] Remove symbolic name prefix hack 
      String group = "jbosgi";
      if (moduleId > 0 && artifact.startsWith("xservice") == false)
         group += "[" + moduleId + "]";

      ModuleIdentifier identifier = new ModuleIdentifier(group, artifact, version + REVISION_MARKER + revision);
      resModule.addAttachment(ModuleIdentifier.class, identifier);
      return identifier;
   }

   /**
    * Get the bundle from a module identifier 
    */
   public AbstractBundle getBundle(ModuleIdentifier identifier)
   {
      ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getBundle() : null;
   }

   /**
    * Get the set of registered module idetifiers
    */
   public Set<ModuleIdentifier> getModuleIdentifiers()
   {
      return Collections.unmodifiableSet(modules.keySet());
   }

   /**
    * Get the module spec for a given identifier
    * @return The module spec or null
    */
   public ModuleSpec getModuleSpec(ModuleIdentifier identifier)
   {
      ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getModuleSpec() : null;
   }

   @Override
   public ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getModuleSpec() : null;
   }

   /**
    * Get the module for the given identifier
    * @return The previously loaded module or null
    */
   public Module getModule(ModuleIdentifier identifier)
   {
      ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getModule() : null;
   }
   
   private ModuleHolder getModuleHolder(ModuleIdentifier identifier)
   {
      ModuleHolder holder = modules.get(identifier);
      if (holder == null)
         holder = getModuleFromUnrevisionedIdentifier(identifier);
      return holder;
   }

   // In some cases a module is looked up without a revision being specified. An example being a module
   // dependency declared in a modules.xml file. In that case we need to search for the module
   private ModuleHolder getModuleFromUnrevisionedIdentifier(ModuleIdentifier identifier)
   {
      if (identifier.getVersion() != null && identifier.getVersion().contains(REVISION_MARKER))
         // There is a revision identifier, so don't search.
         return null;

      Set<ModuleIdentifier> versions = new TreeSet<ModuleIdentifier>(new Comparator<ModuleIdentifier>()
      {
         @Override
         public int compare(ModuleIdentifier o1, ModuleIdentifier o2)
         {
            // Both objects must have a version with a revision marker as
            // this is the condition for being added to the versions set.
            int idx1 = o1.getVersion().lastIndexOf(REVISION_MARKER);
            int idx2 = o2.getVersion().lastIndexOf(REVISION_MARKER);
            Integer i1 = Integer.parseInt(o1.getVersion().substring(idx1 + REVISION_MARKER.length()));
            Integer i2 = Integer.parseInt(o2.getVersion().substring(idx2 + REVISION_MARKER.length()));
            
            // return the highest number first
            return -i1.compareTo(i2);
         }
      });

      for (ModuleIdentifier id : modules.keySet())
      {
         if (id.getGroup().equals(identifier.getGroup()) == false)
            continue;
         
         if (id.getArtifact().equals(identifier.getArtifact()) == false)
            continue;
         
         if (id.getVersion().contains(REVISION_MARKER))
            versions.add(id);
      }

      if (versions.size() == 0)
         return null;

      return modules.get(versions.iterator().next());
   }

   @Override
   public Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      ModuleHolder holder = getModuleHolder(identifier);
      if (holder == null)
         throw new IllegalStateException("Cannot find module: " + identifier);
      
      Module module = holder.getModule();
      if (module == null)
      {
         module = super.loadModule(identifier);
         holder.setModule(module);
      }
      return module;
   }

   /**
    * Create a {@link ModuleSpec} from the given resolver module definition
    */
   public ModuleSpec createModuleSpec(final XModule resModule, VirtualFile rootFile)
   {
      ModuleSpec moduleSpec = resModule.getAttachment(ModuleSpec.class);
      if (moduleSpec == null)
      {
         ModuleIdentifier identifier = getModuleIdentifier(resModule);
         ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);

         // Add the framework module as required dependency
         DependencySpec.Builder depBuilder = specBuilder.addDependency(frameworkIdentifier);
         depBuilder.setExport(false); // no re-export

         // Add the exported packages as paths
         List<String> exportPaths = new ArrayList<String>();
         for (XPackageCapability cap : resModule.getPackageCapabilities())
            exportPaths.add(getPathFromPackageName(cap.getName()));

         // Add the bundle's {@link ResourceLoader}
         specBuilder.addRoot("[TODO] What Value?", new VirtualFileResourceLoader(rootFile, exportPaths));

         // Map the dependency builder for (the likely) case that the same exporter is choosen for multiple wires
         Map<XModule, DependencySpec.Builder> depBuilders = new HashMap<XModule, DependencySpec.Builder>();
         depBuilders.put(bundleManager.getSystemBundle().getResolverModule(), depBuilder);
         
         // For every {@link XWire} add a dependency on the exporter
         // The resolver module will have wires when this method is 
         // called as a result of the resolver callback
         List<XWire> wires = resModule.getWires();
         if (wires != null)
         {
            for (XWire wire : wires)
            {
               XRequirement req = wire.getRequirement();
               XModule importer = wire.getImporter();
               XModule exporter = wire.getExporter();
               if (exporter == importer)
                  continue;

               // Get or create the dependency builder for the exporter
               ModuleIdentifier exporterId = getModuleIdentifier(exporter);
               depBuilder = depBuilders.get(exporter);
               if (depBuilder == null)
               {
                  depBuilder = specBuilder.addDependency(exporterId);
                  depBuilders.put(exporter, depBuilder);
               }
               
               // Dependency for Import-Package
               if (req instanceof XPackageRequirement)
               {
                  String path = getPathFromPackageName(req.getName());
                  depBuilder.addExportInclude(path);
                  depBuilder.setExport(false); // no re-export
                  continue;
               }

               // Dependency for Require-Bundle
               if (req instanceof XRequireBundleRequirement)
               {
                  XRequireBundleRequirement bndreq = (XRequireBundleRequirement)req;
                  boolean reexport = Constants.VISIBILITY_REEXPORT.equals(bndreq.getVisibility());
                  depBuilder.setExport(reexport);
                  continue;
               }
            }
         }

         ModuleClassLoaderFactory loaderFactory = new ModuleClassLoaderFactory()
         {
            @Override
            public ModuleClassLoader getModuleClassLoader(Module module, ModuleSpec moduleSpec)
            {
               return new OSGiModuleClassLoader(bundleManager, resModule, module, moduleSpec);
            }
         };
         specBuilder.setClassLoaderFactory(loaderFactory);
         moduleSpec = specBuilder.create();
      }

      log.debug("Created " + moduleSpec.toLongString(new StringBuffer()));
      AbstractBundle bundleState = (AbstractBundle)resModule.getAttachment(Bundle.class);
      modules.put(moduleSpec.getIdentifier(), new ModuleHolder(bundleState, moduleSpec));
      return moduleSpec;
   }

   /**
    * Create the {@link Framework} module from the give resolver module definition.
    */
   public ModuleSpec createFrameworkModule(final XModule resModule)
   {
      if (frameworkIdentifier != null)
         throw new IllegalStateException("Framework module already created");

      frameworkIdentifier = getModuleIdentifier(resModule, 0);
      ModuleSpec.Builder builder = ModuleSpec.build(frameworkIdentifier);

      // The Framework ModuleSpec has no associated {@link ModuleContentLoader}
      // Instead, we use a {@link ModuleClassLoaderFactory} that reports the 
      // exported paths through {@link ModuleClassLoader#getExportedPaths()} 
      ModuleClassLoaderFactory loaderFactory = new ModuleClassLoaderFactory()
      {
         @Override
         public ModuleClassLoader getModuleClassLoader(Module module, ModuleSpec moduleSpec)
         {
            return new FrameworkModuleClassLoader(bundleManager, resModule, module);
         }
      };
      builder.setClassLoaderFactory(loaderFactory);
      ModuleSpec frameworkSpec = builder.create();

      AbstractBundle bundleState = (AbstractBundle)resModule.getAttachment(Bundle.class);
      modules.put(frameworkIdentifier, new ModuleHolder(bundleState, frameworkSpec));
      return frameworkSpec;
   }

   private String getPathFromPackageName(String packageName)
   {
      return packageName.replace('.', File.separatorChar);
   }

   public boolean removeModule(ModuleIdentifier identifier)
   {
      // The module should remove automatically from the ModuleLoader
      // through Garbage Collection as it uses weak references.
      return modules.remove(identifier) != null;
   }

   /**
    * A holder for the {@link ModuleSpec}  @{link Module} tuple
    */
   protected static class ModuleHolder
   {
      private AbstractBundle bundle;
      private ModuleSpec moduleSpec;
      private Module module;

      public ModuleHolder(AbstractBundle bundle, ModuleSpec moduleSpec)
      {
         if (bundle == null)
            throw new IllegalArgumentException("Null bundle");
         if (moduleSpec == null)
            throw new IllegalArgumentException("Null moduleSpec");
         this.bundle = bundle;
         this.moduleSpec = moduleSpec;
      }

      AbstractBundle getBundle()
      {
         return bundle;
      }

      ModuleSpec getModuleSpec()
      {
         return moduleSpec;
      }

      Module getModule()
      {
         return module;
      }

      void setModule(Module module)
      {
         this.module = module;
      }
   }
}
