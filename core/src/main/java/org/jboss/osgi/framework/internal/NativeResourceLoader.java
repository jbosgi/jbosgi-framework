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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.framework.spi.NativeLibraryProvider;

/**
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
final class NativeResourceLoader implements ResourceLoader {

    // List of native library providers
    private volatile List<NativeLibraryProvider> nativeLibraries;

    NativeResourceLoader() {
    }

    void addNativeLibrary(NativeLibraryProvider libProvider) {
        if (nativeLibraries == null)
            nativeLibraries = new CopyOnWriteArrayList<NativeLibraryProvider>();

        nativeLibraries.add(libProvider);
    }

    @Override
    public String getLibrary(String libname) {
        List<NativeLibraryProvider> list = nativeLibraries;
        if (list == null)
            return null;

        NativeLibraryProvider libProvider = null;
        for (NativeLibraryProvider aux : list) {
            if (libname.equals(aux.getLibraryName())) {
                libProvider = aux;
                break;
            }
        }

        if (libProvider == null)
            return null;

        File libfile;
        try {
            libfile = libProvider.getLibraryLocation();
        } catch (IOException ex) {
            LOGGER.errorCannotProvideNativeLibraryLocation(ex, libname);
            return null;
        }

        return libfile.getAbsolutePath();
    }

    @Override
    public String getRootName() {
        return "undefined";
    }

    @Override
    public ClassSpec getClassSpec(String name) throws IOException {
        return null;
    }

    @Override
    public PackageSpec getPackageSpec(String name) throws IOException {
        return null;
    }

    @Override
    public Resource getResource(String name) {
        return null;
    }

    @Override
    public Collection<String> getPaths() {
        return Collections.emptyList();
    }
}
