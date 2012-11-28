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
package org.jboss.osgi.framework.spi;

import java.util.List;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.internal.ModuleManagerImpl;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.wiring.BundleWire;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
public class ModuleManagerPlugin extends AbstractIntegrationService<ModuleManager> implements ModuleManager {

    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<SystemPaths> injectedSystemPaths = new InjectedValue<SystemPaths>();
    private final InjectedValue<FrameworkModuleProvider> injectedFrameworkModule = new InjectedValue<FrameworkModuleProvider>();
    private final InjectedValue<FrameworkModuleLoader> injectedModuleLoader = new InjectedValue<FrameworkModuleLoader>();
    private ModuleManager moduleManager;

    public ModuleManagerPlugin() {
        super(IntegrationServices.MODULE_MANGER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<ModuleManager> builder) {
        builder.addDependency(IntegrationServices.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_LOADER, FrameworkModuleLoader.class, injectedModuleLoader);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_PROVIDER, FrameworkModuleProvider.class, injectedFrameworkModule);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS, SystemPaths.class, injectedSystemPaths);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        FrameworkModuleProvider moduleProvider = injectedFrameworkModule.getValue();
        XEnvironment env = injectedEnvironment.getValue();
        SystemPaths syspaths = injectedSystemPaths.getValue();
        FrameworkModuleLoader moduleLoader = injectedModuleLoader.getValue();
        moduleManager = new ModuleManagerImpl(env, syspaths, moduleProvider, moduleLoader);
    }

    @Override
    public ModuleManager getValue() {
        return this;
    }

    public Module getModule(ModuleIdentifier identifier) {
        return moduleManager.getModule(identifier);
    }

    public XBundle getBundleState(Class<?> clazz) {
        return moduleManager.getBundleState(clazz);
    }

    public Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        return moduleManager.loadModule(identifier);
    }

    public void removeModule(XBundleRevision brev, ModuleIdentifier identifier) {
        moduleManager.removeModule(brev, identifier);
    }

    public Module getFrameworkModule() {
        return moduleManager.getFrameworkModule();
    }

    public ModuleIdentifier getModuleIdentifier(XBundleRevision brev) {
        return moduleManager.getModuleIdentifier(brev);
    }

    public ModuleIdentifier addModule(XBundleRevision brev, List<BundleWire> wires) {
        return moduleManager.addModule(brev, wires);
    }

}
