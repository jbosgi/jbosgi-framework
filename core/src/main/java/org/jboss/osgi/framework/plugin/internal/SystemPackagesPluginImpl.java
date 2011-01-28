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
package org.jboss.osgi.framework.plugin.internal;

import static org.osgi.framework.Constants.FRAMEWORK_BOOTDELEGATION;
import static org.osgi.framework.Constants.FRAMEWORK_BUNDLE_PARENT;
import static org.osgi.framework.Constants.FRAMEWORK_BUNDLE_PARENT_BOOT;
import static org.osgi.framework.Constants.FRAMEWORK_BUNDLE_PARENT_EXT;
import static org.osgi.framework.Constants.FRAMEWORK_SYSTEMPACKAGES;
import static org.osgi.framework.Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.SystemPackagesPlugin;

/**
 * A plugin manages the Framework's system packages.
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class SystemPackagesPluginImpl extends AbstractPlugin implements SystemPackagesPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(SystemPackagesPluginImpl.class);

    // The derived combination of all system packages
    private Set<String> systemPackages = new LinkedHashSet<String>();
    // The boot delegation packages
    private Set<String> bootDelegationPackages = new LinkedHashSet<String>();
    // The framework packages
    private Set<String> frameworkPackages = new LinkedHashSet<String>();

    public SystemPackagesPluginImpl(BundleManager bundleManager) {
        super(bundleManager);

        String systemPackagesProp = (String) bundleManager.getProperty(FRAMEWORK_SYSTEMPACKAGES);
        if (systemPackagesProp != null) {
            systemPackages.addAll(packagesAsList(systemPackagesProp));
        } else {
            // The default system packages
            systemPackages.add("javax.imageio");
            systemPackages.add("javax.imageio.stream");
            systemPackages.add("javax.management");
            systemPackages.add("javax.management.loading");

            systemPackages.add("javax.management.modelmbean");
            systemPackages.add("javax.management.monitor");
            systemPackages.add("javax.management.openmbean");
            systemPackages.add("javax.management.relation");
            systemPackages.add("javax.management.remote");
            systemPackages.add("javax.management.remote.rmi");
            systemPackages.add("javax.management.timer");

            systemPackages.add("javax.naming");
            systemPackages.add("javax.naming.event");
            systemPackages.add("javax.naming.spi");

            systemPackages.add("javax.net");
            systemPackages.add("javax.net.ssl");

            systemPackages.add("javax.security.cert");

            systemPackages.add("javax.xml.datatype");
            systemPackages.add("javax.xml.namespace");
            systemPackages.add("javax.xml.parsers");
            systemPackages.add("javax.xml.validation");
            systemPackages.add("javax.xml.transform");
            systemPackages.add("javax.xml.transform.dom");
            systemPackages.add("javax.xml.transform.sax");
            systemPackages.add("javax.xml.transform.stream");

            systemPackages.add("org.jboss.modules");

            systemPackages.add("org.w3c.dom");
            systemPackages.add("org.w3c.dom.bootstrap");
            systemPackages.add("org.w3c.dom.ls");
            systemPackages.add("org.w3c.dom.events");
            systemPackages.add("org.w3c.dom.ranges");
            systemPackages.add("org.w3c.dom.views");
            systemPackages.add("org.w3c.dom.traversal");

            systemPackages.add("org.xml.sax");
            systemPackages.add("org.xml.sax.ext");
            systemPackages.add("org.xml.sax.helpers");

            // SchemaFactoryFinder attempting to use the platform default XML Schema validator
            systemPackages.add("com.sun.org.apache.xerces.internal.jaxp.validation");

            String asString = packagesAsString(systemPackages);
            bundleManager.setProperty(FRAMEWORK_SYSTEMPACKAGES, asString);
        }

        // Add the extra system packages
        String extraPackages = (String) bundleManager.getProperty(FRAMEWORK_SYSTEMPACKAGES_EXTRA);
        if (extraPackages != null)
            systemPackages.addAll(packagesAsList(extraPackages));

        // Initialize the boot delegation package names
        String bootDelegationProp = (String) bundleManager.getProperty(FRAMEWORK_BOOTDELEGATION);
        if (bootDelegationProp != null) {
            String[] packageNames = bootDelegationProp.split(",");
            for (String packageName : packageNames) {
                bootDelegationPackages.add(packageName);
            }
        } else {
            bootDelegationPackages.add("sun.reflect");
        }
        
        // Initialize the framework package names
        frameworkPackages.add("org.jboss.msc.service");
        frameworkPackages.add("org.jboss.osgi.deployment.deployer");
        frameworkPackages.add("org.jboss.osgi.deployment.interceptor");
        frameworkPackages.add("org.jboss.osgi.framework.url");
        frameworkPackages.add("org.jboss.osgi.modules");

        frameworkPackages.add("org.osgi.framework;version=1.5");
        frameworkPackages.add("org.osgi.framework.hooks;version=1.0");
        frameworkPackages.add("org.osgi.framework.hooks.service;version=1.0");
        frameworkPackages.add("org.osgi.framework.launch;version=1.0");
        frameworkPackages.add("org.osgi.service.condpermadmin;version=1.1");
        frameworkPackages.add("org.osgi.service.packageadmin;version=1.2");
        frameworkPackages.add("org.osgi.service.permissionadmin;version=1.2");
        frameworkPackages.add("org.osgi.service.startlevel;version=1.1");
        frameworkPackages.add("org.osgi.service.url;version=1.0");
        frameworkPackages.add("org.osgi.util.tracker;version=1.4");
    }

    @Override
    public Set<String> getFrameworkPackages() {
        return Collections.unmodifiableSet(frameworkPackages);
    }

    @Override
    public Set<String> getFrameworkPaths() {
        Set<String> paths = new LinkedHashSet<String>();
        for (String packageSpec : getFrameworkPackages()) {
            int index = packageSpec.indexOf(';');
            if (index > 0) {
                packageSpec = packageSpec.substring(0, index);
            }
            paths.add(packageSpec.replace('.', '/'));
        }
        return Collections.unmodifiableSet(paths);
    }

    @Override
    public boolean isFrameworkPackage(String name) {
        assertInitialized();
        return isPackageNameInSet(getFrameworkPackages(), name);
    }

    @Override
    public PathFilter getFrameworkPackageFilter() {
        assertInitialized();
        return PathFilters.in(getFrameworkPaths());
    }

    @Override
    public Set<String> getBootDelegationPackages() {
        assertInitialized();
        return Collections.unmodifiableSet(bootDelegationPackages);
    }

    @Override
    public boolean isBootDelegationPackage(String name) {
        assertInitialized();
        if (name == null)
            throw new IllegalArgumentException("Null package name");

        if (name.startsWith("java."))
            return true;

        if (bootDelegationPackages.contains(name))
            return true;

        // Match foo with foo.*
        for (String aux : bootDelegationPackages) {
            if (aux.endsWith(".*") && name.startsWith(aux.substring(0, aux.length() - 2)))
                return true;
        }

        return false;
    }

    @Override
    public Set<String> getSystemPackages() {
        assertInitialized();
        return Collections.unmodifiableSet(systemPackages);
    }

    @Override
    public boolean isSystemPackage(String name) {
        assertInitialized();
        return isPackageNameInSet(systemPackages, name);
    }

    @Override
    public PathFilter getSystemPackageFilter() {
        assertInitialized();
        MultiplePathFilterBuilder builder = PathFilters.multiplePathFilterBuilder(false);

        // Add bootdelegation paths
        for (String packageName : getBootDelegationPackages()) {
            if (packageName.equals("*")) {
                if (doFrameworkPackageDelegation()) {
                    builder.addFilter(PathFilters.acceptAll(), true);
                } else {
                    builder.addFilter(PathFilters.all(PathFilters.acceptAll(), PathFilters.not(getFrameworkPackageFilter())), true);
                }
            } else if (packageName.endsWith(".*")) {
                packageName = packageName.substring(0, packageName.length() - 2);
                builder.addFilter(PathFilters.isChildOf(packageName.replace('.', '/')), true);
            } else {
                builder.addFilter(PathFilters.is(packageName.replace('.', '/')), true);
            }
        }

        // Add system packages exported by the framework
        for (String packageSpec : getSystemPackages()) {
            int index = packageSpec.indexOf(';');
            if (index > 0) {
                packageSpec = packageSpec.substring(0, index);
            }
            builder.addFilter(PathFilters.is(packageSpec.replace('.', '/')), true);
        }

        return builder.create();
    }

    @Override
    public boolean doFrameworkPackageDelegation() {
        String property = (String) getBundleManager().getProperty(FRAMEWORK_BUNDLE_PARENT);
        if (property == null) {
            property = FRAMEWORK_BUNDLE_PARENT_BOOT;
        }
        boolean allBootDelegation = getBootDelegationPackages().contains("*");
        return !(allBootDelegation && (FRAMEWORK_BUNDLE_PARENT_BOOT.equals(property) || FRAMEWORK_BUNDLE_PARENT_EXT.equals(property)));
    }

    private boolean isPackageNameInSet(Set<String> packages, String name) {
        if (name == null)
            throw new IllegalArgumentException("Null package name");

        int index = name.indexOf(';');
        if (index > 0)
            name = name.substring(0, index);

        if (packages.contains(name))
            return true;

        for (String aux : packages) {
            if (aux.startsWith(name + ";"))
                return true;
        }

        return false;
    }

    private String packagesAsString(Set<String> sysPackages) {
        int i = 0;
        StringBuffer result = new StringBuffer();
        for (String packageName : sysPackages) {
            if (i++ > 0) {
                result.append(",");
            }
            result.append(packageName);
        }
        return result.toString();
    }

    private List<String> packagesAsList(String sysPackages) {
        List<String> result = new ArrayList<String>();
        for (String name : sysPackages.split(",")) {
            name = name.trim();
            if (name.length() > 0)
                result.add(name);
        }
        return result;
    }

    private void assertInitialized() {
        if (systemPackages.isEmpty())
            throw new IllegalStateException("SystemPackagesPlugin not initialized");
    }
}