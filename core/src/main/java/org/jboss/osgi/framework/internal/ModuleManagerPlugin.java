/*
 * #%L
 * JBossOSGi Framework Core
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.VISIBILITY_REEXPORT;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ModuleSpec.Builder;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.ClassFilter;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.FrameworkModulePlugin;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.ModuleLoaderPlugin;
import org.jboss.osgi.framework.ModuleLoaderPlugin.ModuleSpecBuilderContext;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.SystemPathsPlugin;
import org.jboss.osgi.framework.internal.NativeCodePlugin.BundleNativeLibraryProvider;
import org.jboss.osgi.metadata.ActivationPolicyMetaData;
import org.jboss.osgi.metadata.NativeLibrary;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.XResourceRequirement;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.BundleReference;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.resource.Capability;
import org.osgi.resource.Resource;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
final class ModuleManagerPlugin extends AbstractPluginService<ModuleManagerPlugin> {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<SystemPathsPlugin> injectedSystemPaths = new InjectedValue<SystemPathsPlugin>();
    private final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    private final InjectedValue<FrameworkModulePlugin> injectedFrameworkModule = new InjectedValue<FrameworkModulePlugin>();
    private final InjectedValue<ModuleLoaderPlugin> injectedModuleLoader = new InjectedValue<ModuleLoaderPlugin>();
    private Module frameworkModule;

    static void addService(ServiceTarget serviceTarget) {
        ModuleManagerPlugin service = new ModuleManagerPlugin();
        ServiceBuilder<ModuleManagerPlugin> builder = serviceTarget.addService(InternalServices.MODULE_MANGER_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, service.injectedEnvironment);
        builder.addDependency(Services.SYSTEM_BUNDLE, SystemBundleState.class, service.injectedSystemBundle);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_PLUGIN, FrameworkModulePlugin.class, service.injectedFrameworkModule);
        builder.addDependency(IntegrationServices.MODULE_LOADER_PLUGIN, ModuleLoaderPlugin.class, service.injectedModuleLoader);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS_PLUGIN, SystemPathsPlugin.class, service.injectedSystemPaths);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private ModuleManagerPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        FrameworkModulePlugin modulePlugin = injectedFrameworkModule.getValue();
        SystemBundleState systemBundle = injectedSystemBundle.getValue();
        frameworkModule = modulePlugin.getFrameworkModule(systemBundle);
    }

    @Override
    public ModuleManagerPlugin getValue() {
        return this;
    }

    ModuleLoaderPlugin getModuleLoaderPlugin() {
        return injectedModuleLoader.getValue();
    }

    ModuleLoader getModuleLoader() {
        return getModuleLoaderPlugin().getModuleLoader();
    }

    Module getFrameworkModule() {
        return frameworkModule;
    }

    ModuleIdentifier getModuleIdentifier(final XBundleRevision brev) {
        assert brev != null : "Null resource";
        assert !brev.isFragment() : "A fragment is not a module";

        ModuleIdentifier identifier = brev.getAttachment(ModuleIdentifier.class);
        if (identifier != null)
            return identifier;

        XIdentityCapability icap = brev.getIdentityCapability();
        Module module = brev.getAttachment(Module.class);
        if (module != null) {
            identifier = module.getIdentifier();
        } else if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(icap.getSymbolicName())) {
            identifier = getFrameworkModule().getIdentifier();
        } else {
            identifier = getModuleLoaderPlugin().getModuleIdentifier(brev);
        }

        return identifier;
    }

    /**
     * Get the module with the given identifier
     *
     * @return The module or null
     */
    Module getModule(ModuleIdentifier identifier) {
        if (frameworkModule.getIdentifier().equals(identifier)) {
            return frameworkModule;
        }
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
    XBundleRevision getBundleRevision(ModuleIdentifier identifier) {
        XBundleRevision result = null;
        XEnvironment env = injectedEnvironment.getValue();
        for (XResource res : env.getResources(XEnvironment.ALL_IDENTITY_TYPES)) {
            Module module = res.getAttachment(Module.class);
            if (module != null && module.getIdentifier().equals(identifier)) {
                result = (XBundleRevision) res;
            }
        }
        return result;
    }

    /**
     * Get the bundle for the given class
     *
     * @return The bundle or null
     */
    XBundle getBundleState(Class<?> clazz) {
        assert clazz != null : "Null clazz";
        XBundle result = null;
        ClassLoader loader = clazz.getClassLoader();
        if (loader instanceof BundleReference) {
            BundleReference bundleRef = (BundleReference) loader;
            result = (XBundle) bundleRef.getBundle();
        } else if (loader instanceof ModuleClassLoader) {
            Module module = ((ModuleClassLoader) loader).getModule();
            XBundleRevision brev = getBundleRevision(module.getIdentifier());
            result = brev != null ? brev.getBundle() : null;
        }
        if (result == null)
            LOGGER.debugf("Cannot obtain bundle for: %s", clazz.getName());
        return result;
    }

    ModuleIdentifier addModule(final XBundleRevision brev, final List<BundleWire> wires) {
        assert brev != null : "Null res";
        assert wires != null : "Null wires";
        assert !brev.isFragment() : "Fragments cannot be added: " + brev;

        Module module = brev.getAttachment(Module.class);
        if (module != null) {
            ModuleIdentifier identifier = module.getIdentifier();
            ModuleLoaderPlugin moduleLoaderPlugin = getModuleLoaderPlugin();
            if (moduleLoaderPlugin.getModule(identifier) == null) {
                moduleLoaderPlugin.addModule(module);
            }
            return identifier;
        }

        ModuleIdentifier identifier;
        XIdentityCapability icap = brev.getIdentityCapability();
        if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(icap.getSymbolicName())) {
            identifier = getFrameworkModule().getIdentifier();
        } else {
            HostBundleRevision hostRev = HostBundleRevision.assertHostRevision(brev);
            identifier = createHostModule(hostRev, wires);
        }
        return identifier;
    }

    /**
     * Create a {@link ModuleSpec} from the given resolver module definition
     */
    private ModuleIdentifier createHostModule(final HostBundleRevision hostRev, final List<BundleWire> wires) {

        HostBundleState hostBundle = hostRev.getBundleState();
        List<RevisionContent> contentRoots = hostBundle.getContentRoots();

        final ModuleIdentifier identifier = getModuleIdentifier(hostRev);
        final ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);
        final Map<ModuleIdentifier, DependencySpec> moduleDependencies = new LinkedHashMap<ModuleIdentifier, DependencySpec>();

        // Add a system dependency
        SystemPathsPlugin plugin = injectedSystemPaths.getValue();
        Set<String> bootPaths = plugin.getBootDelegationPaths();
        PathFilter bootFilter = plugin.getBootDelegationFilter();
        PathFilter acceptAll = PathFilters.acceptAll();
        specBuilder.addDependency(DependencySpec.createSystemDependencySpec(bootFilter, acceptAll, bootPaths));

        // Map the dependency for (the likely) case that the same exporter is choosen for multiple wires
        Map<BundleRevision, ModuleDependencyHolder> specHolderMap = new LinkedHashMap<BundleRevision, ModuleDependencyHolder>();

        // For every {@link XWire} add a dependency on the exporter
        processModuleWireList(wires, specHolderMap);

        // Process fragment wires
        Set<String> allPaths = new HashSet<String>();

        // Add the holder values to dependencies
        for (ModuleDependencyHolder holder : specHolderMap.values()) {
            moduleDependencies.put(holder.getIdentifier(), holder.create());
        }

        // Add the module dependencies to the builder
        for (DependencySpec dep : moduleDependencies.values())
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
            @Override
            public boolean accept(String className) {
                return true;
            }
        };
        final PathFilter cefPath = getExportClassFilter(hostRev);
        ClassFilter classExportFilter = new ClassFilter() {
            @Override
            public boolean accept(String className) {
                return cefPath.accept(className);
            }
        };
        LOGGER.tracef("createLocalDependencySpec: [if=%s,ef=%s,rif=%s,ref=%s,cf=%s]", importFilter, exportFilter, resImportFilter, resExportFilter, cefPath);
        DependencySpec localDep = DependencySpec.createLocalDependencySpec(importFilter, exportFilter, resImportFilter, resExportFilter, classImportFilter,
                classExportFilter);
        specBuilder.addDependency(localDep);

        // Native - Hack
        addNativeResourceLoader(hostRev, specBuilder);

        PathFilter lazyActivationFilter = getLazyPackagesFilter(hostBundle);
        specBuilder.setModuleClassLoaderFactory(new HostBundleClassLoader.Factory(hostBundle, lazyActivationFilter));
        specBuilder.setFallbackLoader(new FallbackLoader(hostRev, identifier, importedPaths));

        ModuleSpecBuilderContext context = new ModuleSpecBuilderContext() {

            @Override
            public XBundleRevision getBundleRevision() {
                return hostRev;
            }

            @Override
            public Builder getModuleSpecBuilder() {
                return specBuilder;
            }

            @Override
            public Map<ModuleIdentifier, DependencySpec> getModuleDependencies() {
                return Collections.unmodifiableMap(moduleDependencies);
            }
        };

        // Add integration dependencies, build the spec and add it to the module loader
        ModuleLoaderPlugin moduleLoaderPlugin = getModuleLoaderPlugin();
        moduleLoaderPlugin.addIntegrationDependencies(context);
        moduleLoaderPlugin.addModule(specBuilder.create());

        return identifier;
    }

    private void processModuleWireList(List<BundleWire> wires, Map<BundleRevision, ModuleDependencyHolder> depBuilderMap) {

        // A bundle may both import packages (via Import-Package) and require one
        // or more bundles (via Require-Bundle), but if a package is imported via
        // Import-Package, it is not also visible via Require-Bundle: Import-Package
        // takes priority over Require-Bundle, and packages which are exported by a
        // required bundle and imported via Import-Package must not be treated as
        // split packages.

        // Collect bundle and package wires
        List<BundleWire> bundleWires = new ArrayList<BundleWire>();
        List<BundleWire> packageWires = new ArrayList<BundleWire>();
        for (BundleWire wire : wires) {
            XRequirement req = (XRequirement) wire.getRequirement();
            XBundleRevision importer = (XBundleRevision) wire.getRequirer();
            XBundleRevision exporter = (XBundleRevision) wire.getProvider();

            // Skip dependencies on the module itself
            if (exporter == importer)
                continue;

            // Dependency for Import-Package
            if (req.adapt(XPackageRequirement.class) != null) {
                packageWires.add(wire);
                continue;
            }

            // Dependency for Require-Bundle
            if (req.adapt(XResourceRequirement.class) != null) {
                bundleWires.add(wire);
                continue;
            }
        }

        Set<String> importedPaths = new HashSet<String>();
        Set<Resource> packageExporters = new HashSet<Resource>();
        for (BundleWire wire : packageWires) {
            XBundleRevision exporter = (XBundleRevision) wire.getProvider();
            packageExporters.add(exporter);
            XRequirement xreq = (XRequirement) wire.getRequirement();
            XPackageRequirement packreq = xreq.adapt(XPackageRequirement.class);
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            String path = VFSUtils.getPathFromPackageName(packreq.getPackageName());
            holder.setOptional(packreq.isOptional());
            holder.addImportPath(path);
            importedPaths.add(path);
        }
        PathFilter importedPathsFilter = PathFilters.in(importedPaths);

        for (BundleWire wire : bundleWires) {
            XBundleRevision exporter = (XBundleRevision) wire.getProvider();
            if (packageExporters.contains(exporter))
                continue;

            XRequirement xreq = (XRequirement) wire.getRequirement();
            XResourceRequirement resreq = xreq.adapt(XResourceRequirement.class);
            ModuleDependencyHolder holder = getDependencyHolder(depBuilderMap, exporter);
            holder.setImportFilter(PathFilters.not(importedPathsFilter));
            holder.setOptional(resreq.isOptional());

            boolean reexport = VISIBILITY_REEXPORT.equals(resreq.getVisibility());
            if (reexport == true) {
                Set<String> exportedPaths = new HashSet<String>();
                for (Capability auxcap : exporter.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE)) {
                    XCapability xcap = (XCapability) auxcap;
                    XPackageCapability packcap = xcap.adapt(XPackageCapability.class);
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
            XCapability xcap = (XCapability) auxcap;
            XPackageCapability packcap = xcap.adapt(XPackageCapability.class);
            String includeDirective = packcap.getDirective(Constants.INCLUDE_DIRECTIVE);
            if (includeDirective != null) {
                String packageName = packcap.getPackageName();
                String[] patterns = includeDirective.split(",");
                List<PathFilter> includes = new ArrayList<PathFilter>();
                for (String pattern : patterns) {
                    includes.add(PathFilters.match(packageName + "." + pattern));
                }
                includeFilter = PathFilters.any(includes);
            }
            String excludeDirective = packcap.getDirective(Constants.EXCLUDE_DIRECTIVE);
            if (excludeDirective != null) {
                String packageName = packcap.getPackageName();
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
    private ModuleDependencyHolder getDependencyHolder(Map<BundleRevision, ModuleDependencyHolder> depBuilderMap, XBundleRevision exporter) {
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
        getModuleLoaderPlugin().removeModule(identifier);
    }

    private class ModuleDependencyHolder {

        private final ModuleIdentifier identifier;
        private DependencySpec dependencySpec;
        private Set<String> importPaths;
        private PathFilter importFilter;
        private PathFilter exportFilter;
        private boolean optional;

        ModuleDependencyHolder(ModuleIdentifier identifier) {
            this.identifier = identifier;
        }

        ModuleIdentifier getIdentifier() {
            return identifier;
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
            LOGGER.tracef("createModuleDependencySpec: [id=%s,if=%s,ef=%s,loader=%s,optional=%s]", identifier, importFilter, exportFilter, depLoader, optional);
            return DependencySpec.createModuleDependencySpec(importFilter, exportFilter, depLoader, identifier, optional);
        }

        private void assertNotCreated() {
            assert dependencySpec == null : "DependencySpec already created";
        }
    }
}
