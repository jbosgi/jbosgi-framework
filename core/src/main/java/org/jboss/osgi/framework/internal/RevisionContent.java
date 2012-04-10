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
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Enumeration;
import java.util.Vector;

import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;

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

    RevisionContent(UserBundleRevision userRev, int contentId, VirtualFile rootFile) {
        assert userRev != null : "Null userRev";
        assert rootFile != null : "Null rootFile";
        this.userRev = userRev;
        this.virtualFile = rootFile;
        this.contentId = contentId;

        AbstractBundleState bundleState = userRev.getBundleState();
        String symbolicName = bundleState.getSymbolicName();
        if (symbolicName != null) {
            symbolicName = symbolicName.replace(':', '.');
            symbolicName = symbolicName.replace('-', '.');
        } else {
            symbolicName = "anonymous";
        }
        long bundleId = bundleState.getBundleId();
        int revisionId = userRev.getRevisionId();
        identity = symbolicName + "-" + bundleId + "-" + revisionId + "-" + contentId;
    }

    static RevisionContent findRevisionContent(BundleManager bundleManager, String identity) {
        assert identity != null : "Null identity";
        String[] parts = identity.split("-");
        assert parts.length == 4 : "Invalid identity: " + identity;
        long bundleId = Long.parseLong(parts[1]);
        int revisionId = Integer.parseInt(parts[2]);
        int contentId = Integer.parseInt(parts[3]);
        AbstractBundleState bundleState = bundleManager.getBundleById(bundleId);
        if (bundleState == null)
            return null;
        AbstractBundleRevision bundleRev = bundleState.getBundleRevisionById(revisionId);
        if (bundleRev == null)
            return null;
        UserBundleRevision userRev = (UserBundleRevision) bundleRev;
        RevisionContent revContent = userRev.getContentById(contentId);
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
        return virtualFile;
    }

    @Override
    public URL getEntry(String path) {
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
        try {
            Enumeration<URL> urls = virtualFile.findEntries(path, pattern, recurse);
            return getBundleURLs(urls);
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
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
        return "[id=" + identity + ",vfile=" + virtualFile + "]";
    }
}
