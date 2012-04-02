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
import static org.osgi.framework.Constants.VISIBILITY_REEXPORT;

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
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.ClassFilter;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.ModuleLoaderProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemPathsProvider;
import org.jboss.osgi.framework.internal.NativeCodePlugin.BundleNativeLibraryProvider;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XBundleRequirement;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.BundleReference;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;

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
    private final InjectedValue<SystemPathsProvider> injectedSystemPaths = new InjectedValue<SystemPathsProvider>();
    private final InjectedValue<ModuleLoaderProvider> injectedModuleLoader = new InjectedValue<ModuleLoaderProvider>();

    private Map<ModuleIdentifier, BundleRevision> modules = new ConcurrentHashMap<ModuleIdentifier, BundleRevision>();

    static void addService(ServiceTarget serviceTarget) {
        ModuleManagerPlugin service = new ModuleManagerPlugin();
        ServiceBuilder<ModuleManagerPlugin> builder = serviceTarget.addService(InternalServices.MODULE_MANGER_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(Services.MODULE_LOADER_PROVIDER, ModuleLoaderProvider.class, service.injectedModuleLoader);
        builder.addDependency(Services.SYSTEM_PATHS_PROVIDER, SystemPathsProvider.class, service.injectedSystemPaths);
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

    ModuleIdentifier getModuleIdentifier(final XResource xres) {
        if (xres == null)
            throw new IllegalArgumentException("Null resource");
        if (xres instanceof FragmentBundleRevision)
            throw new IllegalArgumentException("A fragment is not a module");

        ModuleIdentifier identifier = xres.getAttachment(ModuleIdentifier.class);
        if (identifier != null)
            return identifier;

        XIdentityCapability icap = xres.getIdentityCapability();
        Module module = xres.getAttachment(Module.class);
        if (module != null) {
            identifier = module.getIdentifier();
        } else if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(icap.getSymbolicName())) {
            identifier = getFrameworkModule().getIdentifier();
        } else {
            int revision = (xres instanceof AbstractBundleRevision ? ((AbstractBundleRevision)xres).getRevisionId() : 0);
            identifier = getModuleLoaderIntegration().getModuleIdentifier(xres, revision);
        }

        xres.addAttachment(ModuleIdentifier.class, identifier);
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
     * Get the associated bundle revision for the given identifier
     * @return The revision or null
     */
    BundleRevision getBundleRevision(ModuleIdentifier identifier) {
        return modules.get(identifier);
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
        }
        if (result == null)
            log.debugf("Cannot obtain bundle for: %s", clazz.getName());
        return result;
    }

    ModuleIdentifier addModule(final XResource res, final List<Wire> wires) {
        if (res == null)
            throw new IllegalArgumentException("Null res");
        if (wires == null)
            throw new IllegalArgumentException("Null wires");
        if (res instanceof FragmentBundleRevision)
            throw new IllegalStateException("Fragments cannot be added: " + res);

        Module module = res.getAttachment(Module.class);
        if (module != null) {
            getModuleLoaderIntegration().addModule(module);
            return module.getIdentifier();
        }

        ModuleIdentifier identifier;
        XIdentityCapability icap = res.getIdentityCapability();
        if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(icap.getSymbolicName())) {
            identifier = getFrameworkModule().getIdentifier();
        } else {
            HostBundleRevision hostRev = HostBundleRevision.assertHostRevision(res);
            identifier = createHostModule(hostRev, wires);
        }
        return identifier;
    }

    /**
     * Create a {@link ModuleSpec} from the given resolver module definition
     */
    private ModuleIdentifier createHostModule(final HostBundleRevision hostRev, final List<Wire> wires) {

        HostBundleState hostBundle = hostRev.getBundleState();
        List<RevisionContent> contentRoots = hostBundle.getContentRoots();

        ModuleIdentifier identifier = getModuleIdentifier(hostRev);
        ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);
        List<DependencySpec> moduleDependencies = new ArrayList<DependencySpec>();

        // Add a system dependency
        SystemPathsProvider plugin = injectedSystemPaths.getValue();
        Set<String> bootPaths = plugin.getBootDelegationPaths();
        PathFilter bootFilter = plugin.getBootDelegationFilter();
        PathFilter acceptAll = PathFilters.acceptAll();
        moduleDependencies.add(DependencySpec.createSystemDependencySpec(bootFilter, acceptAll, bootPaths));

        // Map the dependency for (the likely) case that the same exporter is choosen for multiple wires
        Map<XResource, ModuleDependencyHolder> specHolderMap = new LinkedHashMap<XResource, ModuleDependencyHolder>();

        // For every {@link XWire} add a dependency on the exporter
        processModuleWireList(wires, specHolderMap);

        // Process fragment wires
        Set<String> allPaths = new HashSet<String>();

        // Add the holder values to dependencies
        for (ModuleDependencyHolder holder : specHolderMap.values())
            moduleDependencies.add(holder.create());

        // Add the module dependencies to the builder
        for (DependencySpec dep : moduleDependencies)
            specBuilder.addDependency(dep);

        // Add resource roots the local bundle content
        for (RevisionContent revContent : contentRoots) {
            ResourceLoader resLoader = new RevisionContentResourceLoader(revContent);
            specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resLoader));
            allPaths.addAll(resLoader.getPaths());
        }

        // Process fragment local content and more resource roots
        Set<FragmentBundleRevision> fragRevs = hostRev.getAttachedFragments();
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

        // Setup the local loader dependency
        PathFilter importFilter = acceptAll;
        PathFilter exportFilter = acceptAll;
        if (importedPaths.isEmpty() == false) {
            importFilter = PathFilters.not(PathFilters.in(importedPaths));
        }
        PathFilter resImportFilter = PathFilters.acceptAll();
        PathFilter resExportFilter = PathFilters.acceptAll();
        ClassFilter classImportFilter = new ClassFilter() {
            public boolean accept(String className) {
                return true;
            }
        };
        final PathFilter cefPath = getExportClassFilter(hostRev);
        ClassFilter classExportFilter = new ClassFilter() {
            public boolean accept(String className) {
                return cefPath.accept(className);
            }
        };
        log.debugf("createLocalDependencySpec: [if=%s,ef=%s,rif=%s,ref=%s,cf=%s]", importFilter, exportFilter, resImportFilter, resExportFilter, cefPath);
        DependencySpec localDep = DependencySpec.createLocalDependencySpec(importFilter, exportFilter, resImportFilter, resExportFilter, classImportFilter,
                classExportFilter);
        specBuilder.addDependency(localDep);

        // Native - Hack
        addNativeResourceLoader(hostRev, specBuilder);

        PathFilter lazyActivationFilter = getLazyPackagesFilter(hostBundle);
        specBuilder.setModuleClassLoaderFactory(new HostBundleClassLoader.Factory(hostBundle, lazyActivationFilter));
        specBuilder.setFallbackLoader(new FallbackLoader(hostRev, identifier, importedPaths));

        // Build the ModuleSpec
        ModuleSpec moduleSpec = specBuilder.create();

        modules.put(identifier, hostRev);
        getModuleLoaderIntegration().addModule(moduleSpec);
        return identifier;
    }

    private void processModuleWireList(List<Wire> wires, Map<XResource, ModuleDependencyHolder> depBuilderMap) {

        // A bundle may both import packages (via Import-Package) and require one
        // or more bundles (via Require-Bundle), but if a package is imported via
        // Import-Package, it is not also visible via Require-Bundle: Import-Package
        // takes priority over Require-Bundle, and packages which are exported by a
        // required bundle and imported via Import-Package must not be treated as
        // split packages.

        // Collect bundle and package wires
        List<Wire> bundleWires = new ArrayList<Wire>();
        List<Wire> packageWires = new ArrayList<Wire>();
        for (Wire wire : wires) {
            Requirement req = wire.getRequirement();
            XResource importer = (XResource) wire.getRequirer();
            XResource exporter = (XResource) wire.getProvider();

            // Skip dependencies on the module itself
            if (exporter == importer)
                continue;

            // Dependency for Import-Package
            if (req instanceof XPackageRequirement) {
                packageWires.add(wire);
                continue;
            }

            // Dependency for Require-Bundle
            if (req instanceof XBundleRequirement) {
                bundleWires.add(wire);
                continue;
            }
        }

        Set<String> importedPaths = new HashSet<String>();
        Set<Resource> packageExporters = new HashSet<Resource>();
        for (Wire wire : packageWires) {
            XResource exporter = (XResource) wire.getProvider();
            packageExporters.add(exporter);
            XPackageRequirement req = (XPackageRequirement) wire.getRequirement();
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            String path = VFSUtils.getPathFromPackageName(req.getPackageName());
            holder.setOptional(req.isOptional());
            holder.addImportPath(path);
            importedPaths.add(path);
        }
        PathFilter importedPathsFilter = PathFilters.in(importedPaths);

        for (Wire wire : bundleWires) {
            XResource exporter = (XResource) wire.getProvider();
            if (packageExporters.contains(exporter))
                continue;

            XBundleRequirement req = (XBundleRequirement) wire.getRequirement();
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            holder.setImportFilter(PathFilters.not(importedPathsFilter));
            holder.setOptional(req.isOptional());

            boolean reexport = VISIBILITY_REEXPORT.equals(req.getVisibility());
            if (reexport == true) {
                Set<String> exportedPaths = new HashSet<String>();
                for (Capability auxcap : exporter.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                    XPackageCapability packcap = (XPackageCapability) auxcap;
                    String path = packcap.getPackageName().replace('.', '/');
                    if (importedPaths.contains(path) == false)
                        exportedPaths.add(path);
                }
                PathFilter exportedPathsFilter = PathFilters.in(exportedPaths);
                holder.setImportFilter(exportedPathsFilter);
                holder.setExportFilter(exportedPathsFilter);
            }
        }
    }

    private PathFilter getExportClassFilter(XResource resModule) {
        PathFilter includeFilter = null;
        PathFilter excludeFilter = null;
        for (Capability auxcap : resModule.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
            XPackageCapability packageCap = (XPackageCapability) auxcap;
            String includeDirective = packageCap.getDirective(Constants.INCLUDE_DIRECTIVE);
            if (includeDirective != null) {
                String packageName = packageCap.getPackageName();
                String[] patterns = includeDirective.split(",");
                List<PathFilter> includes = new ArrayList<PathFilter>();
                for (String pattern : patterns) {
                    includes.add(PathFilters.match(packageName + "." + pattern));
                }
                includeFilter = PathFilters.any(includes);
            }
            String excludeDirective = packageCap.getDirective(Constants.EXCLUDE_DIRECTIVE);
            if (excludeDirective != null) {
                String packageName = packageCap.getPackageName();
                String[] patterns = excludeDirective.split(",");
                List<PathFilter> excludes = new ArrayList<PathFilter>();
                for (String pattern : patterns) {
                    excludes.add(PathFilters.match(packageName + "." + pattern));
                }
                excludeFilter = PathFilters.not(PathFilters.any(excludes));
            }
        }

        // Accept all classes for export if there is no filter specified
        if (includeFilter == null && excludeFilter == null)
            return PathFilters.acceptAll();

        if (includeFilter == null)
            includeFilter = PathFilters.acceptAll();

        if (excludeFilter == null)
            excludeFilter = PathFilters.rejectAll();

        return PathFilters.all(includeFilter, excludeFilter);
    }

    /**
     * Get a path filter for packages that trigger bundle activation for a host bundle with lazy ActivationPolicy
     */
    private PathFilter getLazyPackagesFilter(HostBundleState hostBundle) {

        // By default all packages are loaded lazily
        PathFilter result = PathFilters.acceptAll();

        ActivationPolicyMetaData activationPolicy = hostBundle.getActivationPolicy();
        if (activationPolicy != null) {
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
        }
        return result;
    }

    private void addNativeResourceLoader(HostBundleRevision hostrev, ModuleSpec.Builder specBuilder) {
        Deployment deployment = hostrev.getDeployment();
        addNativeResourceLoader(specBuilder, hostrev, deployment);
        if (hostrev instanceof HostBundleRevision) {
            for (FragmentBundleRevision fragRev : hostrev.getAttachedFragments()) {
                addNativeResourceLoader(specBuilder, hostrev, fragRev.getDeployment());
            }
        }
    }

    private void addNativeResourceLoader(ModuleSpec.Builder specBuilder, HostBundleRevision hostrev, Deployment deployment) {
        NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
        if (libMetaData != null) {
            NativeResourceLoader nativeLoader = new NativeResourceLoader();
            for (NativeLibrary library : libMetaData.getNativeLibraries()) {
                String libpath = library.getLibraryPath();
                String libfile = new File(libpath).getName();
                String libname = libfile.substring(0, libfile.lastIndexOf('.'));

                // Add the library provider to the policy
                NativeLibraryProvider libProvider = new BundleNativeLibraryProvider(hostrev, libname, libpath);
                nativeLoader.addNativeLibrary(libProvider);

                // [TODO] why does the TCK use 'Native' to mean 'libNative' ?
                if (libname.startsWith("lib")) {
                    libname = libname.substring(3);
                    libProvider = new BundleNativeLibraryProvider(hostrev, libname, libpath);
                    nativeLoader.addNativeLibrary(libProvider);
                }
            }

            specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(nativeLoader));
        }
    }

    // Get or create the dependency builder for the exporter
    private ModuleDependencyHolder getDependencyHolder(Map<XResource, ModuleDependencyHolder> depBuilderMap, XResource exporter) {
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
        if (getFrameworkModule().getIdentifier().equals(identifier))
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
            log.debugf("createModuleDependencySpec: [id=%s,if=%s,ef=%s,loader=%s,optional=%s]", identifier, importFilter, exportFilter, depLoader, optional);
            return DependencySpec.createModuleDependencySpec(importFilter, exportFilter, depLoader, identifier, optional);
        }

        private void assertNotCreated() {
            if (dependencySpec != null)
                throw new IllegalStateException("DependencySpec already created");
        }
    }
}