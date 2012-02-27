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

import org.jboss.osgi.vfs.VirtualFile;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * A collection of IO utilities.
 * 
 * @author thomas.diesler@jboss.com
 * @since 19-Dec-2009
 */
public final class IOUtils {

    // Hide the ctor
    private IOUtils() {
    }

    /**
     * Get a URL from the given string
     * 
     * @throws IllegalArgumentException if the urlstr does not represent a valid URL
     */
    public static URL toURL(String urlstr) {
        try {
            return new URL(urlstr);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Malformed URL: " + urlstr);
        }
    }

    /**
     * Get a URL from the given virtual file
     * 
     * @throws IllegalArgumentException if the file does not represent a valid URL
     */
    public static URL toURL(VirtualFile file) {
        try {
            return file.toURL();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot convert to URL: " + file, ex);
        }
    }

}
