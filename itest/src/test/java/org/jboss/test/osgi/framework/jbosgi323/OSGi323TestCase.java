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
package org.jboss.test.osgi.framework.jbosgi323;

// 

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.b.B;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.io.InputStream;

/**
 * [JBOSGI-323] DynamicImport-Package takes presendence over embedded classes
 * 
 * https://jira.jboss.org/jira/browse/JBOSGI-323
 * 
 * @author thomas.diesler@jboss.com
 * @since 04-May-2010
 */
public class OSGi323TestCase extends OSGiFrameworkTest {

    @Test
    public void testStaticImport() throws Exception {
        // Bundle-SymbolicName: jbosgi323-bundleA
        // Import-Package: org.jboss.test.osgi.framework.classloader.support.a
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleA");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addImportPackages("org.jboss.test.osgi.framework.classloader.support.a");
                return builder.openStream();
            }
        });

        // Bundle-SymbolicName: jbosgi323-bundleB
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a,
        // org.jboss.test.osgi.container.classloader.support.b
        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleB");
        archiveB.addClasses(A.class, B.class);
        archiveB.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages("org.jboss.test.osgi.framework.classloader.support.a");
                builder.addExportPackages("org.jboss.test.osgi.framework.classloader.support.b");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        Bundle bundleB = installBundle(archiveB);
        try {
            assertLoadClass(bundleA, A.class.getName(), bundleB);
            assertLoadClassFail(bundleA, B.class.getName());

            assertLoadClass(bundleB, A.class.getName(), bundleB);
            assertLoadClass(bundleB, B.class.getName(), bundleB);

            assertBundleState(Bundle.RESOLVED, bundleA.getState());
            assertBundleState(Bundle.RESOLVED, bundleB.getState());
        } finally {
            bundleA.uninstall();
            bundleB.uninstall();
        }
    }

    @Test
    public void testDynamicImportWithPackage() throws Exception {
        // Bundle-SymbolicName: jbosgi323-bundleA
        // DynamicImport-Package: org.jboss.test.osgi.framework.classloader.support.a
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleA");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addDynamicImportPackages("org.jboss.test.osgi.framework.classloader.support.a");
                return builder.openStream();
            }
        });

        // Bundle-SymbolicName: jbosgi323-bundleB
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a,
        // org.jboss.test.osgi.container.classloader.support.b
        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleB");
        archiveB.addClasses(A.class, B.class);
        archiveB.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages("org.jboss.test.osgi.framework.classloader.support.a");
                builder.addExportPackages("org.jboss.test.osgi.framework.classloader.support.b");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        Bundle bundleB = installBundle(archiveB);
        try {
            assertLoadClass(bundleA, A.class.getName(), bundleA);
            assertLoadClassFail(bundleA, B.class.getName());

            assertLoadClass(bundleB, A.class.getName(), bundleB);
            assertLoadClass(bundleB, B.class.getName(), bundleB);

            assertBundleState(Bundle.RESOLVED, bundleA.getState());
            assertBundleState(Bundle.RESOLVED, bundleB.getState());
        } finally {
            bundleA.uninstall();
            bundleB.uninstall();
        }
    }

    @Test
    public void testDynamicImportWithWildcard() throws Exception {
        // Bundle-SymbolicName: jbosgi323-bundleA
        // DynamicImport-Package: *
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleA");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addDynamicImportPackages("*");
                return builder.openStream();
            }
        });

        // Bundle-SymbolicName: jbosgi323-bundleB
        // Export-Package: org.jboss.test.osgi.framework.classloader.support.a,
        // org.jboss.test.osgi.container.classloader.support.b
        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleB");
        archiveB.addClasses(A.class, B.class);
        archiveB.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages("org.jboss.test.osgi.framework.classloader.support.a");
                builder.addExportPackages("org.jboss.test.osgi.framework.classloader.support.b");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        Bundle bundleB = installBundle(archiveB);
        try {
            assertLoadClass(bundleA, A.class.getName(), bundleA);
            assertLoadClass(bundleA, B.class.getName(), bundleB);

            assertLoadClass(bundleB, A.class.getName(), bundleB);
            assertLoadClass(bundleB, B.class.getName(), bundleB);

            assertBundleState(Bundle.RESOLVED, bundleA.getState());
            assertBundleState(Bundle.RESOLVED, bundleB.getState());
        } finally {
            bundleA.uninstall();
            bundleB.uninstall();
        }
    }
}
