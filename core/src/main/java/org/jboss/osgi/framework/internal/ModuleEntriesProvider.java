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

import java.net.URL;
import java.util.Enumeration;
import java.util.Vector;

import org.jboss.modules.Module;

/**
 * A bundle entries provider.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Sep-2010
 */
final class ModuleEntriesProvider implements EntriesProvider {

    private final Module module;

    ModuleEntriesProvider(Module module) {
        assert module != null : "Null module";
        this.module = module;
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        Enumeration<URL> urls = module.getExportedResources(path);
        if (urls == null)
            return null;

        Vector<String> result = new Vector<String>();
        while (urls.hasMoreElements())
            result.add(urls.nextElement().getPath());

        return result.elements();
    }

    @Override
    public URL getEntry(String path) {
        return module.getExportedResource(path);
    }

    @Override
    public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) {
        return null;
    }
}
