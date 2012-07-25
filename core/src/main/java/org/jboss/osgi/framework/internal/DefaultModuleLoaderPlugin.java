package org.jboss.osgi.framework.internal;
/*
 * #%L
 * JBossOSGi Framework
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

import static org.jboss.osgi.framework.Constants.JBOSGI_PREFIX;
import static org.jboss.osgi.framework.IntegrationServices.MODULE_LOADER_PLUGIN;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.service.ValueService;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.osgi.framework.ModuleLoaderPlugin;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XIdentityCapability;

/**
 * Integration point for the {@link ModuleLoader}.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
final class DefaultModuleLoaderPlugin extends ModuleLoader implements ModuleLoaderPlugin {

    private Map<ModuleIdentifier, ModuleHolder> moduleSpecs = new ConcurrentHashMap<ModuleIdentifier, ModuleHolder>();
    private ServiceRegistry serviceRegistry;
    private ServiceTarget serviceTarget;

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(MODULE_LOADER_PLUGIN) == null) {
            ModuleLoaderPlugin service = new DefaultModuleLoaderPlugin();
            ServiceBuilder<ModuleLoaderPlugin> builder = serviceTarget.addService(MODULE_LOADER_PLUGIN, service);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultModuleLoaderPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        LOGGER.tracef("Starting: %s", context.getController().getName());
        serviceRegistry = context.getController().getServiceContainer();
        serviceTarget = context.getChildTarget();
    }

    @Override
    public void stop(StopContext context) {
        LOGGER.tracef("Stopping: %s", context.getController().getName());
    }

    @Override
    public ModuleLoaderPlugin getValue() {
        return this;
    }

    @Override
    public ModuleLoader getModuleLoader() {
        return this;
    }

    @Override
    public void addIntegrationDependencies(ModuleSpecBuilderContext context) {
        // do nothing
    }

    @Override
    public ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException {
        ModuleHolder moduleHolder = moduleSpecs.get(identifier);
        return moduleHolder != null ? moduleHolder.getModuleSpec() : null;
    }

    @Override
    protected Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        Module module = null;
        ModuleHolder moduleHolder = moduleSpecs.get(identifier);
        if (moduleHolder != null) {
            module = moduleHolder.getModule();
            if (module == null) {
                module = loadModuleLocal(identifier);
                moduleHolder.setModule(module);
            }
        }
        return module;
    }

    @Override
    public ModuleIdentifier getModuleIdentifier(XBundleRevision brev) {
        XIdentityCapability icap = brev.getIdentityCapability();
        List<XBundleRevision> allrevs = brev.getBundle().getAllBundleRevisions();
        String name = icap.getSymbolicName();
        if (allrevs.size() > 1) {
            name += "-rev" + (allrevs.size() - 1);
        }
        return ModuleIdentifier.create(JBOSGI_PREFIX + "." + name, "" + icap.getVersion());
    }

    @Override
    public Module getModule(ModuleIdentifier identifier) {
        final ModuleHolder moduleHolder = moduleSpecs.get(identifier);
        final Module result = moduleHolder != null ? moduleHolder.getModule() : null;
        LOGGER.tracef("getModule: %s => %s", identifier, result);
        return result;
    }

    @Override
    public void addModuleSpec(XBundleRevision brev, ModuleSpec moduleSpec) {
        LOGGER.tracef("addModule: %s", moduleSpec.getModuleIdentifier());
        ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
        if (moduleSpecs.get(identifier) != null)
            throw MESSAGES.illegalStateModuleAlreadyExists(identifier);
        ModuleHolder moduleHolder = new ModuleHolder(moduleSpec);
        moduleSpecs.put(identifier, moduleHolder);
    }

    @Override
    public void addModule(XBundleRevision brev, Module module) {
        LOGGER.tracef("addModule: %s", module.getIdentifier());
        ModuleIdentifier identifier = module.getIdentifier();
        if (moduleSpecs.get(identifier) != null)
            throw MESSAGES.illegalStateModuleAlreadyExists(identifier);
        ModuleHolder moduleHolder = new ModuleHolder(module);
        moduleSpecs.put(identifier, moduleHolder);
    }

    @Override
    public void removeModule(ModuleIdentifier identifier) {
        LOGGER.tracef("removeModule: %s", identifier);

        // Remove the ModuleSpec, which makes the Module unavailable for load
        moduleSpecs.remove(identifier);

        // Remove the module service
        ServiceController<?> moduleService = serviceRegistry.getService(getModuleServiceName(identifier));
        if (moduleService != null) {
            moduleService.setMode(Mode.REMOVE);
        }

        // Unload the Module from the ModuleLoader
        try {
            Module module = loadModuleLocal(identifier);
            if (module != null) {
                unloadModuleLocal(module);
            }
        } catch (ModuleLoadException ex) {
            // ignore
        }
    }

    @Override
    public ServiceName createModuleService(XBundleRevision resource, ModuleIdentifier identifier) {
        Module module;
        ServiceName moduleServiceName = getModuleServiceName(identifier);
        try {
            module = loadModule(identifier);
            ValueService<Module> service = new ValueService<Module>(new ImmediateValue<Module>(module));
            ServiceBuilder<Module> builder = serviceTarget.addService(moduleServiceName, service);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        } catch (ModuleLoadException ex) {
            throw MESSAGES.illegalStateCannotLoadModule(ex, identifier);
        }
        return moduleServiceName;
    }


    @Override
    public ServiceName getModuleServiceName(ModuleIdentifier identifier) {
        return InternalServices.MODULE_SERVICE.append(identifier.getName()).append(identifier.getSlot());
    }

    @Override
    public String toString() {
        return DefaultModuleLoaderPlugin.class.getSimpleName();
    }

    static class ModuleHolder {

        private ModuleSpec moduleSpec;
        private Module module;

        ModuleHolder(ModuleSpec moduleSpec) {
            assert moduleSpec != null : "Null moduleSpec";
            this.moduleSpec = moduleSpec;
        }

        ModuleHolder(Module module) {
            assert module != null : "Null module";
            this.module = module;
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
