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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA;
import org.jboss.test.osgi.framework.service.support.a.A;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleReference;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;

import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.jar.Attributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.osgi.framework.Bundle.INSTALLED;
import static org.osgi.framework.Bundle.RESOLVED;

/**
 * BundleTest.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class BundleTestCase extends OSGiFrameworkTest {

    @Test
    public void testBundleId() throws Exception {
        long id1 = -1;
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            id1 = bundle.getBundleId();
        } finally {
            bundle.uninstall();
        }
        assertEquals(id1, bundle.getBundleId());

        long id2 = -1;
        bundle = installBundle(getBundleArchiveA());
        try {
            id2 = bundle.getBundleId();
        } finally {
            bundle.uninstall();
        }
        assertEquals(id2, bundle.getBundleId());
        assertTrue("Ids should be different" + id1 + "," + id2, id1 != id2);
    }

    @Test
    public void testSymbolicName() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            assertEquals("simple1", bundle.getSymbolicName());
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testState() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            assertEquals(Bundle.INSTALLED, bundle.getState());

            bundle.start();
            assertEquals(Bundle.ACTIVE, bundle.getState());

            bundle.stop();
            assertEquals(Bundle.RESOLVED, bundle.getState());
        } finally {
            bundle.uninstall();
        }
        assertEquals(Bundle.UNINSTALLED, bundle.getState());
    }

    @Test
    public void testGetBundleContext() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            BundleContext bundleContext = bundle.getBundleContext();
            assertNull(bundleContext);

            bundle.start();
            bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            bundle.stop();
            bundleContext = bundle.getBundleContext();
            assertNull(bundleContext);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testLoadClass() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveB());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        Class<?> clazz = bundle.loadClass(ObjectA.class.getName());
        assertBundleState(Bundle.RESOLVED, bundle.getState());
        assertEquals(ObjectA.class.getName(), clazz.getName());
        assertEquals(bundle, ((BundleReference) clazz.getClassLoader()).getBundle());

        bundle.start();
        assertBundleState(Bundle.ACTIVE, bundle.getState());

        bundle.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundle.getState());

        try {
            bundle.loadClass(ObjectA.class.getName());
            fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
            // If this bundle's state is UNINSTALLED then an IllegalStateException is thrown
        }
    }

    @Test
    public void testUninstall() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveB());

        bundle.start();
        assertBundleState(Bundle.ACTIVE, bundle.getState());

        bundle.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundle.getState());

        try {
            bundle.uninstall();
            fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
            // If this bundle's state is UNINSTALLED then an IllegalStateException is thrown
        }
    }

    @Test
    public void testInstallAfterUninstall() throws Exception {
        Bundle bundleA = installBundle(getBundleArchiveB());
        long idA = bundleA.getBundleId();

        bundleA.start();
        assertBundleState(Bundle.ACTIVE, bundleA.getState());

        bundleA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleA.getState());

        Bundle bundleB = installBundle(getBundleArchiveB());
        long idB = bundleB.getBundleId();
        assertTrue("Bundle id incremented", idB > idA);
        assertFalse("Bundles not equal", bundleA.equals(bundleB));

        bundleB.start();
        assertBundleState(Bundle.ACTIVE, bundleB.getState());

        bundleB.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleB.getState());
    }

    @Test
    public void testSingleton() throws Exception {
        Archive<?> assemblyA = assembleArchive("bundle10", "/bundles/singleton/singleton1");
        Bundle bundleA = installBundle(assemblyA);
        try {
            Archive<?> assemblyB = assembleArchive("bundle20", "/bundles/singleton/singleton2");
            Bundle bundleB = installBundle(assemblyB);
            try {
                boolean resolved = getPackageAdmin().resolveBundles(new Bundle[]{bundleA, bundleB});
                assertFalse("Not all Bundles resolved", resolved);
                int stateA = bundleA.getState();
                int stateB = bundleB.getState();
                assertTrue("One Bundle resolved", stateA == INSTALLED && stateB == RESOLVED || stateA == RESOLVED && stateB == INSTALLED);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testNotSingleton() throws Exception {
        // Bundle-SymbolicName: singleton;singleton:=true
        Archive<?> assemblyA = assembleArchive("bundle1", "/bundles/singleton/singleton1");
        Bundle bundleA = installBundle(assemblyA);
        try {
            // Bundle-SymbolicName: singleton
            // Bundle-Version: 2.0.0
            Archive<?> assemblyB = assembleArchive("not-singleton", "/bundles/singleton/not-singleton");
            Bundle bundleB = installBundle(assemblyB);
            try {
                assertEquals(bundleA.getSymbolicName(), bundleB.getSymbolicName());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetHeaders() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            Dictionary expected = new Hashtable();
            expected.put(Constants.BUNDLE_SYMBOLICNAME, "simple1");
            expected.put(Constants.BUNDLE_MANIFESTVERSION, "2");
            expected.put(Constants.IMPORT_PACKAGE, "org.osgi.framework");
            expected.put(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
            Dictionary dictionary = bundle.getHeaders();
            assertEquals(expected, dictionary);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testInvalidExportPackageHeader() throws Exception {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(A.class.getPackage().getName() + ";version=foo");
                return builder.openStream();
            }
        });
        try {
            installBundle(archive);
            fail("BundleException expected");
        } catch (BundleException ex) {
            String message = ex.getMessage();
            assertTrue("Contains Export-Package: " + message, message.contains("Export-Package"));
            assertTrue("Contains version: " + message, message.contains("version"));
            assertTrue("Contains foo: " + message, message.contains("foo"));
        }
    }

    @Test
    public void testInvalidBundleManifestVersion() throws Exception {
        final JavaArchive archive1 = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive1.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive1.getName());
                return builder.openStream();
            }
        });
        try {
            installBundle(archive1);
            fail("BundleException expected");
        } catch (BundleException ex) {
            String message = ex.getMessage();
            Assert.assertEquals("JBOSGI010321: Invalid Bundle-ManifestVersion for: simple1", message);
        }

        final JavaArchive archive2 = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive2.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(3);
                builder.addBundleSymbolicName(archive2.getName());
                return builder.openStream();
            }
        });
        try {
            installBundle(archive2);
            fail("BundleException expected");
        } catch (BundleException ex) {
            String message = ex.getMessage();
            Assert.assertEquals("JBOSGI010320: Unsupported Bundle-ManifestVersion: 3", message);
        }
    }

    @Test
    public void testLegacyBundleManifestVersion() throws Exception {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleName(archive.getName());
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        Bundle bundle = installBundle(archive);
        try {
            Dictionary expected = new Hashtable();
            expected.put(Constants.BUNDLE_NAME, "simple1");
            expected.put(Constants.IMPORT_PACKAGE, "org.osgi.framework");
            expected.put(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
            Dictionary dictionary = bundle.getHeaders();
            assertEquals(expected, dictionary);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testBundleReference() throws Exception {
        Archive<?> assembly = assembleArchive("bundle1", "/bundles/update/update-bundle1", ObjectA.class);
        Bundle bundle = installBundle(assembly);
        try {
            Class<?> clazz = bundle.loadClass(ObjectA.class.getName());
            ClassLoader classLoader = clazz.getClassLoader();
            assertTrue("Instance of BundleReference", classLoader instanceof BundleReference);
            Bundle result = FrameworkUtil.getBundle(clazz);
            assertEquals(bundle, result);
        } finally {
            bundle.uninstall();
        }
    }

    private JavaArchive getBundleArchiveA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleArchiveB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive.addClasses(ObjectA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
