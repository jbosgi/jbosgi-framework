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

import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.spi.ModuleLoaderPlugin;
import org.jboss.osgi.framework.spi.SystemPathsPlugin;
import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.launch.Framework;

/**
 * Represents the state of the {@link Framework}.
 *
 * It is used by the various {@link AbstractFrameworkService}s as well as the {@link FrameworkProxy}.
 * The state is never given to the client.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class FrameworkState {

    private final BundleManagerPlugin bundleManager;

    final InjectedValue<FrameworkCoreServices> injectedCoreServices = new InjectedValue<FrameworkCoreServices>();
    final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();
    final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    final InjectedValue<FrameworkEventsPlugin> injectedFrameworkEvents = new InjectedValue<FrameworkEventsPlugin>();
    final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    final InjectedValue<ModuleLoaderPlugin> injectedModuleLoader = new InjectedValue<ModuleLoaderPlugin>();
    final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    final InjectedValue<ResolverPlugin> injectedResolverPlugin = new InjectedValue<ResolverPlugin>();
    final InjectedValue<ServiceManagerPlugin> injectedServiceManager = new InjectedValue<ServiceManagerPlugin>();
    final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    final InjectedValue<SystemPathsPlugin> injectedSystemPaths = new InjectedValue<SystemPathsPlugin>();

    FrameworkState(BundleManagerPlugin bundleManager) {
        this.bundleManager = bundleManager;
    }

    BundleManagerPlugin getBundleManager() {
        return bundleManager;
    }

    DeploymentFactoryPlugin getDeploymentFactoryPlugin() {
        return injectedDeploymentFactory.getValue();
    }

    BundleStoragePlugin getBundleStoragePlugin() {
        return injectedBundleStorage.getValue();
    }

    FrameworkCoreServices getCoreServices() {
        return injectedCoreServices.getValue();
    }

    FrameworkEventsPlugin getFrameworkEventsPlugin() {
        return injectedFrameworkEvents.getValue();
    }

    ModuleManagerPlugin getModuleManagerPlugin() {
        return injectedModuleManager.getValue();
    }

    ModuleLoaderPlugin getModuleLoaderPlugin() {
        return injectedModuleLoader.getValue();
    }

    NativeCodePlugin getNativeCodePlugin() {
        return injectedNativeCode.getValue();
    }

    ResolverPlugin getResolverPlugin() {
        return injectedResolverPlugin.getValue();
    }

    XEnvironment getEnvironment() {
        return injectedEnvironment.getValue();
    }

    ServiceManagerPlugin getServiceManagerPlugin() {
        return injectedServiceManager.getValue();
    }

    SystemBundleState getSystemBundle() {
        return injectedSystemBundle.getValue();
    }

    SystemPathsPlugin getSystemPathsPlugin() {
        return injectedSystemPaths.getValue();
    }
}
