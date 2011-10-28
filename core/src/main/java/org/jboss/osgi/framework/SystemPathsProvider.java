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
package org.jboss.osgi.framework;

import java.util.Set;

import org.jboss.modules.filter.PathFilter;
import org.jboss.msc.service.Service;

/**
 * A plugin manages the Framework's system packages.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public interface SystemPathsProvider extends Service<SystemPathsProvider> {

    String[] DEFAULT_FRAMEWORK_PACKAGES = new String[] {
            "org.jboss.modules;version=1.0",
            "org.jboss.msc.service;version=1.0",
            "org.jboss.osgi.deployment.deployer;version=1.0",
            "org.jboss.osgi.deployment.interceptor;version=1.0",
            "org.jboss.osgi.framework;version=1.0",
            "org.jboss.osgi.framework.url;version=1.0",
            "org.jboss.osgi.modules;version=1.0",
            "org.jboss.osgi.spi.capability;version=1.0",
            "org.jboss.osgi.spi.util;version=1.0",
            "org.jboss.osgi.testing;version=1.0",
            "org.jboss.osgi.vfs;version=1.0",
            "org.osgi.framework;version=1.5",
            "org.osgi.framework.hooks;version=1.0",
            "org.osgi.framework.hooks.service;version=1.0",
            "org.osgi.framework.launch;version=1.0",
            "org.osgi.service.condpermadmin;version=1.1",
            "org.osgi.service.packageadmin;version=1.2",
            "org.osgi.service.permissionadmin;version=1.2",
            "org.osgi.service.startlevel;version=1.1",
            "org.osgi.service.url;version=1.0",
            "org.osgi.util.tracker;version=1.4"
        };

    String[] DEFAULT_SYSTEM_PACKAGES = new String[] {
            "javax.imageio",
            "javax.imageio.stream",
            "javax.management",
            "javax.management.loading",
            "javax.management.modelmbean",
            "javax.management.monitor",
            "javax.management.openmbean",
            "javax.management.relation",
            "javax.management.remote",
            "javax.management.remote.rmi",
            "javax.management.timer",
            "javax.naming",
            "javax.naming.event",
            "javax.naming.spi",
            "javax.net",
            "javax.net.ssl",
            "javax.security.cert",
            "javax.xml.datatype",
            "javax.xml.namespace",
            "javax.xml.parsers",
            "javax.xml.validation",
            "javax.xml.transform",
            "javax.xml.transform.dom",
            "javax.xml.transform.sax",
            "javax.xml.transform.stream",
            "org.w3c.dom",
            "org.w3c.dom.bootstrap",
            "org.w3c.dom.ls",
            "org.w3c.dom.events",
            "org.w3c.dom.ranges",
            "org.w3c.dom.views",
            "org.w3c.dom.traversal",
            "org.xml.sax",
            "org.xml.sax.ext",
            "org.xml.sax.helpers"
        };

    /**
     * Get the list of defined boot delegation packages
     *
     * @return The list of defined system packages
     */
    Set<String> getBootDelegationPackages();

    /**
     * Get the filter for boot delegation
     *
     * @return The filter of framework exported paths
     */
    PathFilter getBootDelegationFilter();

    /**
     * Get the filter for boot delegation
     *
     * @return The filter of framework exported paths
     */
    Set<String> getBootDelegationPaths();

    /**
     * Get the list of defined system packages
     *
     * @return The list of defined system packages
     */
    Set<String> getSystemPackages();

    /**
     * Get the filter that the system exports
     *
     * @return The filter of framework exported paths
     */
    PathFilter getSystemFilter();

    /**
     * Get the set of paths that the system exports
     *
     * @return The set of paths that the framework exports
     */
    Set<String> getSystemPaths();
}