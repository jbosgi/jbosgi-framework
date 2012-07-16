/*
 * #%L
 * JBossOSGi Framework Core
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

    /** The {@link BootstrapBundlesInstall} service for auto install bundles */
    ServiceName BOOTSTRAP_BUNDLES = INTEGRATION_BASE_NAME.append("BootstrapBundles");

    /** The {@link BootstrapBundlesInstall} service for auto install bundles */
    ServiceName BOOTSTRAP_BUNDLES_INSTALL = BootstrapPhase.serviceName(BOOTSTRAP_BUNDLES, BootstrapPhase.INSTALL);

    /** The {@link BootstrapBundlesResolve} service for auto install bundles */
    ServiceName BOOTSTRAP_BUNDLES_RESOLVE = BootstrapPhase.serviceName(BOOTSTRAP_BUNDLES, BootstrapPhase.RESOLVE);

    /** The {@link BootstrapBundlesActivate} service for auto install bundles */
    ServiceName BOOTSTRAP_BUNDLES_ACTIVATE = BootstrapPhase.serviceName(BOOTSTRAP_BUNDLES, BootstrapPhase.ACTIVATE);

    /** The {@link BootstrapBundlesComplete} service for auto install bundles */
    ServiceName BOOTSTRAP_BUNDLES_COMPLETE = BootstrapPhase.serviceName(BOOTSTRAP_BUNDLES, BootstrapPhase.COMPLETE);

    /** The service name for the {@link BundleInstallPlugin} */
    ServiceName BUNDLE_INSTALL_PLUGIN = INTEGRATION_BASE_NAME.append("BundleInstallPlugin");

    /** The service name for the {@link FrameworkModulePlugin} */
    ServiceName FRAMEWORK_MODULE_PLUGIN = INTEGRATION_BASE_NAME.append("FrameworkModulePlugin");

    /** The service name for the {@link ModuleLoaderPlugin} */
    ServiceName MODULE_LOADER_PLUGIN = INTEGRATION_BASE_NAME.append("ModuleLoaderPlugin");

    /** The {@link BootstrapBundlesInstall} service for persistent bundles */
    ServiceName PERSISTENT_BUNDLES = INTEGRATION_BASE_NAME.append("PersistentBundles");

    /** The {@link BootstrapBundlesInstall} service for persistent bundles */
    ServiceName PERSISTENT_BUNDLES_INSTALL = BootstrapPhase.serviceName(PERSISTENT_BUNDLES, BootstrapPhase.INSTALL);

    /** The {@link BootstrapBundlesResolve} service forpersistent bundles */
    ServiceName PERSISTENT_BUNDLES_RESOLVE = BootstrapPhase.serviceName(PERSISTENT_BUNDLES, BootstrapPhase.RESOLVE);

    /** The {@link BootstrapBundlesActivate} service for persistent bundles */
    ServiceName PERSISTENT_BUNDLES_ACTIVATE = BootstrapPhase.serviceName(PERSISTENT_BUNDLES, BootstrapPhase.ACTIVATE);

    /** The {@link BootstrapBundlesComplete} service for persistent bundles */
    ServiceName PERSISTENT_BUNDLES_COMPLETE = BootstrapPhase.serviceName(PERSISTENT_BUNDLES, BootstrapPhase.COMPLETE);

    /** The service name for the {@link SystemPathsPlugin} */
    ServiceName SYSTEM_PATHS_PLUGIN = INTEGRATION_BASE_NAME.append("SystemPathsPlugin");

    /** The service name for the {@link SystemServicesPlugin} */
    ServiceName SYSTEM_SERVICES_PLUGIN = INTEGRATION_BASE_NAME.append("SystemServicesPlugin");

    public enum BootstrapPhase {

        INSTALL, RESOLVE, ACTIVATE, COMPLETE;

        public BootstrapPhase previous() {
            final int ord = ordinal() - 1;
            final BootstrapPhase[] phases = BootstrapPhase.values();
            return ord < 0 ? null : phases[ord];
        }

        public BootstrapPhase next() {
            final int ord = ordinal() + 1;
            final BootstrapPhase[] phases = BootstrapPhase.values();
            return ord == phases.length ? null : phases[ord];
        }

        public static ServiceName serviceName(ServiceName baseName, BootstrapPhase phase) {
            return baseName.append(phase.toString());
        }
    }
}
