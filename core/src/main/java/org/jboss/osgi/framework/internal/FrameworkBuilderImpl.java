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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.Constants.PROPERTY_FRAMEWORK_BOOTSTRAP_THREADS;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.modules.Module;
import org.jboss.modules.log.JDKModuleLogger;
import org.jboss.modules.log.ModuleLogger;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.spi.BundleLifecyclePlugin;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.DeploymentProviderPlugin;
import org.jboss.osgi.framework.spi.LifecycleInterceptorPlugin;
import org.jboss.osgi.framework.spi.FrameworkModuleLoaderPlugin;
import org.jboss.osgi.framework.spi.BundleStoragePlugin;
import org.jboss.osgi.framework.spi.EnvironmentPlugin;
import org.jboss.osgi.framework.spi.FrameworkBuilder;
import org.jboss.osgi.framework.spi.FrameworkModuleProviderPlugin;
import org.jboss.osgi.framework.spi.IntegrationService;
import org.jboss.osgi.framework.spi.LockManagerPlugin;
import org.jboss.osgi.framework.spi.ModuleManagerPlugin;
import org.jboss.osgi.framework.spi.NativeCodePlugin;
import org.jboss.osgi.framework.spi.PackageAdminPlugin;
import org.jboss.osgi.framework.spi.ResolverPlugin;
import org.jboss.osgi.framework.spi.ServiceManagerPlugin;
import org.jboss.osgi.framework.spi.StartLevelPlugin;
import org.jboss.osgi.framework.spi.SystemPathsPlugin;
import org.jboss.osgi.framework.spi.SystemServicesPlugin;
import org.osgi.framework.launch.Framework;

/**
 * A builder for the {@link Framework} implementation.
 *
 * Provides hooks for various integration aspects.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Mar-2011
 */
public final class FrameworkBuilderImpl implements FrameworkBuilder {

    private final Map<String, Object> initialProperties = new HashMap<String, Object>();
    private final Map<FrameworkPhase, Map<ServiceName, IntegrationService<?>>> integrationServices;
    private final Mode initialMode;
    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;
    private boolean closed;

    public FrameworkBuilderImpl(Map<String, Object> props, Mode initialMode) {
        this.initialMode = initialMode;
        integrationServices = new HashMap<FrameworkPhase, Map<ServiceName, IntegrationService<?>>>();
        if (props != null) {
            initialProperties.putAll(props);
        }
    }

    @Override
    public Object getProperty(String key) {
        return getProperty(key, null);
    }

    @Override
    public Object getProperty(String key, Object defaultValue) {
        Object value = initialProperties.get(key);
        return value != null ? value : defaultValue;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(initialProperties);
    }

    @Override
    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {
        assertNotClosed();
        this.serviceContainer = serviceContainer;
    }

    @Override
    public ServiceContainer createServiceContainer() {
        Object maxThreads = getProperty(PROPERTY_FRAMEWORK_BOOTSTRAP_THREADS);
        if (maxThreads == null)
            maxThreads = SecurityActions.getSystemProperty(PROPERTY_FRAMEWORK_BOOTSTRAP_THREADS, null);
        if (maxThreads != null) {
            return ServiceContainer.Factory.create(new Integer("" + maxThreads), 30L, TimeUnit.SECONDS);
        } else {
            return ServiceContainer.Factory.create();
        }
    }

    @Override
    public ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    @Override
    public void setServiceTarget(ServiceTarget serviceTarget) {
        assertNotClosed();
        this.serviceTarget = serviceTarget;
    }

    @Override
    public Mode getInitialMode() {
        return initialMode;
    }

    @Override
    public Framework createFramework() {
        assertNotClosed();
        return new FrameworkProxy(this);
    }

    @Override
    public void registerIntegrationService(FrameworkPhase phase, IntegrationService<?> service) {
        assertNotClosed();
        Map<ServiceName, IntegrationService<?>> phaseServices = integrationServices.get(phase);
        if (phaseServices == null) {
            phaseServices = new HashMap<ServiceName, IntegrationService<?>>();
            integrationServices.put(phase, phaseServices);
        }
        phaseServices.put(service.getServiceName(), service);
    }

    @Override
    public BundleManager createFrameworkServices(ServiceContainer serviceContainer, boolean firstInit) {

        integrationServices.clear();
        closed = false;

        // Do this first so this URLStreamHandlerFactory gets installed
        registerIntegrationService(FrameworkPhase.CREATE, new URLHandlerPlugin());

        // Setup the logging system for jboss-modules
        if (getProperty(ModuleLogger.class.getName()) == null) {
            Module.setModuleLogger(new JDKModuleLogger());
        }

        BundleManagerImpl bundleManager = new BundleManagerImpl(serviceContainer, this);
        FrameworkState frameworkState = new FrameworkState(bundleManager);

        registerIntegrationService(FrameworkPhase.CREATE, bundleManager);
        registerIntegrationService(FrameworkPhase.CREATE, new FrameworkCreate(frameworkState));
        registerIntegrationService(FrameworkPhase.CREATE, new FrameworkCreate.FrameworkCreated(initialMode));
        registerIntegrationService(FrameworkPhase.CREATE, new BundleLifecyclePlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new FrameworkModuleProviderPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new FrameworkModuleLoaderPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new StartLevelPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new SystemPathsPlugin(this));
        registerIntegrationService(FrameworkPhase.CREATE, new SystemServicesPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new FrameworkCoreServices());
        registerIntegrationService(FrameworkPhase.CREATE, new FrameworkEventsPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new BundleStoragePlugin(firstInit));
        registerIntegrationService(FrameworkPhase.CREATE, new DeploymentProviderPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new EnvironmentPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new LifecycleInterceptorPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new LockManagerPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new ModuleManagerPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new NativeCodePlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new PackageAdminPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new ResolverPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new ServiceManagerPlugin());
        registerIntegrationService(FrameworkPhase.CREATE, new SystemBundlePlugin(frameworkState));
        registerIntegrationService(FrameworkPhase.CREATE, new SystemContextPlugin());

        registerIntegrationService(FrameworkPhase.INIT, new FrameworkInit());
        registerIntegrationService(FrameworkPhase.INIT, new FrameworkInit.FrameworkInitialized(initialMode));
        registerIntegrationService(FrameworkPhase.INIT, new BootstrapBundlesInstallPlugin());
        registerIntegrationService(FrameworkPhase.INIT, new PersistentBundlesInstallPlugin());

        registerIntegrationService(FrameworkPhase.ACTIVE, new FrameworkActive());
        registerIntegrationService(FrameworkPhase.ACTIVE, new FrameworkActive.FrameworkActivated(initialMode));

        return bundleManager;
    }

    @Override
    public void installServices(FrameworkPhase phase, ServiceTarget serviceTarget, ServiceListener<Object> listener) {
        assertNotClosed();
        Map<ServiceName, IntegrationService<?>> phaseServices = integrationServices.get(phase);
        for (IntegrationService<?> service : phaseServices.values()) {
            service.install(serviceTarget, listener);
        }
        FrameworkPhase[] phases = FrameworkPhase.values();
        if (phase == phases[phases.length - 1]) {
            closed = true;
        }
    }

    private void assertNotClosed() {
        if (closed == true)
            throw MESSAGES.illegalStateFrameworkBuilderClosed();
    }
}
