/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Privileged actions used by this package. 
 * No methods in this class are to be made public under any circumstances!
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 29-Oct-2010
 */
final class SecurityActions {

    // Hide ctor
    private SecurityActions() {
    }

    /**
     * Get the thread context class loader
     */
    static ClassLoader getContextClassLoader() {
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                Thread currentThread = Thread.currentThread();
                return currentThread.getContextClassLoader();
            }
        });
    }

    /**
     * Set the thread context class loader
     */
    static Void setContextClassLoader(final ClassLoader classLoader) {
        return AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                Thread currentThread = Thread.currentThread();
                currentThread.setContextClassLoader(classLoader);
                return null;
            }
        });
    }

    static String getSystemProperty(final String key) {
        if (System.getSecurityManager() == null) {
            return System.getProperty(key);
        } else {
            return AccessController.doPrivileged(new PrivilegedAction<String>() {
                public String run() {
                    return System.getProperty(key);
                }
            });
        }
    }

    static void setSystemProperty(final String key, final String value) {
        if (System.getSecurityManager() == null) {
            System.setProperty(key, value);
        } else {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    System.setProperty(key, value);
                    return null;
                }
            });
        }
    }
}
