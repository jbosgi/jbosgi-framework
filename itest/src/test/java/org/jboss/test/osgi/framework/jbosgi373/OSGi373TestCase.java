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
package org.jboss.test.osgi.framework.jbosgi373;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.framework.jbosgi373.bundleA.OSGi373ServiceImpl;
import org.jboss.test.osgi.framework.jbosgi373.bundleB.ObjectB;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * [JBOSGI-373] Cannot load service through java.util.ServiceLoader
 * 
 * https://jira.jboss.org/jira/browse/JBOSGI-373
 * 
 * @author thomas.diesler@jboss.com
 * @since 31-Jul-2010
 */
public class OSGi373TestCase extends OSGiFrameworkTest {

    @Test
    public void testServiceLocal() throws Exception {
        // Bundle-SymbolicName: osgi373.bundleA
        // Export-Package: org.jboss.test.osgi.framework.jbosgi373
        Archive<?> archiveA = assembleArchive("bundleA", "/osgi373/bundleA", OSGi373Service.class, OSGi373ServiceImpl.class);
        Bundle bundleA = installBundle(archiveA);
        try {
            assertBundleState(Bundle.INSTALLED, bundleA.getState());
            assertLoadClass(bundleA, OSGi373Service.class.getName());
            assertLoadClass(bundleA, OSGi373ServiceImpl.class.getName());

            try {
                ServiceLoader<OSGi373Service> loader = ServiceLoader.load(OSGi373Service.class);
                loader.iterator().next();
                fail("NoSuchElementException expected");
            } catch (NoSuchElementException ex) {
                // expected
            }

            Class<?> serviceClass = bundleA.loadClass(OSGi373Service.class.getName());
            ServiceLoader<?> loader = ServiceLoader.load(serviceClass, serviceClass.getClassLoader());
            Object service = loader.iterator().next();
            assertNotNull("Service not null", service);
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testServiceDependent() throws Exception {
        // Bundle-SymbolicName: osgi373.bundleA
        // Export-Package: org.jboss.test.osgi.framework.jbosgi373
        Archive<?> archiveA = assembleArchive("bundleA", "/osgi373/bundleA", OSGi373Service.class, OSGi373ServiceImpl.class);
        Bundle bundleA = installBundle(archiveA);
        try {
            // Bundle-SymbolicName: osgi373.bundleB
            // Import-Package: org.jboss.test.osgi.framework.jbosgi373
            Archive<?> archiveB = assembleArchive("bundleB", "/osgi373/bundleB", ObjectB.class);
            Bundle bundleB = installBundle(archiveB);
            try {
                assertBundleState(Bundle.INSTALLED, bundleA.getState());
                assertBundleState(Bundle.INSTALLED, bundleB.getState());
                assertLoadClass(bundleB, OSGi373Service.class.getName());
                assertLoadClassFail(bundleB, OSGi373ServiceImpl.class.getName());

                Class<?> serviceClass = bundleB.loadClass(OSGi373Service.class.getName());
                ServiceLoader<?> loader = ServiceLoader.load(serviceClass, serviceClass.getClassLoader());
                Object service = loader.iterator().next();
                assertNotNull("Service not null", service);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testServiceResources() throws Exception {
        Archive<?> archiveA = assembleArchive("bundleA", "/osgi373/bundleA", OSGi373Service.class, OSGi373ServiceImpl.class);
        Bundle bundleA = installBundle(archiveA);
        try {
            Archive<?> archiveB = assembleArchive("bundleB", "/osgi373/bundleB", ObjectB.class);
            Bundle bundleB = installBundle(archiveB);
            try {
                assertBundleState(Bundle.INSTALLED, bundleA.getState());
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                String serviceId = "META-INF/services/" + OSGi373Service.class.getName();

                ClassLoader loaderA = bundleA.loadClass(OSGi373ServiceImpl.class.getName()).getClassLoader();
                assertResourceURL(loaderA.getResource(serviceId));
                assertResourceURL(bundleA.getResource(serviceId));
                assertTrue("Enumeration has elements", loaderA.getResources(serviceId).hasMoreElements());
                assertTrue("Enumeration has elements", bundleA.getResources(serviceId).hasMoreElements());

                ClassLoader loaderB = bundleB.loadClass(ObjectB.class.getName()).getClassLoader();
                assertNull("Resource URL null", loaderB.getResource(serviceId));
                assertNull("Resource URL null", bundleB.getResource(serviceId));
                assertFalse("Enumeration is empty", loaderB.getResources(serviceId).hasMoreElements());
                assertNull("Enumeration null", bundleB.getResources(serviceId));
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    private void assertResourceURL(URL resourceURL) throws IOException {
        assertNotNull("Resource URL not null", resourceURL);
        InputStream instream = resourceURL.openStream();
        try {
            assertNotNull("InputStream not null", instream);
            String line = new BufferedReader(new InputStreamReader(instream)).readLine();
            assertEquals(OSGi373ServiceImpl.class.getName(), line);
        } finally {
            instream.close();
        }
    }
}
