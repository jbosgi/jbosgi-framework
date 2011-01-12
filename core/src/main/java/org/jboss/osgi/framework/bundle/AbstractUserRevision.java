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
package org.jboss.osgi.framework.bundle;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;

/**
 * An abstract bundle revision that is based on a user {@link Deployment}.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public abstract class AbstractUserRevision extends AbstractRevision {

    static final Logger log = Logger.getLogger(AbstractUserRevision.class);

    private final Deployment deployment;
    private List<VirtualFile> contentRoots;
    private final EntriesProvider entriesProvider;

    AbstractUserRevision(AbstractUserBundle bundleState, Deployment dep) throws BundleException {
        super(bundleState, getOSGiMetaData(dep), getXModule(dep), getRevision(dep));
        this.deployment = dep;

        if (dep.getRoot() != null) {
            entriesProvider = new VirtualFileEntriesProvider(dep.getRoot());
            contentRoots = getBundleClassPath(dep.getRoot(), getOSGiMetaData());
        } else {
            entriesProvider = new ModuleEntriesProvider(dep.getAttachment(Module.class));
            contentRoots = Collections.emptyList();
        }
    }

    private static OSGiMetaData getOSGiMetaData(Deployment dep) {
        return dep.getAttachment(OSGiMetaData.class);
    }

    private static XModule getXModule(Deployment dep) {
        return dep.getAttachment(XModule.class);
    }

    private static int getRevision(Deployment dep) {
        BundleStorageState storageState = dep.getAttachment(BundleStorageState.class);
        return storageState.getRevision();
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public String getLocation() {
        return deployment.getLocation();
    }

    /**
     * A user revision may have one or more associated content root files. Multiple content root files exist if there is a
     * Bundle-ClassPath header. No content root file may exist if bundle metadata was provided externally.
     * 
     * @return The first entry in the list of content root files or null
     */
    public VirtualFile getFirstContentRoot() {
        return contentRoots.size() > 0 ? contentRoots.get(0) : null;
    }

    /**
     * A user revision may have one or more associated content root files. Multiple content root files exist if there is a
     * Bundle-ClassPath header. No content root file may exist if bundle metadata was provided externally.
     * 
     * @return The list of content root files or an empty list
     */
    public List<VirtualFile> getContentRoots() {
        return contentRoots;
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        getBundleState().assertNotUninstalled();
        return entriesProvider.getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        getBundleState().assertNotUninstalled();
        return entriesProvider.getEntry(path);
    }

    @Override
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        getBundleState().assertNotUninstalled();
        return entriesProvider.findEntries(path, pattern, recurse);
    }

    private List<VirtualFile> getBundleClassPath(VirtualFile rootFile, OSGiMetaData metadata) {
        if (rootFile == null)
            throw new IllegalArgumentException("Null rootFile");

        // Setup single root file list, if there is no Bundle-ClassPath
        if (metadata.getBundleClassPath().size() == 0) {
            List<VirtualFile> rootList = new ArrayList<VirtualFile>(Collections.singleton(rootFile));
            return Collections.unmodifiableList(rootList);
        }

        // Add the Bundle-ClassPath to the root virtual files
        List<VirtualFile> rootList = new ArrayList<VirtualFile>();
        for (String path : metadata.getBundleClassPath()) {
            if (path.equals(".")) {
                rootList.add(rootFile);
            } else {
                try {
                    VirtualFile child = rootFile.getChild(path);
                    if (child != null) {
                        VirtualFile root = AbstractVFS.toVirtualFile(child.toURL());
                        rootList.add(root);
                    }
                } catch (IOException ex) {
                    log.errorf(ex, "Cannot get class path element: %s", path);
                }
            }
        }
        return Collections.unmodifiableList(rootList);
    }
}
