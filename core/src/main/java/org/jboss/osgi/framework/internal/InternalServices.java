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
import org.jboss.osgi.framework.Services;
import org.osgi.service.resolver.Resolver;

/**
 * The collection of internal service names.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public interface InternalServices {

    /** The base name of all internal services */
    ServiceName INTERNAL_SERVICE_BASE = Services.JBOSGI_BASE_NAME.append("internal");

    /** The {@link BundleStoragePlugin} service name */
    ServiceName BUNDLE_STORAGE_PLUGIN = INTERNAL_SERVICE_BASE.append("BundleStorage");
    /** The {@link FrameworkCoreServices} service name. */
    ServiceName FRAMEWORK_CORE_SERVICES = INTERNAL_SERVICE_BASE.append("CoreServices");
    /** The {@link DeploymentFactoryPlugin} service name */
    ServiceName DEPLOYMENT_FACTORY_PLUGIN = INTERNAL_SERVICE_BASE.append("DeploymentFactory");
    /** The {@link FrameworkEventsPlugin} service name */
    ServiceName FRAMEWORK_EVENTS_PLUGIN = INTERNAL_SERVICE_BASE.append("FrameworkEvents");
    /** The {@link LifecycleInterceptorPlugin} service name */
    ServiceName LIFECYCLE_INTERCEPTOR_PLUGIN = INTERNAL_SERVICE_BASE.append("LifecycleInterceptor");
    /** The {@link ModuleManagerPlugin} service name */
    ServiceName MODULE_MANGER_PLUGIN = INTERNAL_SERVICE_BASE.append("ModuleManager");
    /** The {@link NativeCodePlugin} service name */
    ServiceName NATIVE_CODE_PLUGIN = INTERNAL_SERVICE_BASE.append("NativeCode");
    /** The {@link Resolver} service name */
    ServiceName RESOLVER_PLUGIN = INTERNAL_SERVICE_BASE.append("Resolver");
    /** The {@link ServiceManagerPlugin} service name */
    ServiceName SERVICE_MANAGER_PLUGIN = INTERNAL_SERVICE_BASE.append("ServiceManager");
    /** The {@link WebXMLVerifierInterceptor} service name */
    ServiceName WEBXML_VERIFIER_PLUGIN = INTERNAL_SERVICE_BASE.append("WebXMLVerifier");
    /** The {@link URLHandler} service name */
    ServiceName URL_HANDLER_PLUGIN = INTERNAL_SERVICE_BASE.append("URLHandler");
}