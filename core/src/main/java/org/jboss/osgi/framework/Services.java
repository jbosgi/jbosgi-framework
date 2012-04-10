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
package org.jboss.osgi.framework;

import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

/**
 * A collection of public service names.
 *
 * @author thomas.diesler@jboss.com
 * @since 08-Apr-2011
 */
public interface Services {

    /** The prefix for all OSGi services */
    ServiceName JBOSGI_BASE_NAME = ServiceName.of(Constants.JBOSGI_PREFIX);

    /** The prefix for all OSGi services */
    ServiceName JBOSGI_SERVICE_BASE_NAME = JBOSGI_BASE_NAME.append("service");

    /** The base name of all framework OSGi services that were registered outside the OSGi layer */
    ServiceName JBOSGI_XSERVICE_BASE_NAME = JBOSGI_BASE_NAME.append("xservice");

    /** The prefix for all OSGi bundle services */
    ServiceName BUNDLE_BASE_NAME = JBOSGI_BASE_NAME.append("bundle");

    /** The service name for the {@link AutoInstallProvider} */
    ServiceName AUTOINSTALL_PROVIDER = JBOSGI_BASE_NAME.append("AutoInstallProvider");

    /** The {@link AutoInstallProvider} nested service that indicates completion */
    ServiceName AUTOINSTALL_PROVIDER_COMPLETE = AUTOINSTALL_PROVIDER.append("COMPLETE");

    /** The service name for the {@link BundleInstallProvider} */
    ServiceName BUNDLE_INSTALL_PROVIDER = JBOSGI_BASE_NAME.append("BundleInstallProvider");

    /** The {@link BundleManagerService} service name. */
    ServiceName BUNDLE_MANAGER = JBOSGI_BASE_NAME.append("BundleManager");

    /** The {@link XEnvironment} service name */
    ServiceName ENVIRONMENT = JBOSGI_BASE_NAME.append("Environment");

    /** The base name of all framework services */
    ServiceName FRAMEWORK_BASE_NAME = JBOSGI_BASE_NAME.append("Framework");

    /** The service name for the created {@link Framework} */
    ServiceName FRAMEWORK_CREATE = FRAMEWORK_BASE_NAME.append("CREATED");

    /** The service name for the initialized {@link Framework} */
    ServiceName FRAMEWORK_INIT = FRAMEWORK_BASE_NAME.append("INITIALIZED");

    /** The service name for the started {@link Framework} */
    ServiceName FRAMEWORK_ACTIVE = FRAMEWORK_BASE_NAME.append("ACTIVE");

    /** The service name to use as a dependency to activate the {@link Framework} */
    ServiceName FRAMEWORK_ACTIVATOR = FRAMEWORK_BASE_NAME.append("ACTIVATOR");

    /** The service name for the {@link PackageAdmin} service */
    ServiceName PACKAGE_ADMIN = JBOSGI_BASE_NAME.append("PackageAdmin");

    /** The service name for the {@link StartLevel} service */
    ServiceName START_LEVEL = JBOSGI_BASE_NAME.append("StartLevel");

    /** The service name for the system {@link Bundle} */
    ServiceName SYSTEM_BUNDLE = JBOSGI_BASE_NAME.append("SystemBundle");

    /** The service name for the system {@link BundleContext} */
    ServiceName SYSTEM_CONTEXT = JBOSGI_BASE_NAME.append("SystemContext");

    /** The service name for the {@link FrameworkModuleProvider} */
    ServiceName FRAMEWORK_MODULE_PROVIDER = JBOSGI_BASE_NAME.append("FrameworkModuleProvider");

    /** The service name for the {@link ModuleLoaderProvider} */
    ServiceName MODULE_LOADER_PROVIDER = JBOSGI_BASE_NAME.append("ModuleLoaderProvider");

    /** The service name for the {@link SystemServicesProvider} */
    ServiceName SYSTEM_SERVICES_PROVIDER = JBOSGI_BASE_NAME.append("SystemServicesProvider");

    /** The service name for the {@link SystemPathsProvider} */
    ServiceName SYSTEM_PATHS_PROVIDER = JBOSGI_BASE_NAME.append("SystemPathsProvider");
}