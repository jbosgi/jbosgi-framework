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

import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;

/**
 * The collection of internal service names.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public interface InternalServices {

    /** The base name of all internal services */
    ServiceName INTERNAL_SERVICE_BASE = Services.JBOSGI_BASE_NAME.append("internal");
    /** The prefix for all OSGi services */
    ServiceName SERVICE_BASE_NAME = INTERNAL_SERVICE_BASE.append("service");
    /** The prefix for all OSGi bundle services */
    ServiceName BUNDLE_BASE_NAME = Services.JBOSGI_BASE_NAME.append("bundle");

    /** The {@link BundleStoragePlugin} service name */
    ServiceName BUNDLE_STORAGE_PLUGIN = INTERNAL_SERVICE_BASE.append("BundleStorage");
    /** The {@link DeploymentFactoryPlugin} service name */
    ServiceName DEPLOYMENT_FACTORY_PLUGIN = INTERNAL_SERVICE_BASE.append("DeploymentFactory");
    /** The service name for the created {@link Framework} */
    ServiceName FRAMEWORK_STATE_CREATE = INTERNAL_SERVICE_BASE.append("framework", "CREATE");
    /** The service name for the initialized {@link Framework} */
    ServiceName FRAMEWORK_STATE_INIT = INTERNAL_SERVICE_BASE.append("framework", "INIT");
    /** The service name for the started {@link Framework} */
    ServiceName FRAMEWORK_STATE_ACTIVE = INTERNAL_SERVICE_BASE.append("framework", "ACTIVE");
    /** The {@link FrameworkCoreServices} service name. */
    ServiceName FRAMEWORK_CORE_SERVICES = INTERNAL_SERVICE_BASE.append("CoreServices");
    /** The {@link FrameworkEventsPlugin} service name */
    ServiceName FRAMEWORK_EVENTS_PLUGIN = INTERNAL_SERVICE_BASE.append("FrameworkEvents");
    /** The {@link LifecycleInterceptorPlugin} service name */
    ServiceName LIFECYCLE_INTERCEPTOR_PLUGIN = INTERNAL_SERVICE_BASE.append("LifecycleInterceptor");
    /** The {@link ModuleManagerPlugin} service name */
    ServiceName MODULE_MANGER_PLUGIN = INTERNAL_SERVICE_BASE.append("ModuleManager");
    /** The {@link Module} service name */
    ServiceName MODULE_SERVICE = INTERNAL_SERVICE_BASE.append("module");
    /** The {@link NativeCodePlugin} service name */
    ServiceName NATIVE_CODE_PLUGIN = INTERNAL_SERVICE_BASE.append("NativeCode");
    /** The {@link ServiceManagerPlugin} service name */
    ServiceName SERVICE_MANAGER_PLUGIN = INTERNAL_SERVICE_BASE.append("ServiceManager");
    /** The service name for the system {@link Bundle} */
    ServiceName SYSTEM_BUNDLE = INTERNAL_SERVICE_BASE.append("SystemBundle");
    /** The service name for the system {@link BundleContext} */
    ServiceName SYSTEM_CONTEXT = INTERNAL_SERVICE_BASE.append("SystemContext");
    /** The {@link URLHandler} service name */
    ServiceName URL_HANDLER_PLUGIN = INTERNAL_SERVICE_BASE.append("URLHandler");
}
