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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.resource.Resource;

/**
 * A {@link HostBundleRevision} is responsible for the classloading and resource loading of a bundle.
 *
 * Every time a bundle is updated a new {@link HostBundleRevision} is created and referenced from the {@link HostBundleState}.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
final class HostBundleRevision extends UserBundleRevision {

    private Set<FragmentBundleRevision> attachedFragments;

    HostBundleRevision(FrameworkState frameworkState, Deployment dep, OSGiMetaData metadata, InternalStorageState storageState) throws BundleException {
        super(frameworkState, dep, metadata, storageState);
    }

    /**
     * Assert that the given resource is an instance of HostBundleRevision
     */
    static HostBundleRevision assertHostRevision(Resource res) {
        assert res instanceof HostBundleRevision : "Not a HostRevision: " + res;
        return (HostBundleRevision) res;
    }

    HostBundleState getBundleState() {
        return (HostBundleState) getBundle();
    }

    void refreshRevisionInternal() {
        super.refreshRevisionInternal();
        attachedFragments = null;
    }

    void attachFragment(FragmentBundleRevision fragRev) {
        if (attachedFragments == null) {
        	Comparator<FragmentBundleRevision> comp = new Comparator<FragmentBundleRevision>(){
				@Override
				public int compare(FragmentBundleRevision rev1, FragmentBundleRevision rev2) {
					Bundle b1 = rev1.getBundle();
					Bundle b2 = rev2.getBundle();
					return (int)(b1.getBundleId() - b2.getBundleId());
				}

        	};
            attachedFragments = new TreeSet<FragmentBundleRevision>(comp);
        }
        attachedFragments.add(fragRev);
    }

    Set<FragmentBundleRevision> getAttachedFragments() {
        if (attachedFragments == null)
            return Collections.emptySet();

        return Collections.unmodifiableSet(attachedFragments);
    }

    Class<?> loadClass(String className) throws ClassNotFoundException {

        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        if (getBundleState().ensureResolved(true) == false)
            throw MESSAGES.classNotFoundInRevision(className, this);

        // Load the class through the module
        ModuleClassLoader loader = getModuleClassLoader();
        return loader.loadClass(className, true);
    }

    @Override
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        getBundleState().ensureResolved(true);
        return findResolvedEntries(path, pattern, recurse);
    }

    Enumeration<URL> findResolvedEntries(String path, String pattern, boolean recurse) {
        Enumeration<URL> hostEntries = super.findEntries(path, pattern, recurse);

        Set<FragmentBundleRevision> fragments = getAttachedFragments();
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
    public URL getResource(String path) {
        getBundleState().assertNotUninstalled();

        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        if (getBundleState().ensureResolved(false)) {
            ModuleClassLoader moduleClassLoader = getModuleClassLoader();
            return moduleClassLoader.getResource(path);
        }

        // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
        return getEntry(path);
    }

    @Override
    public Enumeration<URL> getResources(String path) throws IOException {
        getBundleState().assertNotUninstalled();

        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        if (getBundleState().ensureResolved(true)) {
            ModuleClassLoader moduleClassLoader = getModuleClassLoader();
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
                LOGGER.errorCannotGetResources(ex, path, this);
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

