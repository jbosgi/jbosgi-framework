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

import org.jboss.modules.filter.PathFilter;
import org.jboss.msc.service.Service;

import java.util.Set;

/**
 * A plugin manages the Framework's system packages.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public interface SystemPathsProvider extends Service<SystemPathsProvider> {

    String[] DEFAULT_FRAMEWORK_PACKAGES = new String[]{
            "org.jboss.modules;version=1.1",
            "org.jboss.msc.service;version=1.0",
            "org.jboss.osgi.deployment.deployer;version=1.0",
            "org.jboss.osgi.deployment.interceptor;version=1.0",
            "org.jboss.osgi.framework;version=1.0",
            "org.jboss.osgi.framework.url;version=1.0",
            "org.jboss.osgi.metadata;version=2.0",
            "org.jboss.osgi.modules;version=1.0",
        	"org.jboss.osgi.resolver;version=2.0",
        	"org.jboss.osgi.resolver.spi;version=2.0",
            "org.jboss.osgi.spi;version=2.0",
            "org.jboss.osgi.vfs;version=1.0",
            "org.osgi.framework;version=1.5",
            "org.osgi.framework.hooks;version=1.0",
            "org.osgi.framework.hooks.service;version=1.0",
            "org.osgi.framework.launch;version=1.0",
        	"org.osgi.resource;version=1.0",
        	"org.osgi.framework.wiring;version=1.0",
            "org.osgi.service.condpermadmin;version=1.1",
            "org.osgi.service.packageadmin;version=1.2",
            "org.osgi.service.permissionadmin;version=1.2",
        	"org.osgi.service.resolver;version=1.0",
            "org.osgi.service.startlevel;version=1.1",
            "org.osgi.service.url;version=1.0",
            "org.osgi.util.tracker;version=1.4",
            "org.osgi.util.xml;version=1.0"
    };

    String[] DEFAULT_SYSTEM_PACKAGES = new String[]{
            "javax.accessibility",
            "javax.activation",
            "javax.activity",
            "javax.annotation",
            "javax.annotation.processing",
            "javax.crypto",
            "javax.crypto.interfaces",
            "javax.crypto.spec",
            "javax.imageio",
            "javax.imageio.event",
            "javax.imageio.metadata",
            "javax.imageio.plugins.bmp",
            "javax.imageio.plugins.jpeg",
            "javax.imageio.spi",
            "javax.imageio.stream",
            "javax.jws",
            "javax.jws.soap",
            "javax.lang.model",
            "javax.lang.model.element",
            "javax.lang.model.type",
            "javax.lang.model.util",
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
            "javax.naming.directory",
            "javax.naming.event",
            "javax.naming.ldap",
            "javax.naming.spi",
            "javax.net",
            "javax.net.ssl",
            "javax.print",
            "javax.print.attribute",
            "javax.print.attribute.standard",
            "javax.print.event",
            "javax.rmi",
            "javax.rmi.CORBA",
            "javax.rmi.ssl",
            "javax.script",
            "javax.security.auth",
            "javax.security.auth.callback",
            "javax.security.auth.kerberos",
            "javax.security.auth.login",
            "javax.security.auth.spi",
            "javax.security.auth.x500",
            "javax.security.cert",
            "javax.security.sasl",
            "javax.sound.midi",
            "javax.sound.midi.spi",
            "javax.sound.sampled",
            "javax.sound.sampled.spi",
            "javax.sql",
            "javax.sql.rowset",
            "javax.sql.rowset.serial",
            "javax.sql.rowset.spi",
            "javax.swing",
            "javax.swing.border",
            "javax.swing.colorchooser",
            "javax.swing.event",
            "javax.swing.filechooser",
            "javax.swing.plaf",
            "javax.swing.plaf.basic",
            "javax.swing.plaf.metal",
            "javax.swing.plaf.multi",
            "javax.swing.plaf.synth",
            "javax.swing.table",
            "javax.swing.text",
            "javax.swing.text.html",
            "javax.swing.text.html.parser",
            "javax.swing.text.rtf",
            "javax.swing.tree",
            "javax.swing.undo",
            "javax.tools",
            /* Provided by J2EE container
            "javax.transaction",
            "javax.transaction.xa",
            */
            "javax.xml",
            "javax.xml.bind",
            "javax.xml.bind.annotation",
            "javax.xml.bind.annotation.adapters",
            "javax.xml.bind.attachment",
            "javax.xml.bind.helpers",
            "javax.xml.bind.util",
            "javax.xml.crypto",
            "javax.xml.crypto.dom",
            "javax.xml.crypto.dsig",
            "javax.xml.crypto.dsig.dom",
            "javax.xml.crypto.dsig.keyinfo",
            "javax.xml.crypto.dsig.spec",
            "javax.xml.datatype",
            "javax.xml.namespace",
            "javax.xml.parsers",
            "javax.xml.soap",
            "javax.xml.stream",
            "javax.xml.stream.events",
            "javax.xml.stream.util",
            "javax.xml.transform",
            "javax.xml.transform.dom",
            "javax.xml.transform.sax",
            "javax.xml.transform.stax",
            "javax.xml.transform.stream",
            "javax.xml.validation",
            "javax.xml.ws",
            "javax.xml.ws.handler",
            "javax.xml.ws.handler.soap",
            "javax.xml.ws.http",
            "javax.xml.ws.soap",
            "javax.xml.ws.spi",
            "javax.xml.ws.wsaddressing",
            "javax.xml.xpath",
            "org.ietf.jgss",
            "org.w3c.dom",
            "org.w3c.dom.bootstrap",
            "org.w3c.dom.events",
            "org.w3c.dom.ls",
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