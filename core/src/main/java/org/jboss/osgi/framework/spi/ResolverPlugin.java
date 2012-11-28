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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.ResolverImpl;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
public class ResolverPlugin extends AbstractIntegrationService<XResolver> implements XResolver {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<NativeCode> injectedNativeCode = new InjectedValue<NativeCode>();
    private final InjectedValue<ModuleManager> injectedModuleManager = new InjectedValue<ModuleManager>();
    private final InjectedValue<FrameworkModuleLoader> injectedModuleLoader = new InjectedValue<FrameworkModuleLoader>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    private XResolver resolver;

    public ResolverPlugin() {
        super(Services.RESOLVER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<XResolver> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(IntegrationServices.NATIVE_CODE_PLUGIN, NativeCode.class, injectedNativeCode);
        builder.addDependency(IntegrationServices.MODULE_MANGER, ModuleManager.class, injectedModuleManager);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_LOADER, FrameworkModuleLoader.class, injectedModuleLoader);
        builder.addDependency(IntegrationServices.LOCK_MANAGER, LockManager.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        NativeCode nativeCode = injectedNativeCode.getValue();
        ModuleManager moduleManager = injectedModuleManager.getValue();
        FrameworkModuleLoader moduleLoader = injectedModuleLoader.getValue();
        XEnvironment env = injectedEnvironment.getValue();
        LockManager lockManager = injectedLockManager.getValue();
        resolver = new ResolverImpl(bundleManager, nativeCode, moduleManager, moduleLoader, env, lockManager);
    }

    @Override
    public XResolver getValue() {
        return this;
    }

    public XResolveContext createResolveContext(XEnvironment environment, Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) {
        return resolver.createResolveContext(environment, mandatory, optional);
    }

    public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
        return resolver.resolve(context);
    }

    public Map<Resource, Wiring> resolveAndApply(XResolveContext context) throws ResolutionException {
        return resolver.resolveAndApply(context);
    }

}
