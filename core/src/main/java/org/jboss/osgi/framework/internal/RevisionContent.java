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
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Enumeration;
import java.util.Vector;

import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;

/**
 * An abstraction for the revision content
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 13-Jan-2011
 */
final class RevisionContent implements EntriesProvider {

    private final UserBundleRevision userRev;
    private final VirtualFile virtualFile;
    private final String identity;
    private final int contentId;
    private boolean closedMarker;

    RevisionContent(UserBundleRevision brev, OSGiMetaData metadata, long bundleId, int contentId, VirtualFile rootFile) {
        assert brev != null : "Null userRev";
        assert rootFile != null : "Null rootFile";
        this.userRev = brev;
        this.virtualFile = rootFile;
        this.contentId = contentId;

        String symbolicName = metadata.getBundleSymbolicName();
        if (symbolicName != null) {
            symbolicName = symbolicName.replace(':', '.');
            symbolicName = symbolicName.replace('-', '.');
        } else {
            symbolicName = "anonymous";
        }
        int revisionId = brev.getRevisionId();
        identity = symbolicName + "-" + bundleId + "-" + revisionId + "-" + contentId;
        LOGGER.tracef("new RevisionContent: %s", identity);
    }

    static RevisionContent findRevisionContent(BundleManagerPlugin bundleManager, String identity) {
        assert identity != null : "Null identity";
        String[] parts = identity.split("-");
        assert parts.length == 4 : "Invalid identity: " + identity;
        long bundleId = Long.parseLong(parts[1]);
        int revisionId = Integer.parseInt(parts[2]);
        int contentId = Integer.parseInt(parts[3]);
        Bundle bundle = bundleManager.getBundleById(bundleId);
        if (bundle == null)
            return null;
        AbstractBundleState<?> bundleState = AbstractBundleState.assertBundleState(bundle);
        BundleStateRevision bundleRev = bundleState.getBundleRevisionById(revisionId);
        if (bundleRev == null)
            return null;
        UserBundleRevision userRev = (UserBundleRevision) bundleRev;
        RevisionContent revContent = userRev.getContentById(contentId);
        LOGGER.tracef("findRevisionContent: %s => %s", identity, revContent);
        return revContent;
    }

    int getContentId() {
        return contentId;
    }

    String getIdentity() {
        return identity;
    }

    UserBundleRevision getRevision() {
        return userRev;
    }

    VirtualFile getVirtualFile() {
        assertNotClosed();
        return virtualFile;
    }

    @Override
    public URL getEntry(String path) {
        assertNotClosed();
        VirtualFile child;
        try {
            child = virtualFile.getChild(path);
            return child != null ? getBundleURL(child) : null;
        } catch (IOException ex) {
            LOGGER.errorCannotGetEntry(ex, path, userRev);
            return null;
        }
    }

    @Override
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        assertNotClosed();
        try {
            Enumeration<URL> urls = virtualFile.findEntries(path, pattern, recurse);
            return getBundleURLs(urls);
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        assertNotClosed();
        try {
            Enumeration<String> entryPaths = virtualFile.getEntryPaths(path);
            if (entryPaths != null && entryPaths.hasMoreElements())
                return entryPaths;
            else
                return null;
        } catch (IOException ex) {
            return null;
        }
    }

    void close() {
        VFSUtils.safeClose(virtualFile);
        closedMarker = true;
    }

    private void assertNotClosed() {
        if (closedMarker) {
            throw MESSAGES.illegalStateRevisionContentClosed(userRev, contentId);
        }
    }

    private Enumeration<URL> getBundleURLs(Enumeration<URL> urls) throws IOException {
        if (urls == null)
            return null;

        if (!urls.hasMoreElements())
            return null;

        Vector<URL> result = new Vector<URL>();
        while (urls.hasMoreElements()) {
            VirtualFile child = AbstractVFS.toVirtualFile(urls.nextElement());
            result.add(getBundleURL(child));
        }

        return result.elements();
    }

    URL getBundleURL(final VirtualFile child) throws IOException {
        final String orgPath = child.getPathName();
        URLStreamHandler streamHandler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) throws IOException {
                String path = url.getPath();
                VirtualFile real = (orgPath.equals(path) ? child : virtualFile.getChild(path));
                return real.getStreamURL().openConnection();
            }

            // [TODO] overwrite hashCode for to prevent host address resolution
            // when offline the BundleEntriesTestCase is slow because of this
        };

        String rootPath = virtualFile.getPathName();
        String pathName = child.getPathName().substring(rootPath.length());

        // The path can potentially be made characters longer (one leading and one trailing slash)
        StringBuilder path = new StringBuilder(pathName.length() + 2);

        if (pathName.startsWith("/") == false)
            path.append('/');

        path.append(pathName);

        if (child.isDirectory() && path.charAt(path.length() - 1) != '/') {
            path.append('/');
        }

        return new URL(BundleProtocolHandler.PROTOCOL_NAME, identity, -1, path.toString(), streamHandler);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if ((obj instanceof RevisionContent) == false)
            return false;
        RevisionContent other = (RevisionContent) obj;
        return identity.equals(other.identity);
    }

    @Override
    public String toString() {
        return "[rev=" + userRev + ",id=" + identity + ",vfile=" + virtualFile + "]";
    }
}
