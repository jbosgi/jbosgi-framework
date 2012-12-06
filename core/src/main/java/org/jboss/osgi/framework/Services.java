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
package org.jboss.osgi.framework;

import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.launch.Framework;

/**
 * The collection of public service names.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public interface Services {

    /** The prefix for all OSGi services */
    ServiceName JBOSGI_BASE_NAME = ServiceName.of(Constants.JBOSGI_PREFIX);

    /** The {@link BundleManager} service name. */
    ServiceName BUNDLE_MANAGER = JBOSGI_BASE_NAME.append("BundleManager");

    /** The {@link XEnvironment} service name */
    ServiceName ENVIRONMENT = JBOSGI_BASE_NAME.append("Environment");

    /** The service name for the created {@link Framework} */
    ServiceName FRAMEWORK_CREATE = JBOSGI_BASE_NAME.append("framework", "CREATE");

    /** The service name for the initialized {@link Framework} */
    ServiceName FRAMEWORK_INIT = JBOSGI_BASE_NAME.append("framework", "INIT");

    /** The service name for the started {@link Framework} */
    ServiceName FRAMEWORK_ACTIVE = JBOSGI_BASE_NAME.append("framework", "ACTIVE");

    /** The {@link XResolver} service name */
    ServiceName RESOLVER = JBOSGI_BASE_NAME.append("Resolver");
}