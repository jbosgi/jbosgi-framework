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

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XFragmentHostRequirement;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResolverCallback;
import org.jboss.osgi.resolver.XResolverException;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.resolver.XWire;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 06-Jul-2009
 */
final class LegacyResolverPlugin extends AbstractPluginService<LegacyResolverPlugin> {

    // Provide logging
    final Logger log = Logger.getLogger(LegacyResolverPlugin.class);

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    private final XResolverFactory factory;
    private XResolver resolver;


    static void addService(ServiceTarget serviceTarget) {
        LegacyResolverPlugin service = new LegacyResolverPlugin();
        ServiceBuilder<LegacyResolverPlugin> builder = serviceTarget.addService(InternalServices.LEGACY_RESOLVER_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, service.injectedModuleManager);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, service.injectedNativeCode);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private LegacyResolverPlugin() {
        factory = XResolverFactory.getInstance(getClass().getClassLoader());
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        resolver = factory.newResolver();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        resolver = null;
    }

    @Override
    public LegacyResolverPlugin getValue() {
        return this;
    }

    /**
     * Get the resolver instance
     */
    XResolver getResolver() {
        return resolver;
    }

    /**
     * Get a new module builder instance
     */
    XModuleBuilder getModuleBuilder() {
        return factory.newModuleBuilder();
    }

    /**
     * Add a module to the resolver.
     *
     * @param resModule the resolver module
     */
    void addModule(XModule resModule) {
        resolver.addModule(resModule);
    }
}