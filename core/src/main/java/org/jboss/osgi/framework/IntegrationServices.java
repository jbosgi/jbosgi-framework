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

import static org.jboss.osgi.framework.Services.INTEGRATION_BASE_NAME;

import org.jboss.msc.service.ServiceName;


/**
 * A collection of integration service names.
 *
 * @author thomas.diesler@jboss.com
 * @since 13-Apr-2012
 */
public interface IntegrationServices {

    /** The service name for the {@link AutoInstallHandler} */
    ServiceName AUTOINSTALL_HANDLER = INTEGRATION_BASE_NAME.append("AutoInstallHandler");

    /** The {@link AutoInstallHandler} nested service that indicates completion */
    ServiceName AUTOINSTALL_HANDLER_COMPLETE = AUTOINSTALL_HANDLER.append("COMPLETE");

    /** The service name for the {@link BundleInstallHandler} */
    ServiceName BUNDLE_INSTALL_HANDLER = INTEGRATION_BASE_NAME.append("BundleInstallHandler");

    /** The service name for the {@link FrameworkModuleProvider} */
    ServiceName FRAMEWORK_MODULE_PROVIDER = INTEGRATION_BASE_NAME.append("FrameworkModuleProvider");

    /** The service name for the {@link ModuleLoaderProvider} */
    ServiceName MODULE_LOADER_PROVIDER = INTEGRATION_BASE_NAME.append("ModuleLoaderProvider");

    /** The {@link PersistentBundlesHandler} service name */
    ServiceName PERSISTENT_BUNDLES_HANDLER = INTEGRATION_BASE_NAME.append("PersistentBundlesHandler");

    /** The {@link PersistentBundlesHandler} nested service that indicates completion */
    ServiceName PERSISTENT_BUNDLES_HANDLER_COMPLETE = PERSISTENT_BUNDLES_HANDLER.append("COMPLETE");

    /** The service name for the {@link SystemPathsProvider} */
    ServiceName SYSTEM_PATHS_PROVIDER = INTEGRATION_BASE_NAME.append("SystemPathsProvider");

    /** The service name for the {@link SystemServicesProvider} */
    ServiceName SYSTEM_SERVICES_PROVIDER = INTEGRATION_BASE_NAME.append("SystemServicesProvider");
}