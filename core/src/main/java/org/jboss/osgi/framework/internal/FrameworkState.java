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
import org.jboss.osgi.framework.spi.BundleStorage;
import org.jboss.osgi.framework.spi.DeploymentProvider;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.FrameworkModuleLoader;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.NativeCode;
import org.jboss.osgi.framework.spi.ServiceManager;
import org.jboss.osgi.framework.spi.SystemPaths;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolver;
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

    private final BundleManagerImpl bundleManager;

    final InjectedValue<BundleStorage> injectedBundleStorage = new InjectedValue<BundleStorage>();
    final InjectedValue<DeploymentProvider> injectedDeploymentFactory = new InjectedValue<DeploymentProvider>();
    final InjectedValue<FrameworkCoreServices> injectedCoreServices = new InjectedValue<FrameworkCoreServices>();
    final InjectedValue<FrameworkEvents> injectedFrameworkEvents = new InjectedValue<FrameworkEvents>();
    final InjectedValue<FrameworkModuleLoader> injectedModuleLoader = new InjectedValue<FrameworkModuleLoader>();
    final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    final InjectedValue<ModuleManager> injectedModuleManager = new InjectedValue<ModuleManager>();
    final InjectedValue<NativeCode> injectedNativeCode = new InjectedValue<NativeCode>();
    final InjectedValue<ServiceManager> injectedServiceManager = new InjectedValue<ServiceManager>();
    final InjectedValue<SystemPaths> injectedSystemPaths = new InjectedValue<SystemPaths>();
    final InjectedValue<XBundle> injectedSystemBundle = new InjectedValue<XBundle>();
    final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    final InjectedValue<XResolver> injectedResolverPlugin = new InjectedValue<XResolver>();

    FrameworkState(BundleManagerImpl bundleManager) {
        this.bundleManager = bundleManager;
    }

    BundleManagerImpl getBundleManager() {
        return bundleManager;
    }

    BundleStorage getBundleStorage() {
        return injectedBundleStorage.getValue();
    }

    DeploymentProvider getDeploymentFactoryPlugin() {
        return injectedDeploymentFactory.getValue();
    }

    FrameworkCoreServices getCoreServices() {
        return injectedCoreServices.getValue();
    }

    FrameworkEvents getFrameworkEventsPlugin() {
        return injectedFrameworkEvents.getValue();
    }

    FrameworkModuleLoader getFrameworkModuleLoader() {
        return injectedModuleLoader.getValue();
    }

    LockManager getLockManager() {
        return injectedLockManager.getValue();
    }

    ModuleManager getModuleManager() {
        return injectedModuleManager.getValue();
    }

    NativeCode getNativeCodePlugin() {
        return injectedNativeCode.getValue();
    }

    ServiceManager getServiceManagerPlugin() {
        return injectedServiceManager.getValue();
    }

    SystemPaths getSystemPathsPlugin() {
        return injectedSystemPaths.getValue();
    }

    XBundle getSystemBundle() {
        return injectedSystemBundle.getValue();
    }

    XEnvironment getEnvironment() {
        return injectedEnvironment.getValue();
    }

    XResolver getResolverPlugin() {
        return injectedResolverPlugin.getValue();
    }
}
