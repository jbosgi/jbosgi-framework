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
package org.jboss.osgi.framework.bundle;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ClassifyingModuleLoader;
import org.jboss.modules.LocalDependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.PathFilters;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.bundle.BundleManager.IntegrationMode;
import org.jboss.osgi.framework.loading.FragmentLocalLoader;
import org.jboss.osgi.framework.loading.FrameworkLocalLoader;
import org.jboss.osgi.framework.loading.JBossLoggingModuleLogger;
import org.jboss.osgi.framework.loading.ModuleClassLoaderExt;
import org.jboss.osgi.framework.loading.NativeLibraryProvider;
import org.jboss.osgi.framework.loading.NativeResourceLoader;
import org.jboss.osgi.framework.loading.VirtualFileResourceLoader;
import org.jboss.osgi.framework.plugin.internal.NativeCodePluginImpl.BundleNativeLibraryProvider;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequireBundleRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.application.Framework;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * Build the {@link ModuleSpec} from {@link OSGiMetaData}.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class ModuleManager
{
   // The property that defines a comma seperated list of system module identifiers
   public static final String PROP_JBOSS_OSGI_SYSTEM_MODULES = "org.jboss.osgi.system.modules";

   // The module prefix for modules managed by the OSGi layer
   private static final String MODULE_PREFIX = "jbosgi";

   // The bundle manager
   private BundleManager bundleManager;
   // The framework module identifier
   private ModuleIdentifier frameworkIdentifier;
   // The module loader for the OSGi layer
   private OSGiModuleLoader moduleLoader;

   public ModuleManager(BundleManager bundleManager)
   {
      if (bundleManager == null)
         throw new IllegalArgumentException("Null bundleManager");

      this.bundleManager = bundleManager;
      this.moduleLoader = new OSGiModuleLoader();

      // Set the {@link ModuleLogger}
      Module.setModuleLogger(new JBossLoggingModuleLogger(Logger.getLogger(ModuleClassLoader.class)));

      // Set the ModuleLoaderSelector to a {@link ClassifyingModuleLoader} that delegates
      // all indetifiers the start with 'jbosgi' to the {@link OSGiModuleLoader}
      final ModuleLoader defaultLoader = Module.getCurrentLoader();
      Module.setModuleLoaderSelector(new ModuleLoaderSelector()
      {
         @Override
         public ModuleLoader getCurrentLoader()
         {
            Map<String, ModuleLoader> delegates = new HashMap<String, ModuleLoader>();
            delegates.put(MODULE_PREFIX, moduleLoader);
            return new ClassifyingModuleLoader(delegates, defaultLoader);
         }
      });
   }

   /**
    * Return the module identifier for a given XModule.
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

      XModuleIdentity moduleId = resModule.getModuleId();
      String name = MODULE_PREFIX + "." + moduleId.getName() + "-" + moduleId.getVersion();
      ModuleIdentifier identifier = ModuleIdentifier.create(name, moduleId.getRevision());
      resModule.addAttachment(ModuleIdentifier.class, identifier);
      return identifier;
   }

   public BundleManager getBundleManager()
   {
      return bundleManager;
   }

   public ModuleLoader getModuleLoader()
   {
      return moduleLoader;
   }

   public AbstractRevision getBundleRevision(ModuleIdentifier identifier)
   {
      return moduleLoader.getBundleRevision(identifier);
   }

   public AbstractBundle getBundleState(ModuleIdentifier identifier)
   {
      return moduleLoader.getBundleState(identifier);
   }

   /**
    * Get the set of registered module idetifiers
    */
   public Set<ModuleIdentifier> getModuleIdentifiers()
   {
      return moduleLoader.getModuleIdentifiers();
   }

   /**
    * Get the module for the given identifier
    * @return The previously loaded module or null
    */
   public Module getModule(ModuleIdentifier identifier)
   {
      return moduleLoader.getModule(identifier);
   }

   /**
    * Create the {@link Framework} module from the give resolver module definition.
    */
   public ModuleSpec createFrameworkSpec(final XModule resModule)
   {
      if (frameworkIdentifier != null)
         throw new IllegalStateException("Framework module already created");

      frameworkIdentifier = getModuleIdentifier(resModule);
      ModuleSpec.Builder specBuilder = ModuleSpec.build(frameworkIdentifier);

      FrameworkLocalLoader frameworkLoader = new FrameworkLocalLoader(bundleManager);
      LocalDependencySpec.Builder localDependency = LocalDependencySpec.build(frameworkLoader, frameworkLoader.getExportedPaths());
      localDependency.setImportFilter(PathFilters.acceptAll()); // [TODO] Remove when this becomes the default
      localDependency.setExportFilter(PathFilters.acceptAll());
      specBuilder.addLocalDependency(localDependency.create());

      // When running in AS there are no jars on the system classpath except jboss-modules.jar
      if (bundleManager.getIntegrationMode() == IntegrationMode.CONTAINER)
      {
         String systemModules = (String)bundleManager.getProperty(PROP_JBOSS_OSGI_SYSTEM_MODULES);
         if (systemModules != null)
         {
            for (String moduleid : systemModules.split(","))
            {
               ModuleIdentifier identifier = ModuleIdentifier.create(moduleid.trim());
               ModuleDependencySpec.Builder moduleDependency = ModuleDependencySpec.build(identifier);
               moduleDependency.setExportFilter(PathFilters.acceptAll()); // re-export everything
               specBuilder.addModuleDependency(moduleDependency.create());
            }
         }
      }

      ModuleSpec frameworkSpec = specBuilder.create();
      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      moduleLoader.addModule(bundleRev, frameworkSpec);

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

         // Add the framework module as the first required dependency
         ModuleDependencySpec.Builder frameworkDependency = ModuleDependencySpec.build(frameworkIdentifier);
         specBuilder.addModuleDependency(frameworkDependency.create());

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
               LocalDependencySpec.Builder localDependency = LocalDependencySpec.build(localLoader, localLoader.getPaths());
               localDependency.setImportFilter(PathFilters.acceptAll()); // [TODO] Remove when this becomes the default
               localDependency.setExportFilter(PathFilters.acceptAll());

               depBuilderMap.put(fragRev.getResolverModule(), new DependencyBuildlerHolder(localDependency));
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

         specBuilder.setFallbackLoader(new ModuleClassLoaderExt(bundleManager, identifier));

         // Build the ModuleSpec
         moduleSpec = specBuilder.create();
      }

      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      moduleLoader.addModule(bundleRev, moduleSpec);
      return moduleSpec;
   }

   private void processModuleWires(List<XWire> wires, Map<XModule, DependencyBuildlerHolder> depBuilderMap)
   {
      for (XWire wire : wires)
      {
         XRequirement req = wire.getRequirement();
         XModule importer = wire.getImporter();
         XModule exporter = wire.getExporter();

         // Skip dependencies on the module itself
         if (exporter == importer)
            continue;

         // Skip dependencies on the system module. This is always added as the first module dependency anyway
         // [TODO] Check if the bundle still fails to resolve when it fails to declare an import on 'org.osgi.framework'
         ModuleIdentifier exporterId = getModuleIdentifier(exporter);
         if (exporterId.equals(frameworkIdentifier))
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
               ModuleDependencySpec.Builder moduleDependency = holder.moduleDependencyBuilder;
               moduleDependency.setExportFilter(PathFilters.acceptAll());
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
      return moduleLoader.removeModule(identifier);
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
               moduleDependencyBuilder.setImportFilter(PathFilters.in(importPaths));
            }
            specBuilder.addModuleDependency(moduleDependencyBuilder.create());
         }

         if (localDependencyBuilder != null)
         {
            specBuilder.addLocalDependency(localDependencyBuilder.create());
         }
      }
   }
}
