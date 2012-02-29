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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleService;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.Constants;

/**
 * The bundle URLs
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Jan-2011
 */
public class BundleURLTestCase extends OSGiFrameworkTest {

    @Test
    public void testGetEntry() throws Exception {
        Bundle bundle = installBundle(getBundleA());
        try {
            URL url = bundle.getEntry("/META-INF/resource-one.txt");
            assertEquals("/META-INF/resource-one.txt", url.getPath());

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());
            
            // Test URL reconstruction
            url = new URL(url.toExternalForm());
            br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());
            
            // Entry access should not resolve the bundle
            assertBundleState(Bundle.INSTALLED, bundle.getState());
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testEntryNotExist() throws Exception {
        Bundle bundle = installBundle(getBundleA());
        try {
            URL baseurl = bundle.getEntry("/META-INF");
            assertEquals("/META-INF/", baseurl.getPath());

            URL url = baseurl.toURI().resolve("does-not-exist").toURL();
            assertEquals("/META-INF/does-not-exist", url.getPath());

            try {
                url.openStream();
                fail("IOException expected");
            } catch (IOException ex) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetEntryPaths() throws Exception {
        Bundle bundle = installBundle(getBundleA());
        try {
            Enumeration urls = bundle.getEntryPaths("/");
            assertEquals("org/", urls.nextElement());
            assertEquals("META-INF/", urls.nextElement());
            assertFalse(urls.hasMoreElements());

            // Entry access should not resolve the bundle
            assertBundleState(Bundle.INSTALLED, bundle.getState());
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testFindEntries() throws Exception {
        Bundle bundle = installBundle(getBundleA());
        try {
            Enumeration urls = bundle.findEntries("/", "*.txt", true);
            URL entry = (URL) urls.nextElement();
            assertTrue(entry.toExternalForm(), entry.getPath().endsWith("META-INF/resource-one.txt"));
            assertFalse(urls.hasMoreElements());

            urls = bundle.findEntries("/", "*.class", true);
            entry = (URL) urls.nextElement();
            String suffix = SimpleService.class.getName().replace('.', '/') + ".class";
            assertTrue(entry.toExternalForm(), entry.getPath().endsWith(suffix));
            assertFalse(urls.hasMoreElements());

            assertBundleState(Bundle.RESOLVED, bundle.getState());
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetResource() throws Exception {
        Bundle bundle = installBundle(getBundleA());
        try {
            URL url = bundle.getResource("/META-INF/resource-one.txt");
            assertEquals("/META-INF/resource-one.txt", url.getPath());

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());

            assertBundleState(Bundle.RESOLVED, bundle.getState());
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetResources() throws Exception {
        Bundle bundle = installBundle(getBundleB());
        try {
            Enumeration urls = bundle.getResources("/META-INF/resource-one.txt");
            URL url = (URL) urls.nextElement();
            assertEquals("/META-INF/resource-one.txt", url.getPath());

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());

            url = (URL) urls.nextElement();
            assertEquals("/META-INF/resource-one.txt", url.getPath());
            assertFalse(urls.hasMoreElements());

            br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());

            assertBundleState(Bundle.RESOLVED, bundle.getState());
        } finally {
            bundle.uninstall();
        }
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA.jar");
        archive.addClasses(SimpleService.class);
        archive.addAsManifestResource("bundles/simple/simple-bundle1/resource-one.txt", "resource-one.txt");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleB.jar");
        archive.add(getBundleA(), "x", ZipExporter.class);
        archive.add(getBundleA(), "y", ZipExporter.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addManifestHeader(Constants.BUNDLE_CLASSPATH, "x/bundleA.jar,y/bundleA.jar");
                return builder.openStream();
            }
        });
        return archive;
    }
}
