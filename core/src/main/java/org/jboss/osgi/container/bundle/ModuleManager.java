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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.logging.Logger;
import org.jboss.modules.AssertionSetting;
import org.jboss.modules.LocalDependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.PathFilters;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.container.loading.FragmentLocalLoader;
import org.jboss.osgi.container.loading.FrameworkLocalLoader;
import org.jboss.osgi.container.loading.JBossLoggingModuleLogger;
import org.jboss.osgi.container.loading.ModuleClassLoaderExt;
import org.jboss.osgi.container.loading.NativeLibraryProvider;
import org.jboss.osgi.container.loading.NativeResourceLoader;
import org.jboss.osgi.container.loading.VirtualFileResourceLoader;
import org.jboss.osgi.container.plugin.internal.NativeCodePluginImpl.BundleNativeLibraryProvider;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
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

   // The bundle manager
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

      // Set the {@link ModuleClassLoaderFactory}
//      Module.setModuleClassLoaderFactory(new ModuleClassLoaderFactory()
//      {
//         @Override
//         public ModuleClassLoader getModuleClassLoader(Module module, AssertionSetting assertionSetting, Collection<ResourceLoader> resourceLoaders)
//         {
//            return new ModuleClassLoaderExt(module, assertionSetting, resourceLoaders);
//         }
//      });

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
      if (resModule.isFragment())
         throw new IllegalArgumentException("A fragment is not a module");

      ModuleIdentifier id = resModule.getAttachment(ModuleIdentifier.class);
      if (id != null)
         return id;

      ModuleSpec moduleSpec = resModule.getAttachment(ModuleSpec.class);
      if (moduleSpec != null)
         return moduleSpec.getModuleIdentifier();

      AbstractRevision bundleRevision = resModule.getAttachment(AbstractRevision.class);
      if (bundleRevision == null)
         throw new IllegalStateException("Cannot obtain revision from: " + resModule);

      return getModuleIdentifier(resModule, bundleRevision.getRevisionCount());
   }

   // [TODO] Remove the revision parameter. The identity of an XModule is the revision.
   private static ModuleIdentifier getModuleIdentifier(XModule resModule, int revision)
   {
      String name = resModule.getName();
      Version version = resModule.getVersion();
      long moduleId = resModule.getModuleId();

      // Modules can define their dependencies on bundles by prefixing the artifact name with 'xservice'
      // For ordinary modules we encode the bundleId in the group name to make sure the identifier is unique.
      // When the underlying ModuleLoader supports unloading of modules we can hopefully create stable module identifiers
      // that do not contain the bundleId, which is inherently unstable accross framework restarts.

      // [TODO] Remove symbolic name prefix hack
      if (moduleId > 0 && name.startsWith("xservice") == false)
         name += "[" + moduleId + "]";

      ModuleIdentifier identifier = ModuleIdentifier.create(name, version + REVISION_MARKER + revision);
      resModule.addAttachment(ModuleIdentifier.class, identifier);
      return identifier;
   }

   public BundleManager getBundleManager()
   {
      return bundleManager;
   }

   /**
    * Get the bundle revision from a module identifier
    */
   public AbstractRevision getBundleRevision(ModuleIdentifier identifier)
   {
      ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getBundleRevision() : null;
   }

   /**
    * Get the bundle from a module identifier
    */
   public AbstractBundle getBundleState(ModuleIdentifier identifier)
   {
      AbstractRevision bundleRev = getBundleRevision(identifier);
      return bundleRev != null ? bundleRev.getBundleState() : null;
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
   // dependency declared in a modules.xml file. In that case we need to search for the module and return
   // the one with the highest revision number.
   private ModuleHolder getModuleFromUnrevisionedIdentifier(ModuleIdentifier identifier)
   {
      if (identifier.getSlot() != null && identifier.getSlot().contains(REVISION_MARKER))
         // There is a revision identifier, so don't search.
         return null;

      Set<ModuleIdentifier> versions = new TreeSet<ModuleIdentifier>(new Comparator<ModuleIdentifier>()
      {
         @Override
         public int compare(ModuleIdentifier o1, ModuleIdentifier o2)
         {
            // Both objects must have a version with a revision marker as
            // this is the condition for being added to the versions set.
            int idx1 = o1.getSlot().lastIndexOf(REVISION_MARKER);
            int idx2 = o2.getSlot().lastIndexOf(REVISION_MARKER);
            Integer i1 = Integer.parseInt(o1.getSlot().substring(idx1 + REVISION_MARKER.length()));
            Integer i2 = Integer.parseInt(o2.getSlot().substring(idx2 + REVISION_MARKER.length()));

            // return the highest number first
            return -i1.compareTo(i2);
         }
      });

      for (ModuleIdentifier id : modules.keySet())
      {

         if (id.getName().equals(identifier.getName()) == false)
            continue;

         if (id.getSlot().contains(REVISION_MARKER))
            versions.add(id);
      }

      if (versions.size() == 0)
         return null;

      return modules.get(versions.iterator().next());
   }

   @Override
   public Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      ModuleHolder holder = getModuleHolder(identifier);
      if (holder == null)
         throw new IllegalStateException("Cannot find module: " + identifier);

      Module module = holder.getModule();
      if (module == null)
      {
         module = super.preloadModule(identifier);
         holder.setModule(module);
      }
      return module;
   }

   /**
    * Create the {@link Framework} module from the give resolver module definition.
    */
   public ModuleSpec createFrameworkSpec(final XModule resModule)
   {
      if (frameworkIdentifier != null)
         throw new IllegalStateException("Framework module already created");

      frameworkIdentifier = getModuleIdentifier(resModule, 0);
      ModuleSpec.Builder builder = ModuleSpec.build(frameworkIdentifier);

      FrameworkLocalLoader frameworkLoader = new FrameworkLocalLoader(bundleManager, resModule);
      LocalDependencySpec.Builder depBuilder = LocalDependencySpec.build(frameworkLoader, frameworkLoader.getExportedPaths());
      depBuilder.setImportFilter(PathFilters.acceptAll()); // [REVIEW] Review why all imports must be accepted
      depBuilder.setExportFilter(PathFilters.acceptAll());

      builder.addLocalDependency(depBuilder.create());
      ModuleSpec frameworkSpec = builder.create();

      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      modules.put(frameworkIdentifier, new ModuleHolder(bundleRev, frameworkSpec));
      return frameworkSpec;
   }

   /**
    * Create a {@link ModuleSpec} from the given resolver module definition
    */
   public ModuleSpec createModuleSpec(final XModule resModule, List<VirtualFile> contentRoots)
   {
      ModuleSpec moduleSpec = resModule.getAttachment(ModuleSpec.class);
      if (moduleSpec == null)
      {
         ModuleIdentifier identifier = getModuleIdentifier(resModule);
         ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);

         // Add the framework module as required dependency
         ModuleDependencySpec.Builder frameworkDependencyBuilder = ModuleDependencySpec.build(frameworkIdentifier);
         // [REVIEW] Review why all imports must be accepted
         frameworkDependencyBuilder.setExportFilter(PathFilters.acceptAll());
         frameworkDependencyBuilder.setImportFilter(PathFilters.acceptAll());
         specBuilder.addModuleDependency(frameworkDependencyBuilder.create());

         // Map the dependency builder for (the likely) case that the same exporter is choosen for multiple wires
         Map<XModule, DependencyBuildlerHolder> depBuilderMap = new LinkedHashMap<XModule, DependencyBuildlerHolder>();

         // In case there are no wires, there may still be dependencies due to attached fragments
         HostBundle hostBundle = resModule.getAttachment(HostBundle.class);
         if (resModule.getWires().isEmpty() && hostBundle != null)
         {
            List<FragmentRevision> fragRevs = hostBundle.getCurrentRevision().getAttachedFragments();
            for (FragmentRevision fragRev : fragRevs)
            {
               // Process the fragment wires. This would take care of Package-Imports and Require-Bundle defined on the fragment
               List<XWire> fragWires = fragRev.getResolverModule().getWires();
               processModuleWires(fragWires, depBuilderMap);

               // Create a fragment {@link LocalLoader} and add a dependency on it
               FragmentLocalLoader localLoader = new FragmentLocalLoader(fragRev);
               LocalDependencySpec.Builder depBuilder = LocalDependencySpec.build(localLoader, localLoader.getPaths());
               // [REVIEW] dependent filter settings
               depBuilder.setImportFilter(PathFilters.acceptAll());
               depBuilder.setExportFilter(PathFilters.acceptAll());

               depBuilderMap.put(fragRev.getResolverModule(), new DependencyBuildlerHolder(depBuilder));
            }
         }

         // For every {@link XWire} add a dependency on the exporter
         processModuleWires(resModule.getWires(), depBuilderMap);

         // Add the dependencies
         for (DependencyBuildlerHolder aux : depBuilderMap.values())
            aux.addDependency(specBuilder);

         // Add a local dependency for the local bundle content
         for (VirtualFile contentRoot : contentRoots)
            specBuilder.addResourceRoot(new VirtualFileResourceLoader(contentRoot));
         specBuilder.addLocalDependency();


         // Native - Hack
         Bundle bundle = resModule.getAttachment(Bundle.class);
         AbstractUserBundle bundleState = AbstractUserBundle.assertBundleState(bundle);
         Deployment deployment = bundleState.getDeployment();

         NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
         if (libMetaData != null)
         {
             NativeResourceLoader nativeLoader = new NativeResourceLoader();
             // Add the native library mappings to the OSGiClassLoaderPolicy
             for (NativeLibrary library : libMetaData.getNativeLibraries())
             {
                String libpath = library.getLibraryPath();
                String libfile = new File(libpath).getName();
                String libname = libfile.substring(0, libfile.lastIndexOf('.'));

                // Add the library provider to the policy
                NativeLibraryProvider libProvider = new BundleNativeLibraryProvider(bundleState, libname, libpath);
                nativeLoader.addNativeLibrary(libProvider);

                // [TODO] why does the TCK use 'Native' to mean 'libNative' ?
                if (libname.startsWith("lib"))
                {
                   libname = libname.substring(3);
                   libProvider = new BundleNativeLibraryProvider(bundleState, libname, libpath);
                   nativeLoader.addNativeLibrary(libProvider);
                }
             }

             specBuilder.addResourceRoot(nativeLoader);
         }

         specBuilder.setFallbackLoader(new ModuleClassLoaderExt(identifier, this));

         // Build the ModuleSpec
         moduleSpec = specBuilder.create();
      }

      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      modules.put(moduleSpec.getModuleIdentifier(), new ModuleHolder(bundleRev, moduleSpec));
      return moduleSpec;
   }

   private void processModuleWires(List<XWire> wires, Map<XModule, DependencyBuildlerHolder> depBuilderMap)
   {
      for (XWire wire : wires)
      {
         XRequirement req = wire.getRequirement();
         XModule importer = wire.getImporter();
         XModule exporter = wire.getExporter();
         if (exporter == importer)
            continue;

         // Dependency for Import-Package
         if (req instanceof XPackageRequirement)
         {
            DependencyBuildlerHolder holder = getDependencyHolder(depBuilderMap, exporter);
            holder.addImportPath(getPathFromPackageName(req.getName()));
            continue;
         }

         // Dependency for Require-Bundle
         if (req instanceof XRequireBundleRequirement)
         {
            DependencyBuildlerHolder holder = getDependencyHolder(depBuilderMap, exporter);
            XRequireBundleRequirement bndreq = (XRequireBundleRequirement)req;
            boolean reexport = Constants.VISIBILITY_REEXPORT.equals(bndreq.getVisibility());
            if (reexport == true)
            {
               ModuleDependencySpec.Builder depBuilder = holder.moduleDependencyBuilder;
               // [REVIEW] dependent filter settings
               depBuilder.setImportFilter(PathFilters.acceptAll());
               depBuilder.setExportFilter(PathFilters.acceptAll());
            }
            continue;
         }
      }
   }

   // Get or create the dependency builder for the exporter
   private DependencyBuildlerHolder getDependencyHolder(Map<XModule, DependencyBuildlerHolder> depBuilderMap, XModule exporter)
   {
      ModuleIdentifier exporterId = getModuleIdentifier(exporter);
      DependencyBuildlerHolder holder = depBuilderMap.get(exporter);
      if (holder == null)
      {
         holder = new DependencyBuildlerHolder(ModuleDependencySpec.build(exporterId));
         depBuilderMap.put(exporter, holder);
      }
      return holder;
   }

   public Module removeModule(ModuleIdentifier identifier)
   {
      // The module should remove automatically from the ModuleLoader
      // through Garbage Collection as it uses weak references.
      ModuleHolder moduleHolder = modules.remove(identifier);
      return (moduleHolder != null ? moduleHolder.module : null);
   }

   public static String getPathFromClassName(final String className)
   {
      int idx = className.lastIndexOf('.');
      return idx > -1 ? getPathFromPackageName(className.substring(0, idx)) : "";
   }

   public static String getPathFromPackageName(String packageName)
   {
      return packageName.replace('.', File.separatorChar);
   }

   static class DependencyBuildlerHolder
   {
      private LocalDependencySpec.Builder localDependencyBuilder;
      private ModuleDependencySpec.Builder moduleDependencyBuilder;
      private Set<String> importPaths;

      DependencyBuildlerHolder(LocalDependencySpec.Builder builder)
      {
         this.localDependencyBuilder = builder;
      }

      DependencyBuildlerHolder(ModuleDependencySpec.Builder builder)
      {
         this.moduleDependencyBuilder = builder;
      }

      void addImportPath(String path)
      {
         if (importPaths == null)
            importPaths = new HashSet<String>();

         importPaths.add(path);
      }

      void addDependency(ModuleSpec.Builder specBuilder)
      {
         if (moduleDependencyBuilder != null)
         {
            if (importPaths != null)
            {
               // [REVIEW] dependent filter settings
               moduleDependencyBuilder.setImportFilter(PathFilters.in(importPaths));
               moduleDependencyBuilder.setExportFilter(PathFilters.in(importPaths));
            }
            specBuilder.addModuleDependency(moduleDependencyBuilder.create());
         }

         if (localDependencyBuilder != null)
         {
            specBuilder.addLocalDependency(localDependencyBuilder.create());
         }
      }
   }

   // A holder for the {@link ModuleSpec}  @{link Module} tuple
   public static class ModuleHolder
   {
      private final AbstractRevision bundleRev;
      private final ModuleSpec moduleSpec;
      private Module module;

      public ModuleHolder(AbstractRevision bundleRev, ModuleSpec moduleSpec)
      {
         if (bundleRev == null)
            throw new IllegalArgumentException("Null bundleRev");
         if (moduleSpec == null)
            throw new IllegalArgumentException("Null moduleSpec");
         this.bundleRev = bundleRev;
         this.moduleSpec = moduleSpec;
      }

      AbstractRevision getBundleRevision()
      {
         return bundleRev;
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
