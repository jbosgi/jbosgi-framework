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

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.PathFilter;
import org.jboss.modules.PathFilters;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.AbstractRevision;
import org.jboss.osgi.framework.bundle.AbstractUserBundle;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.BundleManager.IntegrationMode;
import org.jboss.osgi.framework.bundle.FragmentRevision;
import org.jboss.osgi.framework.bundle.HostBundle;
import org.jboss.osgi.framework.bundle.OSGiModuleLoader;
import org.jboss.osgi.framework.loading.FragmentLocalLoader;
import org.jboss.osgi.framework.loading.FrameworkLocalLoader;
import org.jboss.osgi.framework.loading.JBossLoggingModuleLogger;
import org.jboss.osgi.framework.loading.ModuleClassLoaderExt;
import org.jboss.osgi.framework.loading.NativeLibraryProvider;
import org.jboss.osgi.framework.loading.NativeResourceLoader;
import org.jboss.osgi.framework.loading.VirtualFileResourceLoader;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.internal.NativeCodePluginImpl.BundleNativeLibraryProvider;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequireBundleRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.application.Framework;
import org.osgi.framework.Bundle;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
public class ModuleManagerPluginImpl extends AbstractPlugin implements ModuleManagerPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(ModuleManagerPluginImpl.class);

   // The module loader for the OSGi layer
   private OSGiModuleLoader moduleLoader;
   // The module spec creation hook
   private ModuleSpecCreationHook creationHook;

   public ModuleManagerPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      creationHook = new ModuleSpecCreationHook()
      {
         @Override
         public ModuleSpec create(ModuleSpec.Builder specBuilder)
         {
            return specBuilder.create();
         }
      };
   }

   @Override
   public void initPlugin()
   {
      // Setup the OSGiModuleLoader
      moduleLoader = new OSGiModuleLoader(getBundleManager());

      // Setup the Module system when running STANDALONE
      if (getBundleManager().getIntegrationMode() == IntegrationMode.STANDALONE)
      {
         // Set the {@link ModuleLogger}
         Module.setModuleLogger(new JBossLoggingModuleLogger(Logger.getLogger(ModuleClassLoader.class)));
      }
   }


   @Override
   public void destroyPlugin()
   {
      // Destroy the OSGiModuleLoader
      moduleLoader = null;
   }

   @Override
   public void setModuleSpecCreationHook(ModuleSpecCreationHook hook)
   {
      if (moduleLoader != null)
         throw new IllegalStateException("Cannot set ModuleSpecCreationHook after Framework module was created");
      creationHook = hook;
   }

   @Override
   public ModuleLoader getModuleLoader()
   {
      return moduleLoader;
   }

   @Override
   public ModuleIdentifier getModuleIdentifier(XModule resModule)
   {
      if (resModule.isFragment())
         throw new IllegalArgumentException("A fragment is not a module");

      ModuleIdentifier id = resModule.getAttachment(ModuleIdentifier.class);
      if (id != null)
         return id;

      Module module = resModule.getAttachment(Module.class);
      ModuleIdentifier identifier = (module != null ? module.getIdentifier() : null);
      if (identifier == null)
      {
         XModuleIdentity moduleId = resModule.getModuleId();
         String name = Constants.JBOSGI_PREFIX + "." + moduleId.getName();
         if (name.equals(FRAMEWORK_IDENTIFIER.getName()))
            identifier = FRAMEWORK_IDENTIFIER;

         if (identifier == null)
         {
            String slot = moduleId.getVersion().toString();
            int revision = moduleId.getRevision();
            if (revision > 0)
               slot += "-rev" + revision;

            identifier = ModuleIdentifier.create(name, slot);
         }
      }

      resModule.addAttachment(ModuleIdentifier.class, identifier);
      return identifier;
   }

   @Override
   public Set<ModuleIdentifier> getModuleIdentifiers()
   {
      return moduleLoader.getModuleIdentifiers();
   }

   @Override
   public Module getModule(ModuleIdentifier identifier)
   {
      return moduleLoader.getModule(identifier);
   }

   @Override
   public AbstractRevision getBundleRevision(ModuleIdentifier identifier)
   {
      return moduleLoader.getBundleRevision(identifier);
   }

   @Override
   public AbstractBundle getBundleState(ModuleIdentifier identifier)
   {
      return moduleLoader.getBundleState(identifier);
   }

   @Override
   public ModuleIdentifier addModule(final XModule resModule)
   {
      if (resModule == null)
         throw new IllegalArgumentException("Null module");

      ModuleIdentifier identifier;
      Module module = resModule.getAttachment(Module.class);
      if (module == null)
      {
         if (resModule.getModuleId().getName().equals("system.bundle"))
         {
            ModuleSpec moduleSpec = createFrameworkSpec(resModule);
            identifier = moduleSpec.getModuleIdentifier();
         }
         else
         {
            // Get the root virtual file
            Bundle bundle = resModule.getAttachment(Bundle.class);
            HostBundle bundleState = HostBundle.assertBundleState(bundle);
            List<VirtualFile> contentRoots = bundleState.getContentRoots();

            ModuleSpec moduleSpec = createModuleSpec(resModule, contentRoots);
            identifier = moduleSpec.getModuleIdentifier();
         }
      }
      else
      {
         AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
         moduleLoader.addModule(bundleRev, module);
         identifier = module.getIdentifier();
      }
      return identifier;
   }

   /**
    * Create the {@link Framework} module from the give resolver module definition.
    */
   private ModuleSpec createFrameworkSpec(final XModule resModule)
   {
      ModuleSpec.Builder specBuilder = ModuleSpec.build(FRAMEWORK_IDENTIFIER);

      FrameworkLocalLoader frameworkLoader = new FrameworkLocalLoader(getBundleManager());
      specBuilder.addDependency(DependencySpec.createLocalDependencySpec(frameworkLoader, frameworkLoader.getExportedPaths(), true));

      ModuleSpec frameworkSpec = creationHook.create(specBuilder);
      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      moduleLoader.addModule(bundleRev, frameworkSpec);

      return frameworkSpec;
   }

   /**
    * Create a {@link ModuleSpec} from the given resolver module definition
    */
   private ModuleSpec createModuleSpec(final XModule resModule, List<VirtualFile> contentRoots)
   {
      ModuleSpec moduleSpec = resModule.getAttachment(ModuleSpec.class);
      if (moduleSpec == null)
      {
         ModuleIdentifier identifier = getModuleIdentifier(resModule);
         ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);

         // Add the framework module as the first required dependency
         DependencySpec frameworkDependency = DependencySpec.createModuleDependencySpec(FRAMEWORK_IDENTIFIER, true);
         specBuilder.addDependency(frameworkDependency);

         // Map the dependency builder for (the likely) case that the same exporter is choosen for multiple wires
         Map<XModule, DependencyHolder> specHolderMap = new LinkedHashMap<XModule, DependencyHolder>();

         // In case there are no wires, there may still be dependencies due to attached fragments
         HostBundle hostBundle = resModule.getAttachment(HostBundle.class);
         if (resModule.getWires().isEmpty() && hostBundle != null)
         {
            List<FragmentRevision> fragRevs = hostBundle.getCurrentRevision().getAttachedFragments();
            for (FragmentRevision fragRev : fragRevs)
            {
               // Process the fragment wires. This would take care of Package-Imports and Require-Bundle defined on the fragment
               List<XWire> fragWires = fragRev.getResolverModule().getWires();
               processModuleWires(fragWires, specHolderMap);

               // Create a fragment {@link LocalLoader} and add a dependency on it
               FragmentLocalLoader localLoader = new FragmentLocalLoader(fragRev);
               specHolderMap.put(fragRev.getResolverModule(), new LocalDependencyHolder(localLoader, localLoader.getPaths()));
            }
         }

         // For every {@link XWire} add a dependency on the exporter
         processModuleWires(resModule.getWires(), specHolderMap);

         // Add the dependencies
         for (DependencyHolder aux : specHolderMap.values())
            specBuilder.addDependency(aux.create());

         // Add a local dependency for the local bundle content
         for (VirtualFile contentRoot : contentRoots)
            specBuilder.addResourceRoot(new VirtualFileResourceLoader(contentRoot));

         specBuilder.addDependency(DependencySpec.createLocalDependencySpec());

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

         specBuilder.setFallbackLoader(new ModuleClassLoaderExt(getBundleManager(), identifier));

         // Build the ModuleSpec
         moduleSpec = creationHook.create(specBuilder);
      }

      AbstractRevision bundleRev = resModule.getAttachment(AbstractRevision.class);
      moduleLoader.addModule(bundleRev, moduleSpec);
      return moduleSpec;
   }

   private void processModuleWires(List<XWire> wires, Map<XModule, DependencyHolder> depBuilderMap)
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
         if (exporterId.equals(FRAMEWORK_IDENTIFIER))
            continue;

         // Dependency for Import-Package
         if (req instanceof XPackageRequirement)
         {
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            holder.addImportPath(VFSUtils.getPathFromPackageName(req.getName()));
            continue;
         }

         // Dependency for Require-Bundle
         if (req instanceof XRequireBundleRequirement)
         {
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            XRequireBundleRequirement bndreq = (XRequireBundleRequirement)req;
            boolean reexport = Constants.VISIBILITY_REEXPORT.equals(bndreq.getVisibility());
            if (reexport == true)
               holder.setExportFilter(PathFilters.acceptAll());

            continue;
         }
      }
   }

   // Get or create the dependency builder for the exporter
   private ModuleDependencyHolder getDependencyHolder(Map<XModule, DependencyHolder> depBuilderMap, XModule exporter)
   {
      ModuleIdentifier exporterId = getModuleIdentifier(exporter);
      ModuleDependencyHolder holder = (ModuleDependencyHolder)depBuilderMap.get(exporter);
      if (holder == null)
      {
         holder = new ModuleDependencyHolder(exporterId);
         depBuilderMap.put(exporter, holder);
      }
      return holder;
   }

   @Override
   public Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      return moduleLoader.loadModule(identifier);
   }

   @Override
   public Module removeModule(ModuleIdentifier identifier)
   {
      return moduleLoader.removeModule(identifier);
   }

   abstract class DependencyHolder
   {
      private DependencySpec dependencySpec;

      DependencySpec create()
      {
         assertNotCreated();
         return dependencySpec = createInternal();
      }

      abstract DependencySpec createInternal();

      void assertNotCreated()
      {
         if (dependencySpec != null)
            throw new IllegalStateException("DependencySpec already created");
      }
   }

   class ModuleDependencyHolder extends DependencyHolder
   {
      private ModuleIdentifier identifier;
      private Set<String> importPaths;
      private PathFilter exportFilter = PathFilters.rejectAll();
      private boolean optional;

      ModuleDependencyHolder(ModuleIdentifier identifier)
      {
         this.identifier = identifier;
      }

      void addImportPath(String path)
      {
         assertNotCreated();
         if (importPaths == null)
            importPaths = new HashSet<String>();

         importPaths.add(path);
      }

      void setExportFilter(PathFilter exportFilter)
      {
         assertNotCreated();
         this.exportFilter = exportFilter;
      }

      void setOptional(boolean optional)
      {
         assertNotCreated();
         this.optional = optional;
      }

      DependencySpec createInternal()
      {
         PathFilter importFilter = importPaths != null ? PathFilters.in(importPaths) : PathFilters.acceptAll();
         return DependencySpec.createModuleDependencySpec(importFilter, exportFilter, moduleLoader, identifier, optional);
      }

   }

   class LocalDependencyHolder extends DependencyHolder
   {
      private LocalLoader localLoader;
      private Set<String> loaderPaths;

      LocalDependencyHolder(LocalLoader localLoader, Set<String> loaderPaths)
      {
         this.localLoader = localLoader;
         this.loaderPaths = loaderPaths;
      }

      DependencySpec createInternal()
      {
         PathFilter importFilter = PathFilters.acceptAll();
         PathFilter exportFilter = PathFilters.in(loaderPaths);
         return DependencySpec.createLocalDependencySpec(importFilter, exportFilter, localLoader, loaderPaths);
      }
   }
}