/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.osgi.framework.internal;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.modules.Module;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

/**
 * An abstract bundle revision that is based on a user {@link Deployment}.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
final class SystemBundleRevision extends AbstractBundleRevision {

    SystemBundleRevision(SystemBundleState bundleState, OSGiMetaData metadata) throws BundleException {
        super(bundleState, metadata, 0);
    }

    @Override
    SystemBundleState getBundleState() {
        return (SystemBundleState) super.getBundleState();
    }

    @Override
    String getLocation() {
        return Constants.SYSTEM_BUNDLE_LOCATION;
    }

    @Override
    void refreshRevisionInternal() {
        assert false : "Cannot refresh the system bundle revision";
    }
    
    @Override
    Enumeration<String> getEntryPaths(String path) {
        // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
        // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
        return null;
    }

    @Override
    URL getEntry(String path) {
        // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
        // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
        return null;
    }

    @Override
    Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
        // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
        return null;
    }

    @Override
    Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader classLoader = getFrameworkClassLoader();
        return classLoader.loadClass(className);
    }

    @Override
    URL getResource(String name) {
        ClassLoader classLoader = getFrameworkClassLoader();
        return classLoader.getResource(name);
    }

    @Override
    Enumeration<URL> getResources(String name) throws IOException {
        ClassLoader classLoader = getFrameworkClassLoader();
        return classLoader.getResources(name);
    }

    @Override
    URL getLocalizationEntry(String path) {
        return null;
    }

    private ClassLoader getFrameworkClassLoader() {
        Module module = getBundleState().getFrameworkModule();
        return module.getClassLoader();
    }
}
