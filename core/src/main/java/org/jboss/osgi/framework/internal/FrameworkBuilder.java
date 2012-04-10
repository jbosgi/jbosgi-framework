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

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.log.JDKModuleLogger;
import org.jboss.modules.log.ModuleLogger;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.launch.Framework;

/**
 * A builder for the {@link Framework} implementation. Provides hooks for various integration aspects.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Mar-2011
 */
public final class FrameworkBuilder {

    private final Map<String, Object> initialProperties = new HashMap<String, Object>();
    private Set<ServiceName> providedServices = new HashSet<ServiceName>();
    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;
    private boolean closed;

    public FrameworkBuilder(Map<String, Object> props) {
        if (props != null)
            initialProperties.putAll(props);
    }

    public Object getProperty(String key) {
        return getProperty(key, null);
    }

    public Object getProperty(String key, Object defaultValue) {
        Object value = initialProperties.get(key);
        return value != null ? value : defaultValue;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(initialProperties);
    }

    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    public void setServiceContainer(ServiceContainer serviceContainer) {
        assertNotClosed();
        this.serviceContainer = serviceContainer;
    }

    public ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    public void setServiceTarget(ServiceTarget serviceTarget) {
        assertNotClosed();
        this.serviceTarget = serviceTarget;
    }

    public boolean isProvidedService(ServiceName serviceName) {
        return providedServices.contains(serviceName);
    }

    public void addProvidedService(ServiceName serviceName) {
        assertNotClosed();
        providedServices.add(serviceName);
    }

    public Set<ServiceName> getProvidedServices() {
        return Collections.unmodifiableSet(providedServices);
    }

    public Framework createFramework() {
        assertNotClosed();
        try {
            return new FrameworkProxy(this);
        } finally {
            closed = true;
        }
    }

    public void createFrameworkServices(Mode initialMode, boolean firstInit) {
        assertNotClosed();
        try {
            createFrameworkServicesInternal(serviceTarget, initialMode, firstInit);
        } finally {
            closed = true;
        }
    }

    void createFrameworkServicesInternal(ServiceTarget serviceTarget, Mode initialMode, boolean firstInit) {

        // Do this first so this URLStreamHandlerFactory gets installed
        URLHandlerPlugin.addService(serviceTarget);

        // Setup the logging system for jboss-modules
        ModuleLogger logger = (ModuleLogger) getProperty(ModuleLogger.class.getName());
        if (logger == null) {
            Module.setModuleLogger(new JDKModuleLogger());
        }
        
        BundleManager bundleManager = BundleManager.addService(serviceTarget, this);
        FrameworkState frameworkState = FrameworkCreate.addService(serviceTarget, bundleManager);

        DeploymentFactoryPlugin.addService(serviceTarget);
        BundleStoragePlugin.addService(serviceTarget, firstInit);
        CoreServices.addService(serviceTarget);
        DefaultEnvironmentPlugin.addService(serviceTarget);
        FrameworkActive.addService(serviceTarget);
        FrameworkActivator.addService(serviceTarget, initialMode);
        FrameworkEventsPlugin.addService(serviceTarget);
        FrameworkInit.addService(serviceTarget);
        LifecycleInterceptorPlugin.addService(serviceTarget);
        ModuleManagerPlugin.addService(serviceTarget);
        NativeCodePlugin.addService(serviceTarget);
        PackageAdminPlugin.addService(serviceTarget);
        PersistentBundlesInstaller.addService(serviceTarget);
        DefaultResolverPlugin.addService(serviceTarget);
        ServiceManagerPlugin.addService(serviceTarget);
        StartLevelPlugin.addService(serviceTarget);
        SystemBundleService.addService(serviceTarget, frameworkState);
        SystemContextService.addService(serviceTarget);
        WebXMLVerifierInterceptor.addService(serviceTarget);

        if (isProvidedService(Services.AUTOINSTALL_PROVIDER) == false)
            DefaultAutoInstallProvider.addService(serviceTarget);
        if (isProvidedService(Services.BUNDLE_INSTALL_PROVIDER) == false)
            DefaultBundleInstallProvider.addService(serviceTarget);
        if (isProvidedService(Services.FRAMEWORK_MODULE_PROVIDER) == false)
            DefaultFrameworkModuleProvider.addService(serviceTarget);
        if (isProvidedService(Services.MODULE_LOADER_PROVIDER) == false)
            DefaultModuleLoaderProvider.addService(serviceTarget);
        if (isProvidedService(Services.SYSTEM_PATHS_PROVIDER) == false)
            DefaultSystemPathsProvider.addService(serviceTarget, this);
        if (isProvidedService(Services.SYSTEM_SERVICES_PROVIDER) == false)
            DefaultSystemServicesProvider.addService(serviceTarget);
    }

    private void assertNotClosed() {
        if (closed == true)
            throw MESSAGES.illegalStateFrameworkBuilderClosed();
    }
}