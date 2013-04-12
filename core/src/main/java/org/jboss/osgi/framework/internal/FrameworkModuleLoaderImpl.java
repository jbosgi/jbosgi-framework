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
import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.ValueService;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.osgi.framework.spi.FrameworkModuleLoader;
import org.jboss.osgi.framework.spi.FutureServiceValue;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleWire;

/**
 * Integration point for the {@link ModuleLoader}.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
public final class FrameworkModuleLoaderImpl extends ModuleLoader implements FrameworkModuleLoader {

    private final Map<ModuleIdentifier, ModuleHolder> moduleSpecs = new ConcurrentHashMap<ModuleIdentifier, ModuleHolder>();
    private final ServiceRegistry serviceRegistry;

    public FrameworkModuleLoaderImpl(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
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
        synchronized (moduleSpecs) {
            ModuleHolder moduleHolder = moduleSpecs.get(identifier);
            return moduleHolder != null ? moduleHolder.getModuleSpec() : null;
        }
    }

    @Override
    protected Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        Module module = null;
        synchronized (moduleSpecs) {
            ModuleHolder moduleHolder = moduleSpecs.get(identifier);
            if (moduleHolder != null) {
                module = moduleHolder.getModule();
                if (module == null) {
                    module = loadModuleLocal(identifier);
                    moduleHolder.setModule(module);
                }
            }
        }
        return module;
    }

    @Override
    public ModuleIdentifier getModuleIdentifier(XBundleRevision brev) {
        XBundle bundle = brev.getBundle();
        StorageState storageState = brev.getAttachment(IntegrationConstants.STORAGE_STATE_KEY);
        int revisionId = storageState.getRevisionId();
        String bundleId = "bid" + bundle.getBundleId() + "rev" + revisionId;
        String bsname = bundle.getSymbolicName();
        Version version = bundle.getVersion();
        return ModuleIdentifier.create(JBOSGI_PREFIX + "." + bsname, version + "." + bundleId);
    }

    @Override
    public void addModuleSpec(XBundleRevision brev, ModuleSpec moduleSpec) {
        synchronized (moduleSpecs) {
            LOGGER.tracef("addModule: %s", moduleSpec.getModuleIdentifier());
            ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
            if (moduleSpecs.get(identifier) != null)
                throw MESSAGES.illegalStateModuleAlreadyExists(identifier);
            ModuleHolder moduleHolder = new ModuleHolder(moduleSpec);
            moduleSpecs.put(identifier, moduleHolder);
        }
    }

    @Override
    public void addModule(XBundleRevision brev, Module module) {
        synchronized (moduleSpecs) {
            LOGGER.tracef("addModule: %s", module.getIdentifier());
            ModuleIdentifier identifier = module.getIdentifier();
            if (moduleSpecs.get(identifier) != null)
                throw MESSAGES.illegalStateModuleAlreadyExists(identifier);
            ModuleHolder moduleHolder = new ModuleHolder(module);
            moduleSpecs.put(identifier, moduleHolder);
        }
    }

    @Override
    public void removeModule(XBundleRevision brev) {
        synchronized (moduleSpecs) {
            ModuleIdentifier identifier = brev.getModuleIdentifier();
            LOGGER.tracef("removeModule: %s", identifier);
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
    }

    @Override
    public ServiceName createModuleService(XBundleRevision brev, List<BundleWire> wires) {
        ModuleIdentifier identifier = brev.getModuleIdentifier();
        ServiceName moduleServiceName = getModuleServiceName(identifier);
        ServiceController<?> controller = serviceRegistry.getService(moduleServiceName);
        if (controller != null) {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            FutureServiceValue future = new FutureServiceValue(controller, State.REMOVED);
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // ignore
            }
        }
        try {
            Module module = loadModule(identifier);
            ValueService<Module> service = new ValueService<Module>(new ImmediateValue<Module>(module));
            ServiceTarget serviceTarget = UserBundleRevision.assertBundleRevision(brev).getServiceTarget();
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
        return IntegrationServices.MODULE_SERVICE.append(identifier.getName()).append(identifier.getSlot());
    }

    @Override
    public String toString() {
        return FrameworkModuleLoaderImpl.class.getSimpleName();
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
