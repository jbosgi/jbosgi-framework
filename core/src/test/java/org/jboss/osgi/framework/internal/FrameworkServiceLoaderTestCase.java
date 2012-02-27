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

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.junit.Test;

import java.net.URL;
import java.net.URLStreamHandlerFactory;
import java.util.Iterator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test whether we can load a service through the framework module
 *
 * @author Thomas.Diesler@jboss.com
 * @since 10-Jan-2011
 */
public class FrameworkServiceLoaderTestCase extends AbstractFrameworkTest {

    @Test
    public void testServiceLoaderFails() throws Exception {

        // The {@link ModularURLStreamHandlerFactory} follows a pattern similar to this.
        SystemBundleState systemBundle = getBundleManager().getSystemBundle();
        ModuleManagerPlugin plugin = getFrameworkState().getModuleManagerPlugin();
        Module frameworkModule = plugin.loadModule(systemBundle.getModuleIdentifier());
        assertNotNull("Framework module not null", frameworkModule);

        // Test resource access
        ModuleClassLoader classLoader = frameworkModule.getClassLoader();
        URL resource = classLoader.getResource("META-INF/services/" + URLStreamHandlerFactory.class.getName());
        assertNull("Resource URL null", resource);

        // Test ServiceLoader access
        Iterator<URLStreamHandlerFactory> iterator = frameworkModule.loadService(URLStreamHandlerFactory.class).iterator();
        assertFalse("No more URLStreamHandlerFactory", iterator.hasNext());
    }
}
