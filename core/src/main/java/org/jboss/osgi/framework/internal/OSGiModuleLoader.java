package org.jboss.osgi.framework.internal;

import java.util.Collections;
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

final class OSGiModuleLoader extends ModuleLoader {

    // Provide logging
    final Logger log = Logger.getLogger(OSGiModuleLoader.class);
    
    // The modules that are registered with this {@link ModuleLoader}
    private Map<ModuleIdentifier, ModuleHolder> modules = new ConcurrentHashMap<ModuleIdentifier, ModuleHolder>();

    OSGiModuleLoader(BundleManager bundleManager) {
    }

    @Override
    public ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException {
        ModuleHolder holder = getModuleHolder(identifier);
        return holder != null ? holder.getModuleSpec() : null;
    }

    @Override
    public Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        ModuleHolder holder = getModuleHolder(identifier);
        if (holder == null)
            throw new IllegalStateException("Cannot find module: " + identifier);

        Module module = holder.getModule();
        if (module == null) {
            module = super.preloadModule(identifier);
            holder.setModule(module);
        }
        return module;
    }

    void addModule(AbstractBundleRevision bundleRev, ModuleSpec moduleSpec) {
        log.tracef("addModule: %s", moduleSpec.getModuleIdentifier());
        ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
        if (modules.get(identifier) != null)
            throw new IllegalStateException("Module already exists: " + identifier);
        modules.put(identifier, new ModuleHolder(bundleRev, moduleSpec));
    }

    void addModule(AbstractBundleRevision bundleRev, Module module) {
        ModuleIdentifier identifier = module.getIdentifier();
        if (modules.get(identifier) != null)
            throw new IllegalStateException("Module already exists: " + identifier);

        modules.put(identifier, new ModuleHolder(bundleRev, module));
    }

    Module removeModule(ModuleIdentifier identifier) {
        log.tracef("removeModule: %s", identifier);
        ModuleHolder moduleHolder = modules.remove(identifier);
        if (moduleHolder == null)
            return null;

        Module module = moduleHolder.module;
        if (module.getModuleLoader() == this)
            unloadModuleLocal(module);

        return module;
    }

    Set<ModuleIdentifier> getModuleIdentifiers() {
        return Collections.unmodifiableSet(modules.keySet());
    }

    AbstractBundleRevision getBundleRevision(ModuleIdentifier identifier) {
        ModuleHolder holder = getModuleHolder(identifier);
        return holder != null ? holder.getBundleRevision() : null;
    }

    AbstractBundleState getBundleState(ModuleIdentifier identifier) {
        AbstractBundleRevision bundleRev = getBundleRevision(identifier);
        return bundleRev != null ? bundleRev.getBundleState() : null;
    }

    Module getModule(ModuleIdentifier identifier) {
        ModuleHolder holder = getModuleHolder(identifier);
        return holder != null ? holder.getModule() : null;
    }

    @Override
    public void setAndRelinkDependencies(Module module, List<DependencySpec> dependencies) throws ModuleLoadException {
        super.setAndRelinkDependencies(module, dependencies);
    }

    private ModuleHolder getModuleHolder(ModuleIdentifier identifier) {
        ModuleHolder holder = modules.get(identifier);
        return holder;
    }

    @Override
    public String toString() {
        return "OSGiModuleLoader";
    }

    // A holder for the {@link ModuleSpec} @{link Module} tuple
    static class ModuleHolder {

        private final AbstractBundleRevision bundleRev;
        private ModuleSpec moduleSpec;
        private Module module;

        ModuleHolder(AbstractBundleRevision bundleRev, ModuleSpec moduleSpec) {
            if (bundleRev == null)
                throw new IllegalArgumentException("Null bundleRev");
            if (moduleSpec == null)
                throw new IllegalArgumentException("Null moduleSpec");
            this.bundleRev = bundleRev;
            this.moduleSpec = moduleSpec;
        }

        ModuleHolder(AbstractBundleRevision bundleRev, Module module) {
            if (bundleRev == null)
                throw new IllegalArgumentException("Null bundleRev");
            if (module == null)
                throw new IllegalArgumentException("Null module");
            this.bundleRev = bundleRev;
            this.module = module;
        }

        AbstractBundleRevision getBundleRevision() {
            return bundleRev;
        }

        ModuleSpec getModuleSpec() {
            return moduleSpec;
        }

        Module getModule() {
            return module;
        }

        void setModule(Module module) {
            this.module = module;
        }
    }
}