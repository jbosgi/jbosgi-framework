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

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.modules.ModuleActivator;
import org.jboss.osgi.modules.ModuleContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * A module activator that is called when the module gets loaded by the OSGi layer.
 *
 * @author thomas.diesler@jboss.com
 * @since 13-Jul-2010
 */
final class ModuleActivatorBridge implements BundleActivator {

    private BundleManagerPlugin bundleManager;
    private ModuleActivator moduleActivator;
    private ModuleContext moduleContext;

    ModuleActivatorBridge(ModuleActivator moduleActivator) {
        this.moduleActivator = moduleActivator;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        AbstractBundleState bundleState = AbstractBundleContext.assertBundleContext(context).getBundleState();
        bundleManager = bundleState.getBundleManager();

        ModuleManagerPlugin moduleManager = bundleManager.getFrameworkState().getModuleManagerPlugin();
        ModuleIdentifier identifier = bundleState.getModuleIdentifier();
        Module module = moduleManager.getModule(identifier);

        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        BundleContext systemContext = bundleManager.getSystemBundle().getBundleContext();

        moduleContext = new ModuleContextImpl(serviceContainer, module, systemContext, context.getBundle());
        moduleActivator.start(moduleContext);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        moduleActivator.stop(moduleContext);
    }

    /**
     * A the context for Module/OSGi integration.
     *
     * @author thomas.diesler@jboss.com
     * @since 05-Aug-2010
     */
    class ModuleContextImpl implements ModuleContext {

        private ServiceContainer serviceContainer;
        private Module module;
        private BundleContext systemContext;
        private Bundle bundle;

        ModuleContextImpl(ServiceContainer serviceContainer, Module module, BundleContext systemContext, Bundle bundle) {
            assert serviceContainer != null : "Null serviceContainer";
            assert module != null : "Null module";
            assert systemContext != null : "Null systemContext";
            assert bundle != null : "Null bundle";
            this.serviceContainer = serviceContainer;
            this.module = module;
            this.systemContext = systemContext;
            this.bundle = bundle;
        }

        @Override
        public ServiceName getServiceName(Class<?> service) {
            if (service == null)
                throw MESSAGES.illegalArgumentNull("service");
            return ServiceState.createXServiceName(service.getName());
        }

        @Override
        public ServiceContainer getServiceContainer() {
            return serviceContainer;
        }

        @Override
        public Module getModule() {
            return module;
        }

        @Override
        public BundleContext getSystemContext() {
            return systemContext;
        }

        @Override
        public Bundle getBundle() {
            return bundle;
        }
    }
}
