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
package org.jboss.test.osgi.framework.bundle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * The bundle URLs
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Jan-2011
 */
public class BundleURLTestCase extends OSGiFrameworkTest {

    @Test
    public void testGetEntry() throws Exception {
        Archive<?> assembly = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
        Bundle bundle = installBundle(assembly);
        try {
            URL url = bundle.getEntry("/resource-one.txt");
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            
            assertNotNull("Entry found", url);
            assertEquals("/resource-one.txt", url.getPath());

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());
            
            // Test URL reconstruction
            url = new URL(url.toExternalForm());
            br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetEntryPaths() throws Exception {
    }

    @Test
    public void testFindEntries() throws Exception {
    }

    @Test
    public void testGetResource() throws Exception {
    }

    @Test
    public void testGetResources() throws Exception {
    }
}
