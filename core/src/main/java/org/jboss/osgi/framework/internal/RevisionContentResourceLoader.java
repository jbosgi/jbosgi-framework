/*
 * #%L
 * JBossOSGi Framework Core
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

import java.io.IOException;
import java.net.URL;
import java.util.Collection;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.framework.util.VirtualFileResourceLoader;

/**
 * An {@link ResourceLoader} that is backed by a {@link RevisionContent} pointing to an archive.
 * 
 * @author thomas.diesler@jboss.com
 * @since 13-Jan-2010
 */
final class RevisionContentResourceLoader implements ResourceLoader {

    private final RevisionContent revContent;
    private final VirtualFileResourceLoader delegate;

    RevisionContentResourceLoader(RevisionContent revContent) {
        assert revContent != null : "Null revContent";
        this.delegate = new VirtualFileResourceLoader(revContent.getVirtualFile());
        this.revContent = revContent;
    }

    @Override
    public String getRootName() {
        return delegate.getRootName();
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
