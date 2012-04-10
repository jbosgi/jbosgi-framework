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

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.BundleException;

/**
 * A {@link FragmentBundleRevision} is responsible for the classloading and resource loading of a fragment.
 * <p/>
 * Every time a fragment is updated a new {@link FragmentBundleRevision} is created
 * and referenced from the {@link FragmentBundleState}.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
final class FragmentBundleRevision extends UserBundleRevision {

    private Set<HostBundleRevision> attachedHosts;

    FragmentBundleRevision(FragmentBundleState bundleState, Deployment dep) throws BundleException {
        super(bundleState, dep);
    }

    /**
     * Assert that the given bundleRev is an instance of FragmentRevision
     */
    static FragmentBundleRevision assertFragmentRevision(AbstractBundleRevision bundleRev) {
        assert bundleRev != null : "Null bundleRev";
        assert bundleRev instanceof FragmentBundleRevision : "Not an FragmentRevision: " + bundleRev;
        return (FragmentBundleRevision) bundleRev;
    }

    @Override
    FragmentBundleState getBundleState() {
        return (FragmentBundleState) super.getBundleState();
    }

    void refreshRevisionInternal() {
        attachedHosts = null;
    }

    Set<HostBundleRevision> getAttachedHosts() {
        if (attachedHosts == null)
            return Collections.emptySet();

        return Collections.unmodifiableSet(attachedHosts);
    }

    @Override
    Class<?> loadClass(String className) throws ClassNotFoundException {
        throw MESSAGES.cannotLoadClassFromFragment(this);
    }

    @Override
    URL getResource(String resourceName) {
        // Null if the resource could not be found or if this bundle is a fragment bundle
        return null;
    }

    @Override
    Enumeration<URL> getResources(String resourceName) throws IOException {
        // Null if the resource could not be found or if this bundle is a fragment bundle
        return null;
    }

    void attachToHost(HostBundleRevision hostRev) {
        if (attachedHosts == null)
            attachedHosts = new HashSet<HostBundleRevision>();

        hostRev.attachFragment(this);
        attachedHosts.add(hostRev);
    }

    @Override
    URL getLocalizationEntry(String path) {

        URL result = null;

        // #Bug1867 - Finding Localization Entries for Fragments
        // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1867
        boolean fallbackToFragment = true;

        // If the bundle is a resolved fragment, then the search for localization data must
        // delegate to the attached host bundle with the highest version.
        if (isResolved()) {
            HostBundleRevision highest = null;
            for (HostBundleRevision hostrev : getAttachedHosts()) {
                if (highest == null)
                    highest = hostrev;
                if (highest.getVersion().compareTo(hostrev.getVersion()) < 0)
                    highest = hostrev;
            }
            if (highest == null)
                throw FrameworkMessages.MESSAGES.illegalStateCannotObtainAttachedHost(this);

            boolean hostUninstalled = highest.getBundleState().isUninstalled();
            result = (hostUninstalled ? getEntry(path) : highest.getLocalizationEntry(path));

            // In contrary to the spec the TCK ManifestLocalizationTests.testGetHeaders010()
            // expects to find the localization files in the fragment if they were not found
            // in the attached host
            if (result != null || fallbackToFragment == false)
                return result;
        }

        // If the fragment is not resolved, then the framework must search the fragment's JAR for the localization entry.
        result = getEntry(path);
        return result;
    }
}
