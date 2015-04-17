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

import static org.jboss.osgi.vfs.VFSMessages.MESSAGES;

import java.io.File;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.modules.Module;
import org.jboss.modules.Resource;

/**
 * A bundle entries provider.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Sep-2010
 */
public final class ModuleEntriesProvider implements EntriesProvider {

    private final Module module;

    public ModuleEntriesProvider(Module module) {
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
        if (path == null)
            throw MESSAGES.illegalArgumentNull("path");

        if (pattern == null)
            pattern = "*";

        if (path.startsWith("/"))
            path = path.substring(1);

        final Pattern filter = convertToPattern(pattern);
        final Iterator<Resource> it = module.getClassLoader().iterateResources(path, recurse);
        return new Enumeration<URL>() {

            Resource current;

            @Override
            public synchronized boolean hasMoreElements()
            {
                return current != null || (current = findNext()) != null;
            }

            @Override
            public synchronized URL nextElement()
            {
                Resource current = this.current;
                if(current == null) {
                    current = findNext();
                }
                if(current == null) {
                    throw new NoSuchElementException();
                }
                this.current = null;
                return current.getURL();
            }

            private Resource findNext()
            {
                for (;;) {
                    if (!it.hasNext()) {
                        return null;
                    }
                    Resource r = it.next();
                    String fullName = r.getName();
                    String simpleName = new File(fullName).getName();
                    Matcher matcher = filter.matcher(simpleName);
                    if (matcher.find()) {
                        return r;
                    }
                }
            }
        };
    }

    // Convert file pattern (RFC 1960-based Filter) into a RegEx pattern
    private static Pattern convertToPattern(String filePattern) {
        filePattern = filePattern.replace("*", ".*");
        return Pattern.compile("^" + filePattern + "$");
    }
}
