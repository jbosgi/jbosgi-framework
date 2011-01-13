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
package org.jboss.osgi.framework.loading;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.PathFilter;
import org.jboss.modules.PathFilters;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.framework.bundle.RevisionContent;

/**
 * An {@link ResourceLoader} that is backed by a {@link RevisionContent} pointing to an archive.
 * 
 * @author thomas.diesler@jboss.com
 * @since 13-Jan-2010
 */
public final class RevisionContentResourceLoader implements ResourceLoader {

    private final RevisionContent revContent;
    private final VirtualFileResourceLoader delegate;
    private final PathFilter exportFilter;

    public RevisionContentResourceLoader(RevisionContent revContent) {
        this(revContent, PathFilters.acceptAll());
    }

    public RevisionContentResourceLoader(RevisionContent revContent, PathFilter exportFilter) {
        if (revContent == null)
            throw new IllegalArgumentException("Null revContent");
        if (exportFilter == null)
            throw new IllegalArgumentException("Null exportFilter");

        this.delegate = new VirtualFileResourceLoader(revContent.getVirtualFile(), exportFilter);
        this.revContent = revContent;
        this.exportFilter = exportFilter;
    }

    @Override
    public String getRootName() {
        return delegate.getRootName();
    }

    @Override
    public PathFilter getExportFilter() {
        return exportFilter;
    }

    @Override
    public ClassSpec getClassSpec(String fileName) throws IOException {
        return delegate.getClassSpec(fileName);
    }

    @Override
    public PackageSpec getPackageSpec(String name) throws IOException {
        return delegate.getPackageSpec(name);
    }

    @Override
    public Resource getResource(String path) {
        URL url = revContent.getEntry(path);
        return url != null ? new URLResource(url) : null;
    }

    @Override
    public String getLibrary(String name) {
        return null;
    }

    @Override
    public Collection<String> getPaths() {
        return delegate.getPaths();
    }
}
