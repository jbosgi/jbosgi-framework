/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.c.CA;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * Test optional imports
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Aug-2011
 */
public class OptionalImportTestCase extends OSGiFrameworkTest {

    static final String BUNDLE_A = "optional-import-a";
    static final String BUNDLE_B = "optional-import-b";

    @Test
    public void testUnresolvedOptionalImport() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            Assert.assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundleA.getState());
            bundleA.start();
            Assert.assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundleA.getState());
            try {
                bundleA.loadClass("org.jboss.test.osgi.framework.classloader.support.c.CA").newInstance();
                Assert.fail("NoClassDefFoundError expected");
            } catch (NoClassDefFoundError ex) {
                // expected
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testResolvedOptionalImport() throws Exception {
        Bundle bundleB = installBundle(getBundleB());
        try {
            Assert.assertEquals("Bundle INSTALLED", Bundle.INSTALLED, bundleB.getState());
            Bundle bundleA = installBundle(getBundleA());
            try {
                Assert.assertEquals(Bundle.INSTALLED, bundleA.getState());
                bundleA.start();
                Assert.assertEquals("Bundle ACTIVE", Bundle.ACTIVE, bundleA.getState());
                Assert.assertEquals("Bundle RESOLVED", Bundle.RESOLVED, bundleB.getState());
                bundleA.loadClass("org.jboss.test.osgi.framework.classloader.support.c.CA");
            } finally {
                bundleA.uninstall();
            }
        } finally {
            bundleB.uninstall();
        }
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_A);
        archive.addClasses(CA.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(A.class.getPackage().getName() + ";resolution:=optional");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_B);
        archive.addClasses(A.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addExportPackages(A.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
