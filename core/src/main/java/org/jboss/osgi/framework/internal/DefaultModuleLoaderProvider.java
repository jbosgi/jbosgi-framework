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

import static org.jboss.osgi.framework.Constants.JBOSGI_PREFIX;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.ModuleLoaderProvider;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XResource;

/**
 * Integration point for the {@link ModuleLoader}.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
final class DefaultModuleLoaderProvider extends ModuleLoader implements ModuleLoaderProvider {

    private Map<ModuleIdentifier, ModuleHolder> moduleSpecs = new ConcurrentHashMap<ModuleIdentifier, ModuleHolder>();

    static void addService(ServiceTarget serviceTarget) {
        ModuleLoaderProvider service = new DefaultModuleLoaderProvider();
        ServiceBuilder<ModuleLoaderProvider> builder = serviceTarget.addService(IntegrationServices.MODULE_LOADER_PROVIDER, service);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultModuleLoaderProvider() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        LOGGER.debugf("Starting: %s", context.getController().getName());
    }

    @Override
    public void stop(StopContext context) {
        LOGGER.debugf("Stopping: %s", context.getController().getName());
    }

    @Override
    public ModuleLoaderProvider getValue() {
        return this;
    }

    @Override
    public ModuleLoader getModuleLoader() {
        return this;
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
    public ModuleIdentifier getModuleIdentifier(XResource resource, int rev) {
        XIdentityCapability icap = resource.getIdentityCapability();
        String name = icap.getSymbolicName();
        String slot = icap.getVersion() + (rev > 0 ? "-rev" + rev : "");
        return ModuleIdentifier.create(JBOSGI_PREFIX + "." + name, slot);
    }

    @Override
    public void addModule(ModuleSpec moduleSpec) {
        LOGGER.tracef("addModule: %s", moduleSpec.getModuleIdentifier());
        ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
        if (moduleSpecs.get(identifier) != null)
            throw MESSAGES.illegalStateModuleAlreadyExists(identifier);
        ModuleHolder moduleHolder = new ModuleHolder(moduleSpec);
        moduleSpecs.put(identifier, moduleHolder);
    }

    @Override
    public void addModule(Module module) {
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
        moduleSpecs.remove(identifier);
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
    public String toString() {
        return DefaultModuleLoaderProvider.class.getSimpleName();
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