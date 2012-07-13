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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A utility class which maintains the set of JDK paths.  
 *
 * @author david.lloyd@redhat.com
 * @author thomas.diesler@jboss.com
 * @since 27-Jul-2011
 */
final class JDKPaths {
    static final Set<String> JDK;

    static {
        final Set<String> pathSet = new HashSet<String>(1024);
        final Set<String> jarSet = new HashSet<String>(1024);
        final String sunBootClassPath = SecurityActions.getSystemProperty("sun.boot.class.path", null);
        final String javaClassPath = SecurityActions.getSystemProperty("java.class.path", null);
        processClassPathItem(sunBootClassPath, jarSet, pathSet);
        processClassPathItem(javaClassPath, jarSet, pathSet);
        JDK = pathSet;
    }

    private JDKPaths() {
    }

    private static void processClassPathItem(final String classPath, final Set<String> jarSet, final Set<String> pathSet) {
        if (classPath == null) return;
        int s = 0, e;
        do {
            e = classPath.indexOf(File.pathSeparatorChar, s);
            String item = e == -1 ? classPath.substring(s) : classPath.substring(s, e);
            if (! jarSet.contains(item)) {
                final File file = new File(item);
                if (file.isDirectory()) {
                    processDirectory0(pathSet, file);
                } else {
                    try {
                        final ZipFile zipFile = new ZipFile(file);
                        try {
                            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                            while (entries.hasMoreElements()) {
                                final ZipEntry entry = entries.nextElement();
                                final String name = entry.getName();
                                final int lastSlash = name.lastIndexOf('/');
                                if (lastSlash != -1) {
                                    pathSet.add(name.substring(0, lastSlash));
                                }
                            }
                        } finally {
                            zipFile.close();
                        }
                    } catch (IOException ex) {
                        // ignore
                    }
                }
            }
            s = e + 1;
        } while (e != -1);
    }

    private static void processDirectory0(final Set<String> pathSet, final File file) {
        for (File entry : file.listFiles()) {
            if (entry.isDirectory()) {
                processDirectory1(pathSet, entry, file.getPath());
            } else {
                final String parent = entry.getParent();
                if (parent != null) pathSet.add(parent);
            }
        }
    }

    private static void processDirectory1(final Set<String> pathSet, final File file, final String pathBase) {
        for (File entry : file.listFiles()) {
            if (entry.isDirectory()) {
                processDirectory1(pathSet, entry, pathBase);
            } else {
                String packagePath = entry.getParent();
                if (packagePath != null) {
                    packagePath = packagePath.substring(pathBase.length()).replace('\\', '/');;
                    if(packagePath.startsWith("/")) {
                        packagePath = packagePath.substring(1);
                    }
                    pathSet.add(packagePath);
                }
            }
        }
    }
}
