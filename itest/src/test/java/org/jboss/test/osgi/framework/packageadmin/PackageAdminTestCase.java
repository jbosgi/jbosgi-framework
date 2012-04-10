/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.test.osgi.framework.packageadmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA2;
import org.jboss.test.osgi.framework.bundle.support.x.ObjectX;
import org.jboss.test.osgi.framework.bundle.support.y.ObjectY;
import org.jboss.test.osgi.framework.packageadmin.exportA.ExportA;
import org.jboss.test.osgi.framework.packageadmin.exportB.ExportB;
import org.jboss.test.osgi.framework.packageadmin.exported.Exported;
import org.jboss.test.osgi.framework.packageadmin.importA.ImportingA;
import org.jboss.test.osgi.framework.packageadmin.importexport.ImportExport;
import org.jboss.test.osgi.framework.packageadmin.optimporter.OptionalImport;
import org.jboss.test.osgi.framework.service.support.a.PA;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;

/**
 * Test PackageAdmin service.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class PackageAdminTestCase extends OSGiFrameworkTest {

    @Test
    public void testGetBundle() throws Exception {
        Archive<?> assemblyA = assembleArchive("smoke-assembled", "/bundles/smoke/smoke-assembled", PA.class);
        Bundle bundleA = installBundle(assemblyA);
        try {
            bundleA.start();
            Class<?> paClass = assertLoadClass(bundleA, PA.class.getName());

            PackageAdmin pa = getPackageAdmin();

            Bundle found = pa.getBundle(paClass);
            assertSame(bundleA, found);

            Bundle notFound = pa.getBundle(getClass());
            assertNull(notFound);
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetBundle2() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
        try {
            bundleA.start();
            bundleB.start();

            Class<?> clA = bundleA.loadClass(Exported.class.getName());
            Class<?> clB = bundleB.loadClass(Exported.class.getName());
            assertNotSame(clA, clB);

            assertEquals(bundleA, pa.getBundle(clA));
            assertEquals(bundleB, pa.getBundle(clB));

            assertNull(pa.getBundle(String.class));
        } finally {
            bundleB.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetBundles() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleC = installBundle(assembleArchive("exporter_3", "/bundles/package-admin/exporter_3", Exported.class));
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
        Bundle bundleR = installBundle(assembleArchive("requiring", "/bundles/package-admin/requiring"));
        try {
            bundleC.start();
            bundleA.start();
            bundleB.start();
            bundleR.start();

            assertNull(pa.getBundles("hello", null));

            Bundle[] rb = pa.getBundles("Requiring", null);
            assertEquals(1, rb.length);
            assertEquals(bundleR, rb[0]);

            Bundle[] eb = pa.getBundles("Exporter", null);
            assertEquals(3, eb.length);
            assertEquals("The first one in the list should have the highest version", bundleC, eb[0]);
            assertEquals(bundleB, eb[1]);
            assertEquals(bundleA, eb[2]);

            Bundle[] eb2 = pa.getBundles("Exporter", "1.0.0");
            assertTrue(Arrays.equals(eb, eb2));
            Bundle[] eb3 = pa.getBundles("Exporter", "[1.0.0, 4.0]");
            assertTrue(Arrays.equals(eb, eb3));
            Bundle[] eb4 = pa.getBundles("Exporter", "[1.0.0, 2.0)");
            assertEquals(1, eb4.length);
            assertEquals(bundleA, eb4[0]);
            Bundle[] eb5 = pa.getBundles("Exporter", "[2.0.0, 2.0.0]");
            assertEquals(1, eb5.length);
            assertEquals(bundleB, eb5[0]);
        } finally {
            bundleR.uninstall();
            bundleC.uninstall();
            bundleB.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetBundleType() throws Exception {
        PackageAdmin pa = getPackageAdmin();

        assertEquals(0, pa.getBundleType(getSystemContext().getBundle(0)));

        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        try {
            assertEquals(0, pa.getBundleType(bundleA));
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetExportedPackagesByBundle() throws Exception {
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleB = installBundle(assembleArchive("opt-importer", "/bundles/package-admin/opt-importer", OptionalImport.class));

        bundleA.start();
        bundleB.start();

        try {
            PackageAdmin pa = getPackageAdmin();
            ExportedPackage[] pkgsA = pa.getExportedPackages(bundleA);
            assertEquals(1, pkgsA.length);
            ExportedPackage pkgA = pkgsA[0];
            assertSame(bundleA, pkgA.getExportingBundle());
            assertEquals(Exported.class.getPackage().getName(), pkgA.getName());
            assertEquals(1, pkgA.getImportingBundles().length);
            assertSame(bundleB, pkgA.getImportingBundles()[0]);
            assertEquals(Version.parseVersion("1.7"), pkgA.getVersion());

            assertNull(pa.getExportedPackages(bundleB));

            Bundle bundleC = installBundle(assembleArchive("import-export", "/bundles/package-admin/import-export", ImportExport.class));
            try {
                bundleC.start();
                ExportedPackage[] allPkgs = pa.getExportedPackages((Bundle) null);
                List<String> pall = new ArrayList<String>();
                for (ExportedPackage ep : allPkgs) {
                    pall.add(ep.getName());
                }
                assertTrue(pall.contains(Exported.class.getPackage().getName()));
                assertTrue(pall.contains(ImportExport.class.getPackage().getName()));
                assertTrue(pall.contains("org.osgi.framework"));
            } finally {
                bundleC.uninstall();
            }
        } finally {
            bundleB.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetExportedPackagesSystemBundle() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        ExportedPackage[] sysPkgs = pa.getExportedPackages(getSystemContext().getBundle(0));
        List<String> pall = new ArrayList<String>();
        for (ExportedPackage ep : sysPkgs) {
            pall.add(ep.getName());
        }
        assertTrue(pall.contains("org.osgi.framework"));
    }

    @Test
    public void testGetExportedPackagesUninstalled() throws Exception {
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleB = installBundle(assembleArchive("import-export", "/bundles/package-admin/import-export", ImportExport.class));

        bundleA.start();
        bundleB.start();

        try {
            PackageAdmin pa = getPackageAdmin();
            ExportedPackage[] pkgsA = pa.getExportedPackages(bundleA);
            assertEquals(1, pkgsA.length);
            ExportedPackage pkgA = pkgsA[0];
            assertSame(bundleA, pkgA.getExportingBundle());
            assertEquals(Exported.class.getPackage().getName(), pkgA.getName());
            assertEquals(1, pkgA.getImportingBundles().length);
            assertSame(bundleB, pkgA.getImportingBundles()[0]);
            assertEquals(Version.parseVersion("1.7"), pkgA.getVersion());

            bundleA.stop();
            bundleA.uninstall();
            pkgA = pa.getExportedPackage(Exported.class.getPackage().getName());
            assertSame(bundleA, pkgA.getExportingBundle());
            assertEquals(Exported.class.getPackage().getName(), pkgA.getName());
            assertNull(pkgA.getImportingBundles());
        } finally {
            bundleB.uninstall();
        }
    }

    @Test
    public void testGetExportedPackage() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        try {
            bundleA.start();

            ExportedPackage ep = pa.getExportedPackage(Exported.class.getPackage().getName());
            assertSame(bundleA, ep.getExportingBundle());
            assertEquals(Version.parseVersion("1.7"), ep.getVersion());

            Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
            try {
                bundleB.start();

                ExportedPackage ep2 = pa.getExportedPackage(Exported.class.getPackage().getName());
                assertSame(bundleB, ep2.getExportingBundle());
                assertEquals(Version.parseVersion("2"), ep2.getVersion());

                assertNull(pa.getExportedPackage("org.foo.bar"));
                assertEquals(getSystemContext().getBundle(0), pa.getExportedPackage("org.osgi.framework").getExportingBundle());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetExportedPackageWired() throws Exception {
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleB = installBundle(assembleArchive("import-export", "/bundles/package-admin/import-export", ImportExport.class));

        bundleA.start();
        bundleB.start();

        try {
            PackageAdmin pa = getPackageAdmin();
            ExportedPackage ep = pa.getExportedPackage(Exported.class.getPackage().getName());
            assertEquals(bundleA, ep.getExportingBundle());
            Bundle[] importingBundles = ep.getImportingBundles();
            assertEquals(1, importingBundles.length);
            assertEquals(bundleB, importingBundles[0]);
        } finally {
            bundleB.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetExportedPackageRequireBundle() throws Exception {
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleR = installBundle(assembleArchive("requiring", "/bundles/package-admin/requiring"));

        bundleA.start();
        bundleR.start();

        try {
            PackageAdmin pa = getPackageAdmin();
            ExportedPackage ep = pa.getExportedPackage(Exported.class.getPackage().getName());
            assertEquals(bundleA, ep.getExportingBundle());
            Bundle[] importingBundles = ep.getImportingBundles();
            assertEquals(1, importingBundles.length);
            assertEquals(bundleR, importingBundles[0]);
        } finally {
            bundleR.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testUpdateExportedPackageRemovalPending() throws Exception {
        JavaArchive archive1 = assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class);
        JavaArchive archive2 = assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class);
        Bundle bundle = installBundle(archive1);
        try {
            bundle.start();
            bundle.update(toInputStream(archive2));

            PackageAdmin pa = getPackageAdmin();
            List<Version> versions = new ArrayList<Version>();
            for (ExportedPackage ep : pa.getExportedPackages(bundle)) {
                Version ver = ep.getVersion();
                versions.add(ver);
                if (ver.equals(Version.parseVersion("1.7"))) {
                    assertTrue(ep.isRemovalPending());
                } else if (ver.equals(Version.parseVersion("2"))) {
                    assertFalse(ep.isRemovalPending());
                } else {
                    fail("Unexpected version: " + ver);
                }
            }
            assertEquals(2, versions.size());
            assertTrue(versions.contains(Version.parseVersion("1.7")));
            assertTrue(versions.contains(Version.parseVersion("2")));
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testUninstallExportedPackageRemovalPending() throws Exception {
        Bundle bundleEx = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleImp = installBundle(assembleArchive("import-export", "/bundles/package-admin/import-export", ImportExport.class));
        try {
            assertNull(getPackageAdmin().getExportedPackage("org.jboss.test.osgi.framework.packageadmin.importexport"));

            bundleEx.start();
            // Adding this importer is important to force packages to stick around after uninstall
            bundleImp.start();

            PackageAdmin pa = getPackageAdmin();
            ExportedPackage[] eps = pa.getExportedPackages(bundleEx);
            assertEquals(1, eps.length);
            assertFalse(eps[0].isRemovalPending());

            bundleEx.uninstall();
            ExportedPackage[] eps2 = pa.getExportedPackages(bundleEx);
            assertEquals(1, eps2.length);
            assertTrue(eps2[0].isRemovalPending());

            assertBundleState(Bundle.ACTIVE, bundleImp.getState());
            ExportedPackage[] epx = pa.getExportedPackages(bundleImp);
            assertEquals(1, epx.length);
            String packageName = epx[0].getName();

            getSystemContext().addFrameworkListener(this);
            pa.refreshPackages(new Bundle[] { bundleEx });
            assertFrameworkEvent(FrameworkEvent.ERROR, bundleImp, BundleException.class);
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            // Packages are refreshed, so the bundle should not export the package any more
            assertNull(pa.getExportedPackages(bundleEx));

            assertBundleState(Bundle.INSTALLED, bundleImp.getState());
            assertNull(pa.getExportedPackage(packageName));
        } finally {
            getSystemContext().removeFrameworkListener(this);
            bundleImp.uninstall();
        }
    }

    @Test
    public void testGetExportedPackageByName() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        try {
            bundleA.start();
            ExportedPackage epA = pa.getExportedPackage(Exported.class.getPackage().getName());
            assertSame(bundleA, epA.getExportingBundle());

            Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
            try {
                bundleB.start();
                ExportedPackage epB = pa.getExportedPackage(Exported.class.getPackage().getName());
                assertSame(bundleB, epB.getExportingBundle());
                assertNotSame(epA, epB);

                ExportedPackage[] eps = pa.getExportedPackages(Exported.class.getPackage().getName());
                assertEquals(2, eps.length);
                List<Version> versions = new ArrayList<Version>();
                for (ExportedPackage ep : eps) {
                    assertEquals(Exported.class.getPackage().getName(), ep.getName());
                    versions.add(ep.getVersion());
                }
                assertTrue(versions.contains(epA.getVersion()));
                assertTrue(versions.contains(epB.getVersion()));

                assertNull(pa.getExportedPackage("org.foo.bar"));
                ExportedPackage[] esp = pa.getExportedPackages("org.osgi.framework");
                assertEquals(1, esp.length);
                assertEquals(getSystemContext().getBundle(0), esp[0].getExportingBundle());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetRequiredBundles() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleB = installBundle(assembleArchive("exporter_2", "/bundles/package-admin/exporter_2", Exported.class));
        Bundle bundleR = installBundle(assembleArchive("requiring", "/bundles/package-admin/requiring"));
        try {
            bundleA.start();
            bundleB.start();
            bundleR.start();

            RequiredBundle[] reqBundles = pa.getRequiredBundles("Exporter");
            assertEquals(2, reqBundles.length);

            Set<String> bsns = new HashSet<String>();
            Set<Bundle> actualBundles = new HashSet<Bundle>();
            for (RequiredBundle rb : reqBundles) {
                bsns.add(rb.getSymbolicName());
                actualBundles.add(rb.getBundle());
            }

            assertEquals(Collections.singleton("Exporter"), bsns);

            bundleB.uninstall();

            Set<Bundle> expectedBundles = new HashSet<Bundle>();
            expectedBundles.add(bundleA);
            expectedBundles.add(bundleB);
            assertEquals(expectedBundles, actualBundles);
            for (RequiredBundle reqBundle : reqBundles) {
                if (reqBundle.getSymbolicName().equals(bundleA.getSymbolicName()) && reqBundle.getVersion().equals(bundleA.getVersion())) {
                    assertEquals(Version.parseVersion("1"), reqBundle.getVersion());
                    assertEquals(reqBundle.getBundle(), bundleA);
                    assertEquals(1, reqBundle.getRequiringBundles().length);
                    assertEquals(bundleR, reqBundle.getRequiringBundles()[0]);
                    assertFalse(reqBundle.isRemovalPending());
                } else if (reqBundle.getSymbolicName().equals(bundleB.getSymbolicName()) && reqBundle.getVersion().equals(bundleB.getVersion())) {
                    assertEquals(Version.parseVersion("2"), reqBundle.getVersion());
                    assertNull(reqBundle.getBundle());
                    assertNull(reqBundle.getRequiringBundles());
                    assertTrue(reqBundle.isRemovalPending());
                } else
                    fail("Unexpected bundle");
            }

            assertNull(pa.getRequiredBundles("foobar"));
        } finally {
            bundleR.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetRequiredBundlesNull() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleA = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        try {
            bundleA.start();
            RequiredBundle[] rqbs = pa.getRequiredBundles(null);
            assertTrue(rqbs.length >= 1);

            boolean exporterFound = false;
            for (RequiredBundle rb : rqbs) {
                if (rb.getSymbolicName().equals("Exporter")) {
                    exporterFound = true;
                    break;
                }
            }

            assertTrue(exporterFound);
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testRefreshPackages() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));

        try {
            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull(getImportedFieldValue(bundleI));

            bundleE.uninstall();
            bundleI.stop();
            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull("The stale bundle E should still be available for classloading so the imported field should have a value", getImportedFieldValue(bundleI));

            getSystemContext().addFrameworkListener(this);
            pa.refreshPackages(new Bundle[] { bundleE });
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertEquals(Bundle.ACTIVE, bundleI.getState());
            assertLoadClassFail(bundleI, Exported.class.getName());
            assertNull("Now that the packages are refreshed, bundle E should be no longer available for classloading", getImportedFieldValue(bundleI));
        } finally {
            getSystemContext().removeFrameworkListener(this);
            bundleI.uninstall();
            if (bundleE.getState() != Bundle.UNINSTALLED)
                bundleE.uninstall();
        }
    }

    @Test
    public void testRefreshPackagesNull() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Archive<?> assemblyx = assembleArchive("bundlex", "/bundles/update/update-bundlex", ObjectX.class);
        Archive<?> assemblyy = assembleArchive("bundley", "/bundles/update/update-bundley", ObjectY.class);
        Archive<?> assembly1 = assembleArchive("bundle1", new String[] { "/bundles/update/update-bundle1", "/bundles/update/classes1" });
        Archive<?> assembly2 = assembleArchive("bundle2", new String[] { "/bundles/update/update-bundle102", "/bundles/update/classes2" });

        Bundle bundleA = installBundle(assembly1);
        Bundle bundleX = installBundle(assemblyx);
        Bundle bundleY = installBundle(assemblyy);
        BundleListener bl = null;
        try {
            BundleContext systemContext = getFramework().getBundleContext();
            int beforeCount = systemContext.getBundles().length;

            bundleA.start();
            bundleX.start();
            bundleY.start();

            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.0"), bundleA.getVersion());
            assertEquals("update-bundle1", bundleA.getSymbolicName());
            assertLoadClass(bundleA, ObjectA.class.getName());
            assertLoadClassFail(bundleA, ObjectA2.class.getName());
            assertLoadClass(bundleX, ObjectA.class.getName());
            assertLoadClassFail(bundleX, ObjectA2.class.getName());

            Class<?> cls = bundleX.loadClass(ObjectX.class.getName());

            bundleA.update(toInputStream(assembly2));
            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.2"), bundleA.getVersion());
            // Should be able to load the new classes via bundleA
            assertLoadClass(bundleA, ObjectA2.class.getName());
            assertLoadClassFail(bundleA, ObjectA.class.getName());
            // Assembly X depends on a package in the old bundle,
            // should be able to reach the old classes through bundle X
            assertLoadClass(bundleX, ObjectA.class.getName());
            assertLoadClassFail(bundleX, ObjectA2.class.getName());
            assertSame(cls, bundleX.loadClass(ObjectX.class.getName()));

            final List<BundleEvent> bundleYEvents = new ArrayList<BundleEvent>();
            bl = new BundleListener() {

                @Override
                public void bundleChanged(BundleEvent event) {
                    if (event.getBundle().getSymbolicName().equals("update-bundley"))
                        bundleYEvents.add(event);
                }
            };
            getSystemContext().addBundleListener(bl);
            getSystemContext().addFrameworkListener(this);

            pa.refreshPackages(null);
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertBundleState(Bundle.ACTIVE, bundleY.getState());
            assertEquals(Version.parseVersion("1.0.2"), bundleA.getVersion());
            assertLoadClass(bundleA, ObjectA2.class.getName());
            assertLoadClassFail(bundleA, ObjectA.class.getName());
            assertLoadClass(bundleX, ObjectA2.class.getName());
            assertLoadClassFail(bundleX, ObjectA.class.getName());

            Class<?> cls2 = bundleX.loadClass(ObjectX.class.getName());
            assertNotSame("Should have loaded a new class", cls, cls2);
            assertEquals("Bundle Y should not have been touched (restarted)", 0, bundleYEvents.size());

            int afterCount = systemContext.getBundles().length;
            assertEquals("Bundle count", beforeCount, afterCount);
        } finally {
            getSystemContext().removeFrameworkListener(this);
            if (bl != null)
                getSystemContext().removeBundleListener(bl);
            bundleY.uninstall();
            bundleX.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testRefreshPackagesNullUninstall() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));

        try {
            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull(getImportedFieldValue(bundleI));

            bundleE.uninstall();
            bundleI.stop();
            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull("The stale bundle E should still be available for classloading so the imported field should have a value", getImportedFieldValue(bundleI));

            getSystemContext().addFrameworkListener(this);
            pa.refreshPackages(null);
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertEquals(Bundle.ACTIVE, bundleI.getState());
            assertLoadClassFail(bundleI, Exported.class.getName());
            assertNull("Now that the packages are refreshed, bundle E should be no longer available for classloading", getImportedFieldValue(bundleI));
        } finally {
            getSystemContext().removeFrameworkListener(this);
            bundleI.uninstall();
            if (bundleE.getState() != Bundle.UNINSTALLED)
                bundleE.uninstall();
        }
    }

    private Object getImportedFieldValue(Bundle bundleI) throws Exception {
        Class<?> iCls = bundleI.loadClass(OptionalImport.class.getName());
        Object importing = iCls.newInstance();
        Field field = iCls.getDeclaredField("imported");
        return field.get(importing);
    }

    @Test
    public void testResolveBundles() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));

        try {
            assertBundleState(Bundle.INSTALLED, bundleE.getState());
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
            assertTrue(pa.resolveBundles(new Bundle[] { bundleE }));
            assertBundleState(Bundle.RESOLVED, bundleE.getState());
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
        } finally {
            bundleI.uninstall();
            bundleE.uninstall();
        }
    }

    @Test
    public void testResolveBundlesNull() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));

        try {
            assertBundleState(Bundle.INSTALLED, bundleE.getState());
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
            assertTrue(pa.resolveBundles(null));
            assertBundleState(Bundle.RESOLVED, bundleE.getState());
            assertBundleState(Bundle.RESOLVED, bundleI.getState());
        } finally {
            bundleI.uninstall();
            bundleE.uninstall();
        }
    }

    @Test
    public void testCantResolveAllBundles() throws Exception {
        PackageAdmin pa = getPackageAdmin();
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));
        Bundle bundleR = installBundle(assembleArchive("requiring", "/bundles/package-admin/requiring"));

        try {
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
            assertBundleState(Bundle.INSTALLED, bundleR.getState());
            assertFalse(pa.resolveBundles(new Bundle[] { bundleR, bundleI }));
            assertTrue(Bundle.RESOLVED == bundleI.getState() || Bundle.INSTALLED == bundleI.getState());
            assertBundleState(Bundle.INSTALLED, bundleR.getState());
        } finally {
            bundleI.uninstall();
            bundleR.uninstall();
        }
    }

    @Test
    public void testComplexUpdateScenario() throws Exception {
        PackageAdmin pa = getPackageAdmin();

        assertNull(pa.getExportedPackage(ExportA.class.getName()));
        assertNull(pa.getExportedPackage(ExportB.class.getName()));

        Bundle bundleE = installBundle(assembleArchive("ExportA", "/bundles/package-admin/exportA", ExportA.class));
        Bundle bundleU = installBundle(assembleArchive("ExportU", "/bundles/package-admin/exporter", Exported.class));

        Archive<?> assemblyE = assembleArchive("ExportA2", "/bundles/package-admin/exportB", ExportB.class);
        try {
            bundleE.start();
            bundleU.start();

            ExportedPackage[] eex = pa.getExportedPackages(bundleE);
            assertEquals(1, eex.length);
            assertEquals(ExportA.class.getPackage().getName(), eex[0].getName());
            assertSame(bundleE, eex[0].getExportingBundle());

            ExportedPackage[] iex = pa.getExportedPackages(bundleU);
            assertEquals(1, iex.length);
            assertEquals(Exported.class.getPackage().getName(), iex[0].getName());
            assertSame(bundleU, iex[0].getExportingBundle());

            bundleE.update(toInputStream(assemblyE));

            // This bundle imports the old version of bundleE, should still be available!
            Bundle bundleI = installBundle(assembleArchive("ImportA", "/bundles/package-admin/importA", ImportingA.class));
            try {
                bundleI.start();

                // PackageAdmin should report both the old and the new package as exported
                List<String> exported = new ArrayList<String>();
                for (ExportedPackage ex : pa.getExportedPackages(bundleE)) {
                    exported.add(ex.getName());
                }
                assertTrue(exported.contains(ExportA.class.getPackage().getName()));
                assertTrue(exported.contains(ExportB.class.getPackage().getName()));
                assertEquals(2, exported.size());

                refreshPackages(new Bundle[] { bundleE, bundleU, bundleI });

                assertEquals(Bundle.ACTIVE, bundleE.getState());
                assertEquals(Bundle.ACTIVE, bundleU.getState());
                assertTrue(Bundle.ACTIVE != bundleI.getState());

                ExportedPackage[] eex2 = pa.getExportedPackages(bundleE);
                assertEquals(1, eex2.length);
                assertEquals(ExportB.class.getPackage().getName(), eex2[0].getName());
                assertSame(bundleE, eex2[0].getExportingBundle());
            } finally {
                bundleI.uninstall();
            }
        } finally {
            bundleU.uninstall();
            bundleE.uninstall();
        }
    }
}
