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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.jboss.modules.Module;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
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
abstract class UserBundleRevision extends AbstractBundleRevision {

    private final Deployment deployment;
    private List<RevisionContent> contentList;
    private final EntriesProvider entriesProvider;

    UserBundleRevision(UserBundleState userBundle, Deployment dep) throws BundleException {
        super(userBundle, getOSGiMetaData(dep), getRevisionId(dep));
        
        this.deployment = dep;

        if (dep.getRoot() != null) {
            contentList = getBundleClassPath(dep.getRoot(), getOSGiMetaData());
            entriesProvider = getRootContent();
        } else {
            Module module = dep.getAttachment(Module.class);
            entriesProvider = new ModuleEntriesProvider(module);
            contentList = Collections.emptyList();
            addAttachment(Module.class, module);
        }
    }

    private static OSGiMetaData getOSGiMetaData(Deployment dep) {
        return dep.getAttachment(OSGiMetaData.class);
    }

    private static int getRevisionId(Deployment dep) {
        StorageState storageState = dep.getAttachment(StorageState.class);
        return storageState.getRevisionId();
    }

    Deployment getDeployment() {
        return deployment;
    }

    @Override
    String getLocation() {
        return deployment.getLocation();
    }

    UserBundleState getBundleState() {
        return (UserBundleState) super.getBundleState();
    }

    /**
     * A user revision may have one or more associated content root files. Multiple content root files exist if there is a
     * Bundle-ClassPath header. No content root file may exist if bundle metadata was provided externally.
     *
     * @return The first entry in the list of content root files or null
     */
    RevisionContent getRootContent() {
        return contentList.size() > 0 ? contentList.get(0) : null;
    }

    /**
     * A user revision may have one or more associated content root files. Multiple content root files exist if there is a
     * Bundle-ClassPath header. No content root file may exist if bundle metadata was provided externally.
     *
     * @return The list of content root files or an empty list
     */
    List<RevisionContent> getContentList() {
        return contentList;
    }

    RevisionContent getContentById(int contentId) {
        for (RevisionContent aux : contentList) {
            if (aux.getContentId() == contentId) {
                return aux;
            }
        }
        return null;
    }

    void close() {
        for (RevisionContent aux : contentList) {
            aux.close();
        }
    }

    @Override
    Enumeration<String> getEntryPaths(String path) {
        getBundleState().assertNotUninstalled();
        return entriesProvider.getEntryPaths(path);
    }

    @Override
    URL getEntry(String path) {
        getBundleState().assertNotUninstalled();
        return entriesProvider.getEntry(path);
    }

    @Override
    Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        getBundleState().assertNotUninstalled();
        return entriesProvider.findEntries(path, pattern, recurse);
    }

    private List<RevisionContent> getBundleClassPath(VirtualFile rootFile, OSGiMetaData metadata) {
        assert rootFile != null : "Null rootFile";

        // Setup single root file list, if there is no Bundle-ClassPath
        if (metadata.getBundleClassPath().size() == 0) {
            RevisionContent revContent = new RevisionContent(this, 0, rootFile);
            return Collections.singletonList(revContent);
        }

        // Add the Bundle-ClassPath to the root virtual files
        List<RevisionContent> rootList = new ArrayList<RevisionContent>();
        for (String path : metadata.getBundleClassPath()) {
            if (path.equals(".")) {
                RevisionContent revContent = new RevisionContent(this, rootList.size(), rootFile);
                rootList.add(revContent);
            } else {
                try {
                    VirtualFile child = rootFile.getChild(path);
                    if (child != null) {
                        VirtualFile anotherRoot = AbstractVFS.toVirtualFile(child.toURL());
                        RevisionContent revContent = new RevisionContent(this, rootList.size(), anotherRoot);
                        rootList.add(revContent);
                    }
                } catch (IOException ex) {
                    LOGGER.errorCannotGetClassPathEntry(ex, path, this);
                }
            }
        }
        return Collections.unmodifiableList(rootList);
    }
}
