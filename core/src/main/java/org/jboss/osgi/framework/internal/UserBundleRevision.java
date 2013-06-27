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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.InternalConstants.MODULE_KEY;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;
import org.osgi.framework.wiring.BundleRevision;

/**
 * An abstract bundle revision that is based on a user {@link Deployment}.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
abstract class UserBundleRevision extends BundleStateRevision {

    private final Deployment deployment;
    private final ServiceTarget serviceTarget;
    private final List<RevisionContent> classPathContent;
    private final EntriesProvider entriesProvider;

    UserBundleRevision(FrameworkState frameworkState, OSGiMetaData metadata, StorageState storageState, Deployment deployment, ServiceTarget serviceTarget) throws BundleException {
        super(frameworkState, metadata, storageState);
        assert deployment != null : "Null deployment";
        assert serviceTarget != null : "Null serviceTarget";
        this.deployment = deployment;
        this.serviceTarget = serviceTarget;

        if (deployment.getRoot() != null) {
            List<RevisionContent> bundleClassPath = new ArrayList<RevisionContent>();
            entriesProvider = getBundleClassPath(deployment.getRoot(), metadata, storageState, bundleClassPath);
            classPathContent = Collections.unmodifiableList(bundleClassPath);
        } else {
            Module module = deployment.getAttachment(MODULE_KEY);
            entriesProvider = new ModuleEntriesProvider(module);
            classPathContent = Collections.emptyList();
            putAttachment(MODULE_KEY, module);
        }

        deployment.putAttachment(IntegrationConstants.BUNDLE_REVISION_KEY, this);
        putAttachment(IntegrationConstants.DEPLOYMENT_KEY, deployment);
    }

    static UserBundleRevision assertBundleRevision(BundleRevision brev) {
        assert brev instanceof UserBundleRevision : "Not an UserBundleRevision: " + brev;
        return (UserBundleRevision) brev;
    }

    UserBundleState getBundleState() {
        return (UserBundleState) getBundle();
    }

    Deployment getDeployment() {
        return deployment;
    }

    ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    @Override
    String getLocation() {
        return getDeployment().getLocation();
    }

    /**
     * A user revision may have one or more associated content root files. Multiple content root files exist if there is a
     * Bundle-ClassPath header. No content root file may exist if bundle metadata was provided externally.
     *
     * @return The list of content root files or an empty list
     */
    List<RevisionContent> getClassPathContent() {
        return classPathContent;
    }

    RevisionContent getContentById(int contentId) {
        for (RevisionContent aux : classPathContent) {
            if (aux.getContentId() == contentId) {
                return aux;
            }
        }
        return null;
    }

    abstract void refreshRevision();

    @Override
    void close() {
        super.close();
        for (RevisionContent aux : classPathContent) {
            aux.close();
        }
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

    private RevisionContent getBundleClassPath(VirtualFile rootFile, OSGiMetaData metadata, StorageState storageState, List<RevisionContent> bundleClassPath) {
        assert rootFile != null : "Null rootFile";

        long bundleId = storageState.getBundleId();
        RevisionContent rootContent = new RevisionContent(this, metadata, bundleId, 0, rootFile);

        // Setup single root file list, if there is no Bundle-ClassPath
        if (metadata.getBundleClassPath() == null) {
            bundleClassPath.add(rootContent);
            return rootContent;
        }

        // Add the Bundle-ClassPath to the root virtual files
        for (String path : metadata.getBundleClassPath()) {
            if (path.equals(".")) {
                bundleClassPath.add(rootContent);
            } else {
                try {
                    VirtualFile child = rootFile.getChild(path);
                    if (child != null) {
                        VirtualFile anotherRoot = AbstractVFS.toVirtualFile(child.toURL());
                        RevisionContent revContent = new RevisionContent(this, metadata, bundleId, bundleClassPath.size(), anotherRoot);
                        bundleClassPath.add(revContent);
                    }
                } catch (IOException ex) {
                    LOGGER.errorCannotGetClassPathEntry(ex, path, this);
                }
            }
        }
        return rootContent;
    }
}
