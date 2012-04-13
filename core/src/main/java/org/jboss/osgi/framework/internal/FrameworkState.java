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

import org.jboss.msc.value.InjectedValue;
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

    private final BundleManager bundleManager;

    final InjectedValue<FrameworkCoreServices> injectedCoreServices = new InjectedValue<FrameworkCoreServices>();
    final InjectedValue<DeploymentFactoryPlugin> injectedDeploymentFactory = new InjectedValue<DeploymentFactoryPlugin>();
    final InjectedValue<BundleStoragePlugin> injectedBundleStorage = new InjectedValue<BundleStoragePlugin>();
    final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    final InjectedValue<FrameworkEventsPlugin> injectedFrameworkEvents = new InjectedValue<FrameworkEventsPlugin>();
    final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    final InjectedValue<ResolverPlugin> injectedResolverPlugin = new InjectedValue<ResolverPlugin>();
    final InjectedValue<ServiceManagerPlugin> injectedServiceManager = new InjectedValue<ServiceManagerPlugin>();
    final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    private int startStopOptions;

    FrameworkState(BundleManager bundleManager) {
        this.bundleManager = bundleManager;
    }

    BundleManager getBundleManager() {
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
}