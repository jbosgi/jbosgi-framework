package org.jboss.test.osgi.framework.classloader;
/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.io.InputStream;

import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.b.B;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * [JBOSGI-323] DynamicImport-Package takes presendence over embedded classes
 *
 * https://jira.jboss.org/jira/browse/JBOSGI-323
 *
 * @author thomas.diesler@jboss.com
 * @since 04-May-2010
 */
public class DynamicImportLoadOrderTestCase extends OSGiFrameworkTest {

    @Test
    public void testStaticImport() throws Exception {

        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleA");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addImportPackages(A.class);
                return builder.openStream();
            }
        });

        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleB");
        archiveB.addClasses(A.class, B.class);
        archiveB.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages(A.class, B.class);
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
        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleA");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addDynamicImportPackages(A.class.getPackage().getName());
                return builder.openStream();
            }
        });

        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleB");
        archiveB.addClasses(A.class, B.class);
        archiveB.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages(A.class, B.class);
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

        final JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "jbosgi323-bundleB");
        archiveB.addClasses(A.class, B.class);
        archiveB.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveB.getName());
                builder.addExportPackages(A.class, B.class);
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
