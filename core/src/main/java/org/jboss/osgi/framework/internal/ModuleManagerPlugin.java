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
package org.jboss.osgi.framework.internal;

import static org.osgi.framework.Constants.SYSTEM_BUNDLE_SYMBOLICNAME;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BundleReferenceClassLoader;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.ModuleLoaderProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequireBundleRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
final class ModuleManagerPlugin extends AbstractPluginService<ModuleManagerPlugin> {

    // Provide logging
    final Logger log = Logger.getLogger(ModuleManagerPlugin.class);

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<SystemPackagesPlugin> injectedSystemPackages = new InjectedValue<SystemPackagesPlugin>();
    private final InjectedValue<Module> injectedSystemModule = new InjectedValue<Module>();
    private final InjectedValue<ModuleLoaderProvider> injectedModuleLoader = new InjectedValue<ModuleLoaderProvider>();

    private Map<ModuleIdentifier, AbstractBundleRevision> modules = new ConcurrentHashMap<ModuleIdentifier, AbstractBundleRevision>();

    static void addService(ServiceTarget serviceTarget) {
        ModuleManagerPlugin service = new ModuleManagerPlugin();
        ServiceBuilder<ModuleManagerPlugin> builder = serviceTarget.addService(InternalServices.MODULE_MANGER_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(Services.MODULE_LOADER_PROVIDER, ModuleLoaderProvider.class, service.injectedModuleLoader);
        builder.addDependency(InternalServices.SYSTEM_PACKAGES_PLUGIN, SystemPackagesPlugin.class, service.injectedSystemPackages);
        builder.addDependency(Services.SYSTEM_MODULE_PROVIDER, Module.class, service.injectedSystemModule);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private ModuleManagerPlugin() {
    }

    @Override
    public ModuleManagerPlugin getValue() {
        return this;
    }

    ModuleLoaderProvider getModuleLoaderIntegration() {
        return injectedModuleLoader.getValue();
    }

    ModuleLoader getModuleLoader() {
        return getModuleLoaderIntegration().getModuleLoader();
    }

    ModuleIdentifier getModuleIdentifier(XModule resModule) {
        if (resModule == null)
            throw new IllegalArgumentException("Null resModule");
        if (resModule.isFragment())
            throw new IllegalArgumentException("A fragment is not a module");

        ModuleIdentifier identifier = resModule.getAttachment(ModuleIdentifier.class);
        if (identifier != null)
            return identifier;

        XModuleIdentity moduleId = resModule.getModuleId();
        Module module = resModule.getAttachment(Module.class);
        if (module != null) {
            identifier = module.getIdentifier();
        }

        if (identifier == null && SYSTEM_BUNDLE_SYMBOLICNAME.equals(moduleId.getName())) {
            identifier = getFrameworkModule().getIdentifier();
        }

        if (identifier == null) {
            identifier = getModuleLoaderIntegration().getModuleIdentifier(resModule);
        }

        resModule.addAttachment(ModuleIdentifier.class, identifier);
        return identifier;
    }

    /**
     * Get the module with the given identifier
     *
     * @return The module or null
     */
    Module getModule(ModuleIdentifier identifier) {
        try {
            return getModuleLoader().loadModule(identifier);
        } catch (ModuleLoadException ex) {
            return null;
        }
    }

    /**
     * Get the bundle revision for the given identifier
     *
     * @return The bundle revision or null
     */
    AbstractBundleRevision getBundleRevision(ModuleIdentifier identifier) {
        return modules.get(identifier);
    }

    /**
     * Get the bundle for the given identifier
     *
     * @return The bundle or null
     */
    AbstractBundleState getBundleState(ModuleIdentifier identifier) {
        AbstractBundleRevision bundleRevision = getBundleRevision(identifier);
        return bundleRevision != null ? bundleRevision.getBundleState() : null;
    }

    /**
     * Get the bundle for the given class
     *
     * @return The bundle or null
     */
    AbstractBundleState getBundleState(Class<?> clazz) {
        if (clazz == null)
            throw new IllegalArgumentException("Null clazz");

        AbstractBundleState result = null;
        ClassLoader loader = clazz.getClassLoader();
        if (loader instanceof BundleReference) {
            BundleReference bundleRef = (BundleReference) loader;
            result = AbstractBundleState.assertBundleState(bundleRef.getBundle());
        } else if (loader instanceof ModuleClassLoader) {
            ModuleClassLoader moduleCL = (ModuleClassLoader) loader;
            result = getBundleState(moduleCL.getModule().getIdentifier());
        }
        if (result == null)
            log.debugf("Cannot obtain bundle for: %s", clazz.getName());
        return result;
    }

    /**
     * Create the module in the {@link ModuleLoader}
     *
     * @return The module identifier
     */
    ModuleIdentifier addModule(final XModule resModule) {
        if (resModule == null)
            throw new IllegalArgumentException("Null module");

        Bundle bundle = resModule.getAttachment(Bundle.class);
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        if (bundleState.isFragment())
            throw new IllegalStateException("Fragments cannot be added: " + bundleState);

        Module module = resModule.getAttachment(Module.class);
        if (module != null) {
            AbstractBundleRevision bundleRev = resModule.getAttachment(AbstractBundleRevision.class);
            ModuleIdentifier identifier = module.getIdentifier();
            modules.put(identifier, bundleRev);
            getModuleLoaderIntegration().addModule(module);
            return identifier;
        }

        ModuleIdentifier identifier;
        if (Constants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(resModule.getModuleId().getName())) {
            identifier = getFrameworkModule().getIdentifier();
        } else {
            identifier = createHostModule(resModule);
        }
        return identifier;
    }

    /**
     * Create a {@link ModuleSpec} from the given resolver module definition
     */
    private ModuleIdentifier createHostModule(final XModule resModule) {
        if (resModule == null)
            throw new IllegalArgumentException("Null resModule");

        Bundle bundle = resModule.getAttachment(Bundle.class);
        HostBundleState hostBundle = HostBundleState.assertBundleState(bundle);
        List<RevisionContent> contentRoots = hostBundle.getContentRoots();

        ModuleSpec moduleSpec = resModule.getAttachment(ModuleSpec.class);
        if (moduleSpec == null) {
            ModuleIdentifier identifier = getModuleIdentifier(resModule);
            ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);
            List<DependencySpec> moduleDependencies = new ArrayList<DependencySpec>();

            // Add a dependency on the system module
            SystemPackagesPlugin plugin = injectedSystemPackages.getValue();
            Module systemModule = getSystemModule();
            ModuleIdentifier systemIdentifier = systemModule.getIdentifier();
            ModuleLoader systemModuleLoader = systemModule.getModuleLoader();
            PathFilter systemPackagesFilter = plugin.getSystemPackageFilter();
            moduleDependencies
                    .add(DependencySpec.createModuleDependencySpec(systemPackagesFilter, PathFilters.acceptAll(), systemModuleLoader, systemIdentifier, false));

            // Map the dependency for (the likely) case that the same exporter is choosen for multiple wires
            Map<XModule, ModuleDependencyHolder> specHolderMap = new LinkedHashMap<XModule, ModuleDependencyHolder>();

            // For every {@link XWire} add a dependency on the exporter
            processModuleWires(resModule.getWires(), specHolderMap);

            // Process fragment wires
            Set<String> allPaths = new HashSet<String>();
            List<FragmentBundleRevision> fragRevs = hostBundle.getCurrentRevision().getAttachedFragments();
            for (FragmentBundleRevision fragRev : fragRevs) {
                // This takes care of Package-Imports and Require-Bundle on the fragment
                List<XWire> fragWires = fragRev.getResolverModule().getWires();
                processModuleWires(fragWires, specHolderMap);
            }

            // Add the holder values to dependencies
            for (ModuleDependencyHolder holder : specHolderMap.values())
                moduleDependencies.add(holder.create());

            // Add the module dependencies to the builder
            for (DependencySpec dep : moduleDependencies)
                specBuilder.addDependency(dep);

            // Add a local dependency for the local bundle content
            for (RevisionContent revContent : contentRoots) {
                ResourceLoader resLoader = new RevisionContentResourceLoader(revContent);
                specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resLoader));
                allPaths.addAll(resLoader.getPaths());
            }

            // Process fragment local content
            for (FragmentBundleRevision fragRev : fragRevs) {
                for (RevisionContent revContent : fragRev.getContentList()) {
                    ResourceLoader resLoader = new RevisionContentResourceLoader(revContent);
                    specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resLoader));
                    allPaths.addAll(resLoader.getPaths());
                }
            }

            // Get the set of imported paths
            Set<String> importedPaths = new HashSet<String>();
            for (ModuleDependencyHolder holder : specHolderMap.values()) {
                Set<String> paths = holder.getImportPaths();
                if (paths != null) {
                    importedPaths.addAll(paths);
                }
            }

            if (hostBundle.isActivationLazy()) {
                Set<String> lazyPaths = new HashSet<String>();
                PathFilter lazyFilter = getLazyPackagesFilter(hostBundle);
                for (String path : allPaths) {
                    if (lazyFilter.accept(path))
                        lazyPaths.add(path);
                }
                log.tracef("Module [%s] lazy filter: %s", identifier, lazyFilter);
                LocalLoader localLoader = new LazyActivationLocalLoader(hostBundle, identifier, moduleDependencies, lazyFilter);
                specBuilder.addDependency(DependencySpec.createLocalDependencySpec(localLoader, lazyPaths, true));

                PathFilter eagerFilter = PathFilters.not(lazyFilter);
                PathFilter exportFilter = PathFilters.acceptAll();
                log.tracef("Module [%s] eager filter: %s", identifier, eagerFilter);
                specBuilder.addDependency(DependencySpec.createLocalDependencySpec(eagerFilter, exportFilter));
            } else {
                PathFilter importFilter = PathFilters.acceptAll();
                PathFilter exportFilter = PathFilters.acceptAll();
                if (importedPaths.isEmpty() == false) {
                    importFilter = PathFilters.not(PathFilters.in(importedPaths));
                }
                specBuilder.addDependency(DependencySpec.createLocalDependencySpec(importFilter, exportFilter));
            }

            // Native - Hack
            addNativeResourceLoader(resModule, specBuilder);

            specBuilder.setModuleClassLoaderFactory(new BundleReferenceClassLoader.Factory(hostBundle.getBundleProxy()));
            specBuilder.setFallbackLoader(new HostBundleFallbackLoader(hostBundle, identifier, importedPaths));

            // Build the ModuleSpec
            moduleSpec = specBuilder.create();
        }

        AbstractBundleRevision bundleRev = resModule.getAttachment(AbstractBundleRevision.class);
        ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
        getModuleLoaderIntegration().addModule(moduleSpec);
        modules.put(identifier, bundleRev);
        return identifier;
    }

    /**
     * Get a path filter for packages that trigger bundle activation for a host bundle with lazy ActivationPolicy
     */
    private PathFilter getLazyPackagesFilter(HostBundleState hostBundle) {
        // By default all packages are loaded lazily
        PathFilter result = PathFilters.acceptAll();

        ActivationPolicyMetaData activationPolicy = hostBundle.getActivationPolicy();
        List<String> includes = activationPolicy.getIncludes();
        if (includes != null) {
            Set<String> paths = new HashSet<String>();
            for (String packageName : includes)
                paths.add(packageName.replace('.', '/'));

            result = PathFilters.in(paths);
        }

        List<String> excludes = activationPolicy.getExcludes();
        if (excludes != null) {
            // The set of packages on the exclude list determines the packages that can be loaded eagerly
            Set<String> paths = new HashSet<String>();
            for (String packageName : excludes)
                paths.add(packageName.replace('.', '/'));

            if (includes != null)
                result = PathFilters.all(result, PathFilters.not(PathFilters.in(paths)));
            else
                result = PathFilters.not(PathFilters.in(paths));
        }

        return result;
    }

    private void addNativeResourceLoader(final XModule resModule, ModuleSpec.Builder specBuilder) {
        Bundle bundle = resModule.getAttachment(Bundle.class);
        UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
        Deployment deployment = userBundle.getDeployment();

        addNativeResourceLoader(specBuilder, userBundle, deployment);

        UserBundleRevision rev = userBundle.getCurrentRevision();
        if (rev instanceof HostBundleRevision) {
            for (FragmentBundleRevision fragRev : ((HostBundleRevision) rev).getAttachedFragments()) {
                addNativeResourceLoader(specBuilder, userBundle, fragRev.getDeployment());
            }
        }
    }

    private void addNativeResourceLoader(ModuleSpec.Builder specBuilder, UserBundleState userBundle, Deployment deployment) {
        NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
        if (libMetaData != null) {
            NativeResourceLoader nativeLoader = new NativeResourceLoader();
            for (NativeLibrary library : libMetaData.getNativeLibraries()) {
                String libpath = library.getLibraryPath();
                String libfile = new File(libpath).getName();
                String libname = libfile.substring(0, libfile.lastIndexOf('.'));

                // Add the library provider to the policy
                NativeLibraryProvider libProvider = new NativeCodePlugin.BundleNativeLibraryProvider(userBundle, libname, libpath);
                nativeLoader.addNativeLibrary(libProvider);

                // [TODO] why does the TCK use 'Native' to mean 'libNative' ?
                if (libname.startsWith("lib")) {
                    libname = libname.substring(3);
                    libProvider = new NativeCodePlugin.BundleNativeLibraryProvider(userBundle, libname, libpath);
                    nativeLoader.addNativeLibrary(libProvider);
                }
            }

            specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(nativeLoader));
        }
    }

    private void processModuleWires(List<XWire> wires, Map<XModule, ModuleDependencyHolder> depBuilderMap) {

        // A bundle may both import packages (via Import-Package) and require one
        // or more bundles (via Require-Bundle), but if a package is imported via
        // Import-Package, it is not also visible via Require-Bundle: Import-Package
        // takes priority over Require-Bundle, and packages which are exported by a
        // required bundle and imported via Import-Package must not be treated as
        // split packages.

        // Collect bundle and package wires
        List<XWire> bundleWires = new ArrayList<XWire>();
        List<XWire> packageWires = new ArrayList<XWire>();
        for (XWire wire : wires) {
            XRequirement req = wire.getRequirement();
            XModule importer = wire.getImporter();
            XModule exporter = wire.getExporter();

            // Skip dependencies on the module itself
            if (exporter == importer)
                continue;

            // Skip dependencies on the host that the fragment is attached to
            if (importer.isFragment()) {
                XModule host = importer.getHostRequirement().getWiredCapability().getModule();
                if (host == exporter)
                    continue;
            }

            // Dependency for Import-Package
            if (req instanceof XPackageRequirement) {
                packageWires.add(wire);
                continue;
            }

            // Dependency for Require-Bundle
            if (req instanceof XRequireBundleRequirement) {
                bundleWires.add(wire);
                continue;
            }
        }

        Set<String> importedPaths = new HashSet<String>();
        Set<XModule> packageExporters = new HashSet<XModule>();
        for (XWire wire : packageWires) {
            XModule exporter = wire.getExporter();
            packageExporters.add(exporter);
            XPackageRequirement req = (XPackageRequirement) wire.getRequirement();
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            String path = VFSUtils.getPathFromPackageName(req.getName());
            holder.setOptional(req.isOptional());
            holder.addImportPath(path);
            importedPaths.add(path);
        }
        PathFilter importedPathsFilter = PathFilters.in(importedPaths);

        for (XWire wire : bundleWires) {
            XModule exporter = wire.getExporter();
            if (packageExporters.contains(exporter))
                continue;

            XRequireBundleRequirement req = (XRequireBundleRequirement) wire.getRequirement();
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            holder.setImportFilter(PathFilters.not(importedPathsFilter));
            holder.setOptional(req.isOptional());

            boolean reexport = Constants.VISIBILITY_REEXPORT.equals(req.getVisibility());
            if (reexport == true) {
                Set<String> exportedPaths = new HashSet<String>();
                for (XPackageCapability cap : exporter.getPackageCapabilities()) {
                    String path = cap.getName().replace('.', '/');
                    if (importedPaths.contains(path) == false)
                        exportedPaths.add(path);
                }
                PathFilter exportedPathsFilter = PathFilters.in(exportedPaths);
                holder.setImportFilter(exportedPathsFilter);
                holder.setExportFilter(exportedPathsFilter);
            }
        }
    }

    // Get or create the dependency builder for the exporter
    private ModuleDependencyHolder getDependencyHolder(Map<XModule, ModuleDependencyHolder> depBuilderMap, XModule exporter) {
        ModuleIdentifier exporterId = getModuleIdentifier(exporter);
        ModuleDependencyHolder holder = depBuilderMap.get(exporter);
        if (holder == null) {
            holder = new ModuleDependencyHolder(exporterId);
            depBuilderMap.put(exporter, holder);
        }
        return holder;
    }

    /**
     * Load the module for the given identifier
     *
     * @throws ModuleLoadException If the module cannot be loaded
     */
    Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        if (getSystemModule().getIdentifier().equals(identifier))
            return getSystemModule();
        else if (getFrameworkModule().getIdentifier().equals(identifier))
            return getFrameworkModule();
        else
            return getModuleLoader().loadModule(identifier);
    }

    /**
     * Remove the module with the given identifier
     */
    void removeModule(ModuleIdentifier identifier) {
        modules.remove(identifier);
        getModuleLoaderIntegration().removeModule(identifier);
    }

    private Module getSystemModule() {
        return injectedSystemModule.getValue();
    }

    private Module getFrameworkModule() {
        BundleManager bundleManager = injectedBundleManager.getValue();
        return bundleManager.getSystemBundle().getFrameworkModule();
    }

    private class ModuleDependencyHolder {

        private DependencySpec dependencySpec;
        private ModuleIdentifier identifier;
        private Set<String> importPaths;
        private PathFilter importFilter;
        private PathFilter exportFilter;
        private boolean optional;

        ModuleDependencyHolder(ModuleIdentifier identifier) {
            this.identifier = identifier;
        }

        void addImportPath(String path) {
            assertNotCreated();
            if (importPaths == null)
                importPaths = new HashSet<String>();

            importPaths.add(path);
        }

        Set<String> getImportPaths() {
            return importPaths;
        }

        void setImportFilter(PathFilter importFilter) {
            assertNotCreated();
            this.importFilter = importFilter;
        }

        void setExportFilter(PathFilter exportFilter) {
            assertNotCreated();
            this.exportFilter = exportFilter;
        }

        void setOptional(boolean optional) {
            assertNotCreated();
            this.optional = optional;
        }

        DependencySpec create() {
            if (exportFilter == null) {
                exportFilter = PathFilters.rejectAll();
            }
            if (importFilter == null) {
                importFilter = (importPaths != null ? PathFilters.in(importPaths) : PathFilters.acceptAll());
            }
            Module frameworkModule = getFrameworkModule();
            ModuleLoader depLoader = (frameworkModule.getIdentifier().equals(identifier) ? frameworkModule.getModuleLoader() : getModuleLoader());
            return DependencySpec.createModuleDependencySpec(importFilter, exportFilter, depLoader, identifier, optional);
        }

        private void assertNotCreated() {
            if (dependencySpec != null)
                throw new IllegalStateException("DependencySpec already created");
        }
    }
}