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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.ResolverImpl;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolver;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
public class ResolverPlugin extends AbstractIntegrationService<XResolver> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<NativeCode> injectedNativeCode = new InjectedValue<NativeCode>();
    private final InjectedValue<ModuleManager> injectedModuleManager = new InjectedValue<ModuleManager>();
    private final InjectedValue<FrameworkModuleLoader> injectedModuleLoader = new InjectedValue<FrameworkModuleLoader>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();

    public ResolverPlugin() {
        super(Services.RESOLVER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<XResolver> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(IntegrationServices.NATIVE_CODE_PLUGIN, NativeCode.class, injectedNativeCode);
        builder.addDependency(IntegrationServices.MODULE_MANGER_PLUGIN, ModuleManager.class, injectedModuleManager);
        builder.addDependency(IntegrationServices.FRAMEWORK_MODULE_LOADER_PLUGIN, FrameworkModuleLoader.class, injectedModuleLoader);
        builder.addDependency(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected XResolver createServiceValue(StartContext startContext) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        NativeCode nativeCode = injectedNativeCode.getValue();
        ModuleManager moduleManager = injectedModuleManager.getValue();
        FrameworkModuleLoader moduleLoader = injectedModuleLoader.getValue();
        XEnvironment env = injectedEnvironment.getValue();
        LockManager lockManager = injectedLockManager.getValue();
        return new ResolverImpl(bundleManager, nativeCode, moduleManager, moduleLoader, env, lockManager);
    }
}
