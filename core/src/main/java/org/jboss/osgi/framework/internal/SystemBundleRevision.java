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

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.modules.Module;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XResource;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

/**
 * An abstract bundle revision that is based on a user {@link Deployment}.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
final class SystemBundleRevision extends BundleStateRevision {

    SystemBundleRevision(FrameworkState frameworkState, OSGiMetaData metadata, StorageState storageState) throws BundleException {
        super(frameworkState, metadata, storageState);
        putAttachment(XResource.RESOURCE_IDENTIFIER_KEY, new Long(0));
    }

    @Override
    String getLocation() {
        return Constants.SYSTEM_BUNDLE_LOCATION;
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
        // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
        return null;
    }

    @Override
    public URL getEntry(String path) {
        // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
        // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
        return null;
    }

    @Override
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        // [Bug-1472] Clarify the semantic of resource API when called on the system bundle
        // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1472
        return null;
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader classLoader = getFrameworkClassLoader();
        return classLoader.loadClass(className);
    }

    @Override
    public URL getResource(String name) {
        ClassLoader classLoader = getFrameworkClassLoader();
        return classLoader.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        ClassLoader classLoader = getFrameworkClassLoader();
        return classLoader.getResources(name);
    }

    @Override
    public URL getLocalizationEntry(String path) {
        return null;
    }

    private ClassLoader getFrameworkClassLoader() {
        ModuleManager moduleManager = getFrameworkState().getModuleManager();
        Module module = moduleManager.getFrameworkModule();
        return module.getClassLoader();
    }
}
