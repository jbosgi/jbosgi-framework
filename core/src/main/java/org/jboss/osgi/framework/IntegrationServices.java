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


/**
 * A collection of integration service names.
 *
 * @author thomas.diesler@jboss.com
 * @since 13-Apr-2012
 */
public interface IntegrationServices {

    /** The service name for the {@link AutoInstallProvider} */
    ServiceName AUTOINSTALL_PROVIDER = Services.JBOSGI_BASE_NAME.append("AutoInstallProvider");

    /** The {@link AutoInstallProvider} nested service that indicates completion */
    ServiceName AUTOINSTALL_PROVIDER_COMPLETE = AUTOINSTALL_PROVIDER.append("COMPLETE");

    /** The service name for the {@link BundleInstallProvider} */
    ServiceName BUNDLE_INSTALL_PROVIDER = Services.JBOSGI_BASE_NAME.append("BundleInstallProvider");

    /** The service name for the {@link FrameworkModuleProvider} */
    ServiceName FRAMEWORK_MODULE_PROVIDER = Services.JBOSGI_BASE_NAME.append("FrameworkModuleProvider");

    /** The service name for the {@link ModuleLoaderProvider} */
    ServiceName MODULE_LOADER_PROVIDER = Services.JBOSGI_BASE_NAME.append("ModuleLoaderProvider");

    /** The service name for the {@link SystemPathsProvider} */
    ServiceName SYSTEM_PATHS_PROVIDER = Services.JBOSGI_BASE_NAME.append("SystemPathsProvider");

    /** The service name for the {@link SystemServicesProvider} */
    ServiceName SYSTEM_SERVICES_PROVIDER = Services.JBOSGI_BASE_NAME.append("SystemServicesProvider");
}