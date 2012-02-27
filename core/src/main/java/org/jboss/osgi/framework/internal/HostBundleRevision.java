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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;

/**
 * A {@link HostBundleRevision} is responsible for the classloading and resource loading of a bundle.
 *
 * It is associated with a {@link XModule} which holds the wiring information of the bundle.
 * <p/>
 *
 * Every time a bundle is updated a new {@link HostBundleRevision} is created and referenced from the {@link HostBundle}.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
final class HostBundleRevision extends UserBundleRevision {

    static final Logger log = Logger.getLogger(HostBundleRevision.class);

    private List<FragmentBundleRevision> attachedFragments;

    HostBundleRevision(HostBundleState hostBundle, Deployment dep) throws BundleException {
        super(hostBundle, dep);
    }

    /**
     * Assert that the given bundleRev is an instance of HostRevision
     * @throws IllegalArgumentException if the given bundleRev is not an instance of HostRevision
     */
    static HostBundleRevision assertUserRevision(AbstractBundleRevision bundleRev) {
        if (bundleRev == null)
            throw new IllegalArgumentException("Null bundleRev");

        if (bundleRev instanceof HostBundleRevision == false)
            throw new IllegalArgumentException("Not an HostRevision: " + bundleRev);

        return (HostBundleRevision) bundleRev;
    }

    @Override
    HostBundleState getBundleState() {
        return (HostBundleState) super.getBundleState();
    }

    void refreshRevisionInternal() {
        super.refreshRevisionInternal();
        attachedFragments = null;
    }

    void attachFragment(FragmentBundleRevision fragRev) {
        if (attachedFragments == null)
            attachedFragments = new CopyOnWriteArrayList<FragmentBundleRevision>();

        attachedFragments.add(fragRev);
    }

    List<FragmentBundleRevision> getAttachedFragments() {
        if (attachedFragments == null)
            return Collections.emptyList();

        return Collections.unmodifiableList(attachedFragments);
    }

    @Override
    Class<?> loadClass(String className) throws ClassNotFoundException {
        
        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        if (getBundleState().ensureResolved(true) == false)
            throw new ClassNotFoundException("Class '" + className + "' not found in: " + this);

        // Load the class through the module
        ModuleClassLoader loader;
        try {
            loader = getModuleClassLoader();
        } catch (ModuleLoadException ex) {
            throw new ClassNotFoundException("Cannot load class: " + className, ex);
        }
        return loader.loadClass(className, true);
    }

    @Override
    Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        getBundleState().ensureResolved(true);
        return findResolvedEntries(path, pattern, recurse);
    }

    Enumeration<URL> findResolvedEntries(String path, String pattern, boolean recurse) {
        Enumeration<URL> hostEntries = super.findEntries(path, pattern, recurse);

        List<FragmentBundleRevision> fragments = getAttachedFragments();
        if (fragments.size() == 0)
            return hostEntries;

        // If there are attached fragments, their entries also need to be included.
        List<URL> allEntries = (hostEntries == null ? new ArrayList<URL>() : new ArrayList<URL>(Collections.list(hostEntries)));
        for (FragmentBundleRevision fragmentRevision : fragments) {
            Enumeration<URL> fragEntries = fragmentRevision.findEntries(path, pattern, recurse);
            if (fragEntries != null)
                allEntries.addAll(Collections.list(fragEntries));
        }

        if (allEntries.size() == 0)
            return null;
        else
            return Collections.enumeration(allEntries);
    }

    @Override
    URL getResource(String path) {
        getBundleState().assertNotUninstalled();

        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        if (getBundleState().ensureResolved(false)) {
            ModuleClassLoader moduleClassLoader;
            try {
                moduleClassLoader = getModuleClassLoader();
            } catch (ModuleLoadException ex) {
                log.debugf("Cannot get resource, because of: %s", ex);
                return null;
            }
            return moduleClassLoader.getResource(path);
        }

        // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
        return getEntry(path);
    }

    @Override
    Enumeration<URL> getResources(String path) throws IOException {
        getBundleState().assertNotUninstalled();

        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        if (getBundleState().ensureResolved(true) == true) {
            ModuleClassLoader moduleClassLoader;
            try {
                moduleClassLoader = getModuleClassLoader();
            } catch (ModuleLoadException ex) {
                log.debugf("Cannot get resources, because of: %s", ex);
                return null;
            }
            Enumeration<URL> resources = moduleClassLoader.getResources(path);
            return resources.hasMoreElements() ? resources : null;
        }

        // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
        for (RevisionContent revContent : getContentList()) {
            try {
                VirtualFile child = revContent.getVirtualFile().getChild(path);
                if (child == null)
                    return null;

                Vector<URL> vector = new Vector<URL>();
                vector.add(child.toURL());
                return vector.elements();
            } catch (IOException ex) {
                log.errorf(ex, "Cannot get resources: %s", path);
                return null;
            }
        }

        return null;
    }

    @Override
    URL getLocalizationEntry(String path) {
        // The framework must first search in the bundle’s JAR for the localization entry.
        URL entry = getEntry(path);
        if (entry != null)
            return entry;

        // If the entry is not found and the bundle has fragments,
        // then the attached fragment JARs must be searched for the localization entry.
        for (FragmentBundleRevision fragrev : getAttachedFragments()) {
            if (fragrev.getBundleState().isUninstalled() == false) {
                entry = fragrev.getEntry(path);
                if (entry != null)
                    return entry;
            }
        }

        return null;
    }
}

