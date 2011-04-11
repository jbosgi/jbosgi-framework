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
import org.jboss.osgi.framework.ServiceNames;

/**
 * The collection of internal service names.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
interface InternalServices {

    /** The base name of all framnework plugin services */
    ServiceName FRAMEWORK_PLUGIN_BASE = ServiceNames.FRAMEWORK_BASE_NAME.append("plugin");

    /** The {@link CoreServices} service name. */
    ServiceName CORE_SERVICES = ServiceNames.FRAMEWORK_BASE_NAME.append("coreservices");

    /** The {@link DeploymentFactoryPlugin} service name */
    ServiceName AUTOINSTALL_PROCESSOR = FRAMEWORK_PLUGIN_BASE.append("autoinstallprocessor");
    /** The {@link BundleStoragePlugin} service name */
    ServiceName BUNDLE_STORAGE_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("storage");
    /** The {@link DeploymentFactoryPlugin} service name */
    ServiceName DEPLOYMENT_FACTORY_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("deployment");
    /** The {@link FrameworkEventsPlugin} service name */
    ServiceName FRAMEWORK_EVENTS_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("frameworkevents");
    /** The {@link LifecycleInterceptorPlugin} service name */
    ServiceName LIFECYCLE_INTERCEPTOR_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("lifecycle");
    /** The {@link ModuleManagerPlugin} service name */
    ServiceName MODULE_MANGER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("modulemanager");
    /** The {@link NativeCodePlugin} service name */
    ServiceName NATIVE_CODE_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("nativecode");
    /** The {@link ResolverPlugin} service name */
    ServiceName RESOLVER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("resolver");
    /** The {@link ServiceManagerPlugin} service name */
    ServiceName SERVICE_MANAGER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("servicemanager");
    /** The {@link SystemPackagesPlugin} service name */
    ServiceName SYSTEM_PACKAGES_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("systempackages");
    /** The {@link WebXMLVerifierInterceptor} service name */
    ServiceName WEBXML_VERIFIER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("webxmlverifier");
    /** The {@link URLHandler} service name */
    ServiceName URL_HANDLER_PLUGIN = FRAMEWORK_PLUGIN_BASE.append("urlhandler");
}