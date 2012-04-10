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
package org.jboss.osgi.framework.util;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * An {@link ResourceLoader} that is backed by a {@link VirtualFile} pointing to an archive.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public final class VirtualFileResourceLoader implements ResourceLoader {

    private final VirtualFile virtualFile;
    private final Set<String> localPaths;

    public VirtualFileResourceLoader(VirtualFile virtualFile) {
        if (virtualFile == null) 
            throw MESSAGES.illegalArgumentNull("virtualFile");
        this.virtualFile = virtualFile;
        this.localPaths = getLocalPaths();
    }

    @Override
    public String getRootName() {
        return virtualFile.getPathName();
    }

    @Override
    public ClassSpec getClassSpec(String fileName) throws IOException {
        VirtualFile child = virtualFile.getChild(fileName);
        if (child == null)
            return null;

        ClassSpec classSpec = new ClassSpec();
        InputStream is = child.openStream();
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
            VFSUtils.copyStream(is, os);
            classSpec.setBytes(os.toByteArray());
        } finally {
            safeClose(is);
        }

        CodeSigner[] codeSigners = child.getCodeSigners();
        classSpec.setCodeSource(new CodeSource(new URL("jar", null, -1, child.getName()), codeSigners));

        return classSpec;
    }

    @Override
    public PackageSpec getPackageSpec(String name) throws IOException {
        PackageSpec spec = new PackageSpec();
        Manifest manifest = VFSUtils.getManifest(virtualFile);
        if (manifest == null) {
            return spec;
        }
        Attributes mainAttribute = manifest.getAttributes(name);
        Attributes entryAttribute = manifest.getAttributes(name);
        spec.setSpecTitle(getDefinedAttribute(Attributes.Name.SPECIFICATION_TITLE, entryAttribute, mainAttribute));
        spec.setSpecVersion(getDefinedAttribute(Attributes.Name.SPECIFICATION_VERSION, entryAttribute, mainAttribute));
        spec.setSpecVendor(getDefinedAttribute(Attributes.Name.SPECIFICATION_VENDOR, entryAttribute, mainAttribute));
        spec.setImplTitle(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_TITLE, entryAttribute, mainAttribute));
        spec.setImplVersion(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VERSION, entryAttribute, mainAttribute));
        spec.setImplVendor(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VENDOR, entryAttribute, mainAttribute));
        if (Boolean.parseBoolean(getDefinedAttribute(Attributes.Name.SEALED, entryAttribute, mainAttribute))) {
            spec.setSealBase(virtualFile.toURL());
        }
        return spec;
    }

    private static String getDefinedAttribute(Attributes.Name name, Attributes entryAttribute, Attributes mainAttribute) {
        final String value = entryAttribute == null ? null : entryAttribute.getValue(name);
        return value == null ? mainAttribute == null ? null : mainAttribute.getValue(name) : value;
    }

    @Override
    public Resource getResource(String name) {
        try {
            VirtualFile child = virtualFile.getChild(name);
            if (child == null)
                return null;

            return new VirtualResource(child);
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public String getLibrary(String name) {
        return null;
    }

    @Override
    public Collection<String> getPaths() {
        return localPaths;
    }

    private Set<String> getLocalPaths() {
        Set<String> result = new HashSet<String>();
        try {
            List<VirtualFile> descendants = virtualFile.getChildrenRecursively();
            String rootPath = virtualFile.getPathName();
            for (VirtualFile descendant : descendants) {
                if (descendant.isFile()) {
                    String entryPath = descendant.getPathName().substring(rootPath.length());
                    if (entryPath.startsWith("/"))
                        entryPath = entryPath.substring(1);

                    int inx = entryPath.lastIndexOf("/");
                    result.add(inx > 0 ? entryPath.substring(0, inx) : "");
                }
            }
        } catch (IOException ex) {
            throw MESSAGES.illegalArgumentCannotObtainPaths(ex,  virtualFile);
        }
        if (result.size() == 0)
            throw MESSAGES.illegalArgumentCannotObtainPaths(null,  virtualFile);

        return Collections.unmodifiableSet(result);
    }

    private void safeClose(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    static class VirtualResource implements Resource {

        VirtualFile child;

        VirtualResource(VirtualFile child) {
            assert child != null : "Null child";
            this.child = child;
        }

        @Override
        public String getName() {
            return child.getName();
        }

        @Override
        public URL getURL() {
            try {
                return child.toURL();
            } catch (IOException ex) {
                throw MESSAGES.illegalStateCannotObtainURL(child);
            }
        }

        @Override
        public InputStream openStream() throws IOException {
            return child.openStream();
        }

        @Override
        public long getSize() {
            return 0;
        }
    }
}

