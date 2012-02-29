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
package org.jboss.test.osgi.framework.classloader;

import java.io.InputStream;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.a.NonExistingResourceLoadingActivator;
import org.jboss.test.osgi.framework.classloader.support.b.B;
import org.jboss.test.osgi.framework.classloader.support.c.C;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;

/**
 * Test the DynamicImport-Package manifest header.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 26-Mar-2010
 */
public class DynamicImportPackageTestCase extends OSGiFrameworkTest {
    @Test
    public void testAllPackagesWildcard() throws Exception {

        // Bundle-SymbolicName: dynamic-wildcard-a
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a
        // Import-Package: org.jboss.test.osgi.framework.classloader.support.b
        // DynamicImport-Package: *
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-a");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addExportPackages(A.class.getPackage().getName());
                builder.addImportPackages(B.class.getPackage().getName());
                builder.addDynamicImportPackages("*");
                return builder.openStream();
            }
        });

        // Bundle-SymbolicName: dynamic-wildcard-bc
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.b,
        // org.jboss.test.osgi.container.classloader.support.c
        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-bc");
        archiveB.addClasses(B.class, C.class);
        archiveB.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages(B.class.getPackage().getName());
                builder.addExportPackages(C.class.getPackage().getName());
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());
        try {
            Bundle bundleB = installBundle(archiveB);
            assertBundleState(Bundle.INSTALLED, bundleB.getState());
            try {
                assertLoadClass(bundleA, A.class.getName(), bundleA);
                assertLoadClass(bundleA, B.class.getName(), bundleB);
                assertLoadClass(bundleA, C.class.getName(), bundleB);

                assertBundleState(Bundle.RESOLVED, bundleA.getState());
                assertBundleState(Bundle.RESOLVED, bundleB.getState());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testAllPackagesWildcardNotWired() throws Exception {

        // Bundle-SymbolicName: dynamic-wildcard-a
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a
        // DynamicImport-Package: *
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-a");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addExportPackages(A.class.getPackage().getName());
                builder.addDynamicImportPackages("*");
                return builder.openStream();
            }
        });

        // Bundle-SymbolicName: dynamic-wildcard-c
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.c
        final JavaArchive archiveC = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-c");
        archiveC.addClasses(C.class);
        archiveC.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveC.getName());
                builder.addExportPackages(C.class.getPackage().getName());
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());
        try {
            Bundle bundleC = installBundle(archiveC);
            assertBundleState(Bundle.INSTALLED, bundleC.getState());
            try {
                assertLoadClass(bundleA, A.class.getName(), bundleA);
                assertLoadClass(bundleA, C.class.getName(), bundleC);

                assertBundleState(Bundle.RESOLVED, bundleA.getState());
                assertBundleState(Bundle.RESOLVED, bundleC.getState());
            } finally {
                bundleC.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testAllPackagesWildcardNotThere() throws Exception {

        // Bundle-SymbolicName: dynamic-wildcard-a
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a
        // DynamicImport-Package: *
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-a");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addExportPackages(A.class.getPackage().getName());
                builder.addDynamicImportPackages("*");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());
        try {
            assertLoadClass(bundleA, A.class.getName(), bundleA);

            assertLoadClassFail(bundleA, C.class.getName());

            assertBundleState(Bundle.RESOLVED, bundleA.getState());
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testPackageWildcardWired() throws Exception {

        // Bundle-SymbolicName: dynamic-wildcard-a
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a
        // Import-Package: org.jboss.test.osgi.framework.classloader.support.b
        // DynamicImport-Package: org.jboss.test.osgi.framework.classloader.*
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-a");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addExportPackages(A.class.getPackage().getName());
                builder.addImportPackages(B.class.getPackage().getName());
                builder.addDynamicImportPackages("org.jboss.test.osgi.framework.classloader.*");
                return builder.openStream();
            }
        });

        // Bundle-SymbolicName: dynamic-wildcard-bc
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.b,
        // org.jboss.test.osgi.container.classloader.support.c
        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-bc");
        archiveB.addClasses(B.class, C.class);
        archiveB.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages(B.class.getPackage().getName());
                builder.addExportPackages(C.class.getPackage().getName());
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());
        try {
            Bundle bundleB = installBundle(archiveB);
            assertBundleState(Bundle.INSTALLED, bundleB.getState());
            try {
                assertLoadClass(bundleA, A.class.getName(), bundleA);
                assertLoadClass(bundleA, B.class.getName(), bundleB);
                assertLoadClass(bundleA, C.class.getName(), bundleB);

                assertBundleState(Bundle.RESOLVED, bundleA.getState());
                assertBundleState(Bundle.RESOLVED, bundleB.getState());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testPackageWildcardNotWired() throws Exception {

        // Bundle-SymbolicName: dynamic-wildcard-a
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a
        // DynamicImport-Package: org.jboss.test.osgi.framework.classloader.*
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-a");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addExportPackages(A.class.getPackage().getName());
                builder.addDynamicImportPackages("org.jboss.test.osgi.framework.classloader.*");
                return builder.openStream();
            }
        });

        // Bundle-SymbolicName: dynamic-wildcard-c
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.c
        final JavaArchive archiveC = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-c");
        archiveC.addClasses(C.class);
        archiveC.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveC.getName());
                builder.addExportPackages(C.class.getPackage().getName());
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());
        try {
            Bundle bundleC = installBundle(archiveC);
            assertBundleState(Bundle.INSTALLED, bundleC.getState());
            try {
                assertLoadClass(bundleA, A.class.getName(), bundleA);
                assertLoadClass(bundleA, C.class.getName(), bundleC);

                assertBundleState(Bundle.RESOLVED, bundleA.getState());
                assertBundleState(Bundle.RESOLVED, bundleC.getState());
            } finally {
                bundleC.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testPackageWildcardNotThere() throws Exception {

        // Bundle-SymbolicName: dynamic-wildcard-a
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a
        // DynamicImport-Package: org.jboss.test.osgi.framework.classloader.*
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "dynamic-wildcard-a");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addExportPackages(A.class.getPackage().getName());
                builder.addDynamicImportPackages("*");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());
        try {
            assertLoadClass(bundleA, A.class.getName(), bundleA);

            assertLoadClassFail(bundleA, C.class.getName());

            assertBundleState(Bundle.RESOLVED, bundleA.getState());
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testBundleSymbolicNameDirective() throws Exception {

        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "tb8a");
        archiveA.addClasses(A.class);
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addExportPackages(A.class);
                return builder.openStream();
            }
        });

        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "tb8b");
        archiveB.addClasses(A.class);
        archiveB.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages(A.class);
                return builder.openStream();
            }
        });

        final JavaArchive archiveC = ShrinkWrap.create(JavaArchive.class, "tb17c");
        archiveC.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveC.getName());
                String packageA = A.class.getPackage().getName();
                builder.addDynamicImportPackages(packageA + ";bundle-symbolic-name=tb8b," + packageA + ";bundle-symbolic-name=tb8a");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertLoadClass(bundleA, A.class.getName(), bundleA);

        Bundle bundleB = installBundle(archiveB);
        assertLoadClass(bundleB, A.class.getName(), bundleB);

        Bundle bundleC = installBundle(archiveC);
        assertLoadClass(bundleC, A.class.getName(), bundleB);

        bundleA.uninstall();
        bundleB.uninstall();
        bundleC.uninstall();

        // Reverse the order of installed bundles
        bundleB = installBundle(archiveB);
        assertLoadClass(bundleB, A.class.getName(), bundleB);

        bundleA = installBundle(archiveA);
        assertLoadClass(bundleA, A.class.getName(), bundleA);

        bundleC = installBundle(archiveC);
        assertLoadClass(bundleC, A.class.getName(), bundleB);

        bundleA.uninstall();
        bundleB.uninstall();
        bundleC.uninstall();
    }

    @Test
    public void testResourceLookupNoPath() throws Exception {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "resource-no-path");
        archive.addClass(NonExistingResourceLoadingActivator.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(NonExistingResourceLoadingActivator.class);
                builder.addImportPackages(BundleActivator.class);
                builder.addDynamicImportPackages("*");
                return builder.openStream();
            }
        });

        Bundle bundle = installBundle(archive);
        try {
            assertBundleState(Bundle.INSTALLED, bundle.getState());

            bundle.start();
            // The activator performs a resource lookup
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            bundle.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundle.getState());
        }
    }
}
