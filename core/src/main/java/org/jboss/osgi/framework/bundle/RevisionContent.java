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
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Enumeration;
import java.util.Vector;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.plugin.internal.BundleProtocolHandler;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * An abstraction for the revision content
 * 
 * @author thomas.diesler@jboss.com
 * @since 13-Jan-2011
 */
public final class RevisionContent implements EntriesProvider {

    static final Logger log = Logger.getLogger(RevisionContent.class);

    private final AbstractUserRevision userRev;
    private final VirtualFile virtualFile;
    private final String identity;
    private final int contentId;

    RevisionContent(AbstractUserRevision userRev, int contentId, VirtualFile rootFile) {
        if (userRev == null)
            throw new IllegalArgumentException("Null userRev");
        if (rootFile == null)
            throw new IllegalArgumentException("Null rootFile");
        this.userRev = userRev;
        this.virtualFile = rootFile;
        this.contentId = contentId;

        AbstractBundle bundleState = userRev.getBundleState();
        String symbolicName = bundleState.getSymbolicName();
        symbolicName = symbolicName.replace(':', '.');
        symbolicName = symbolicName.replace('-', '.');
        long bundleId = bundleState.getBundleId();
        int revisionId = userRev.getRevisionId();
        identity = symbolicName + "-" + bundleId + "-" + revisionId + "-" + contentId;
    }
    
    public static RevisionContent findRevisionContent(BundleManager bundleManager, String identity) {
        if (identity == null)
            throw new IllegalArgumentException("Null identity");
        String[] parts = identity.split("-");
        if (parts.length != 4)
            throw new IllegalArgumentException("Invalid identity: " + identity);
        long bundleId = Long.parseLong(parts[1]);
        int revisionId = Integer.parseInt(parts[2]);
        int contentId = Integer.parseInt(parts[3]);
        AbstractBundle bundleState = bundleManager.getBundleById(bundleId);
        if (bundleState == null)
            return null;
        AbstractRevision bundleRev = bundleState.getRevisionById(revisionId);
        if (bundleRev == null)
            return null;
        AbstractUserRevision userRev = AbstractUserRevision.assertUserRevision(bundleRev);
        RevisionContent revContent = userRev.getContentById(contentId);
        return revContent;
    }

    public int getContentId() {
        return contentId;
    }

    public String getIdentity() {
        return identity;
    }

    public AbstractUserRevision getRevision() {
        return userRev;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    @Override
    public URL getEntry(String path) {
        VirtualFile child;
        try {
            child = virtualFile.getChild(path);
            return child != null ? toBundleURL(child) : null;
        } catch (IOException ex) {
            log.errorf(ex, "Cannot get entry: %s", path);
            return null;
        }
    }

    @Override
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        try {
            Enumeration<URL> urls = virtualFile.findEntries(path, pattern, recurse);
            return toBundleURLs(urls);
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        try {
            return virtualFile.getEntryPaths(path);
        } catch (IOException ex) {
            return null;
        }
    }

    public void close() {
        VFSUtils.safeClose(virtualFile);
    }

    private Enumeration<URL> toBundleURLs(Enumeration<URL> urls) throws IOException {
        if (urls == null)
            return null;

        Vector<URL> result = new Vector<URL>();
        while (urls.hasMoreElements()) {
            VirtualFile child = AbstractVFS.toVirtualFile(urls.nextElement());
            result.add(toBundleURL(child));
        }

        return result.elements();
    }

    private URL toBundleURL(final VirtualFile child) throws IOException {
        URLStreamHandler streamHandler = new URLStreamHandler() {
            protected URLConnection openConnection(URL url) throws IOException {
                return child.toURL().openConnection();
            }
        };
        String rootPath = virtualFile.getPathName();
        String pathName = child.getPathName().substring(rootPath.length());
        return new URL(BundleProtocolHandler.PROTOCOL_NAME, identity, -1, pathName, streamHandler);
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
