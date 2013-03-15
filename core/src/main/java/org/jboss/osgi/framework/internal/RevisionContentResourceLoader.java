package org.jboss.osgi.framework.internal;
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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.IterableResourceLoader;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.framework.spi.URLResource;
import org.jboss.osgi.framework.spi.VirtualFileResourceLoader;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleWiring;

/**
 * An {@link ResourceLoader} that is backed by a {@link RevisionContent} pointing to an archive.
 *
 * @author thomas.diesler@jboss.com
 * @since 13-Jan-2010
 */
final class RevisionContentResourceLoader implements IterableResourceLoader {

    private final HostBundleRevision hostRev;
    private final RevisionContent revContent;
    private final IterableResourceLoader delegate;

    RevisionContentResourceLoader(HostBundleRevision hostRev, RevisionContent revContent) {
        assert hostRev != null : "Null hostRev";
        assert revContent != null : "Null revContent";
        this.delegate = new VirtualFileResourceLoader(revContent.getVirtualFile());
        this.revContent = revContent;
        this.hostRev = hostRev;
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

    @Override
    public Iterator<Resource> iterateResources(String startPath, boolean recursive) {

        // Collect paths for substituted packages
        List<String> importedPaths = new ArrayList<String>();
        BundleWiring wiring = hostRev.getBundle().adapt(BundleWiring.class);
        List<BundleRequirement> preqs = wiring != null ? wiring.getRequirements(PackageNamespace.PACKAGE_NAMESPACE) : null;
        if (preqs != null) {
            for (BundleRequirement req : preqs) {
                XPackageRequirement preq = (XPackageRequirement) req;
                String packageName = preq.getPackageName();
                importedPaths.add(packageName.replace('.', '/'));
            }
        }
        Iterator<Resource> itres = delegate.iterateResources(startPath, recursive);
        if (importedPaths.isEmpty()) {
            return itres;
        }

        // Filter substituted packages
        List<Resource> filteredResources = new ArrayList<Resource>();
        while(itres.hasNext()) {
            Resource res = itres.next();
            String pathname = res.getName();
            int lastIndex = pathname.lastIndexOf('/');
            String respath = lastIndex > 0 ? pathname.substring(0, lastIndex) : pathname;
            if (!importedPaths.contains(respath)) {
                filteredResources.add(res);
            }
        }

        return filteredResources.iterator();
    }

    @Override
    public String toString() {
        return revContent.toString();
    }
}
