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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.osgi.framework.launch.Framework;

/**
 * A builder for the {@link Framework} implementation.
 * Provides hooks for various integration aspects.
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

    public void createFrameworkServices(ServiceTarget serviceTarget) {
        assertNotClosed();
        try {
            createFrameworkServicesInternal(serviceTarget, true);
        } finally {
            closed = true;
        }
    }

    void createFrameworkServicesInternal(ServiceTarget serviceTarget, boolean firstInit) {
        URLHandlerPlugin.addService(serviceTarget);

        BundleManager bundleManager = BundleManager.addService(serviceTarget, this);
        FrameworkState frameworkState = FrameworkCreate.addService(serviceTarget, bundleManager);
        FrameworkInit.addService(serviceTarget);
        FrameworkActive.addService(serviceTarget);
        SystemBundleService.addService(serviceTarget, frameworkState);

        BundleDeploymentPlugin.addService(serviceTarget);
        BundleStoragePlugin.addService(serviceTarget, firstInit);
        CoreServices.addService(serviceTarget);
        DefaultDeployerServiceProvider.addService(serviceTarget);
        DefaultFrameworkModuleProvider.addService(serviceTarget);
        DefaultModuleLoaderProvider.addService(serviceTarget);
        DefaultSystemModuleProvider.addService(serviceTarget);
        FrameworkEventsPlugin.addService(serviceTarget);
        ModuleManagerPlugin.addService(serviceTarget);
        NativeCodePlugin.addService(serviceTarget);
        PackageAdminPlugin.addService(serviceTarget);
        ResolverPlugin.addService(serviceTarget);
        ServiceManagerPlugin.addService(serviceTarget);
        StartLevelPlugin.addService(serviceTarget);
        SystemContextService.addService(serviceTarget);
        SystemPackagesPlugin.addService(serviceTarget, this);
        WebXMLVerifierInterceptor.addService(serviceTarget);
    }

    private void assertNotClosed() {
        if (closed == true)
            throw new IllegalStateException("Framework builder already closed");
    }
}