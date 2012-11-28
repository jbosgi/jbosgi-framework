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

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.framework.internal.FrameworkModuleLoaderImpl;
import org.jboss.osgi.resolver.XBundleRevision;

/**
 * Integration point for the {@link ModuleLoader}.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
public class FrameworkModuleLoaderPlugin extends AbstractIntegrationService<FrameworkModuleLoader> implements FrameworkModuleLoader  {

    private FrameworkModuleLoader moduleLoader;

    public FrameworkModuleLoaderPlugin() {
        super(IntegrationServices.FRAMEWORK_MODULE_LOADER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkModuleLoader> builder) {
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        ServiceContainer serviceContainer = context.getController().getServiceContainer();
        moduleLoader = new FrameworkModuleLoaderImpl(serviceContainer);
    }


    @Override
    public FrameworkModuleLoader getValue() {
        return this;
    }

    public ModuleLoader getModuleLoader() {
        return moduleLoader.getModuleLoader();
    }

    public ModuleIdentifier getModuleIdentifier(XBundleRevision brev) {
        return moduleLoader.getModuleIdentifier(brev);
    }

    public void addIntegrationDependencies(ModuleSpecBuilderContext context) {
        moduleLoader.addIntegrationDependencies(context);
    }

    public void addModuleSpec(XBundleRevision brev, ModuleSpec moduleSpec) {
        moduleLoader.addModuleSpec(brev, moduleSpec);
    }

    public void addModule(XBundleRevision brev, Module module) {
        moduleLoader.addModule(brev, module);
    }

    public ServiceName createModuleService(XBundleRevision brev, ModuleIdentifier identifier) {
        return moduleLoader.createModuleService(brev, identifier);
    }

    public void removeModule(XBundleRevision brev, ModuleIdentifier identifier) {
        moduleLoader.removeModule(brev, identifier);
    }

    public ServiceName getModuleServiceName(ModuleIdentifier identifier) {
        return moduleLoader.getModuleServiceName(identifier);
    }

}
