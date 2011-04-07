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
 * 021101301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.Constants;

/**
 * The collection of public service names.
 * 
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public interface Services {

    /** The base name of all framework services */
    ServiceName FRAMEWORK_BASE = ServiceName.of(Constants.JBOSGI_PREFIX, "framework");
    /** The base name of all framework OSGi services */
    ServiceName FRAMEWORK_SERVICE_BASE = ServiceName.of(Constants.JBOSGI_PREFIX, "service");
    /** The base name of all framework OSGi services that were registered outside the OSGi layer */
    ServiceName FRAMEWORK_XSERVICE_BASE = ServiceName.of(Constants.JBOSGI_PREFIX, "xservice");
    /** The base name of all framnework plugin services */
    ServiceName FRAMEWORK_PLUGIN_BASE = FRAMEWORK_BASE.append("plugin");
    
    /** The {@link BundleManager} service name. */
    ServiceName BUNDLE_MANAGER = FRAMEWORK_BASE.append("bundlemanager");
    /** The {@link CoreServices} service name. */
    ServiceName CORE_SERVICES = FRAMEWORK_BASE.append("coreservices");
    /** The {@link FrameworkCreate} service name */
    ServiceName FRAMEWORK_CREATE = FRAMEWORK_BASE.append("CREATED");
    /** The {@link FrameworkInit} service name */
    ServiceName FRAMEWORK_INIT = FRAMEWORK_BASE.append("INITIAL");
    /** The {@link FrameworkActive} service name */
    ServiceName FRAMEWORK_ACTIVE = FRAMEWORK_BASE.append("ACTIVE");
    /** The {@link SystemBundleService} service name */
    ServiceName SYSTEM_BUNDLE = FRAMEWORK_BASE.append("systembundle");
    /** The {@link SystemContextService} service name */
    ServiceName SYSTEM_CONTEXT = FRAMEWORK_BASE.append("systemcontext");
    
    /** The {@link BundleDeploymentPlugin} service name */
    ServiceName BUNDLE_DEPLOYMENT_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("deployment");
    /** The {@link BundleStoragePlugin} service name */
    ServiceName BUNDLE_STORAGE_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("storage");
    /** The {@link FrameworkEventsPlugin} service name */
    ServiceName FRAMEWORK_EVENTS_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("frameworkevents");
    /** The {@link LifecycleInterceptorPlugin} service name */
    ServiceName LIFECYCLE_INTERCEPTOR_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("lifecycle");
    /** The {@link ModuleManagerPlugin} service name */
    ServiceName MODULE_MANGER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("modulemanager");
    /** The {@link NativeCodePlugin} service name */
    ServiceName NATIVE_CODE_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("nativecode");
    /** The {@link PackageAdminPlugin} service name */
    ServiceName PACKAGE_ADMIN_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("packageadmin");
    /** The {@link ResolverPlugin} service name */
    ServiceName RESOLVER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("resolver");
    /** The {@link ServiceManagerPlugin} service name */
    ServiceName SERVICE_MANAGER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("servicemanager");
    /** The {@link StartLevelPlugin} service name */
    ServiceName START_LEVEL_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("startlevel");
    /** The {@link SystemPackagesPlugin} service name */
    ServiceName SYSTEM_PACKAGES_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("systempackages");
    /** The {@link URLHandler} service name */
    ServiceName URL_HANDLER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("urlhandler");

}