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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.b.B;
import org.jboss.test.osgi.framework.classloader.support.c.C;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.io.InputStream;

import static org.junit.Assert.fail;

/**
 * RequireBundleTest.
 * 
 * TODO test security
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author thomas.diesler@jboss.com
 */
public class RequireBundleTestCase extends OSGiFrameworkTest {

    @Test
    public void testSimpleRequireBundle() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            assertLoadClass(bundleA, C.class.getName());

            Bundle bundleB = installBundle(getRequireBundleA());
            try {
                bundleB.start();
                assertLoadClass(bundleB, B.class.getName(), bundleB);
                assertLoadClass(bundleB, A.class.getName(), bundleA);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testSimpleRequireBundleFails() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            
            // Bundle-SymbolicName: classloader.bundleB
            // Require-Bundle: doesnotexist
            Archive<?> assemblyB = assembleArchive("simplerequirebundlefails", "/bundles/classloader/simplerequirebundlefails", B.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                fail("Should not be here!");
            } catch (BundleException ex) {
                // expected
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testVersionRequireBundle() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            
            // Bundle-SymbolicName: classloader.bundleB
            // Require-Bundle: classloader.bundleA;bundle-version="[0.0.0,1.0.0]"
            Archive<?> assemblyB = assembleArchive("versionrequirebundleA", "/bundles/classloader/versionrequirebundleA", B.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                assertLoadClass(bundleB, A.class.getName(), bundleA);
                assertLoadClass(bundleB, B.class.getName(), bundleB);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testVersionRequireBundleFails() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            
            // Bundle-SymbolicName: classloader.bundleB
            // Require-Bundle: classloader.bundleA;bundle-version="[0.0.0,1.0.0)"
            Archive<?> assemblyB = assembleArchive("versionrequirebundlefails", "/bundles/classloader/versionrequirebundlefails", B.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                fail("Should not be here!");
            } catch (BundleException rte) {
                // expected
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testOptionalRequireBundle() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            
            
            // Bundle-SymbolicName: classloader.bundleB
            // Require-Bundle: classloader.bundleA;resolution:=optional
            Archive<?> assemblyB = assembleArchive("optionalrequirebundleA", "/bundles/classloader/optionalrequirebundleA", B.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                assertLoadClass(bundleB, A.class.getName(), bundleA);
                assertLoadClass(bundleB, B.class.getName(), bundleB);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testOptionalRequireBundleFails() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            
            // Bundle-SymbolicName: classloader.bundleB
            // Require-Bundle: doesnotexist;resolution:=optional
            Archive<?> assemblyB = assembleArchive("optionalrequirebundlefails", "/bundles/classloader/optionalrequirebundlefails", B.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                assertLoadClassFail(bundleB, A.class.getName());
                assertLoadClass(bundleB, B.class.getName());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testReExportRequireBundle() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            assertLoadClass(bundleA, C.class.getName());

            // Bundle-Name: BundleB
            // Bundle-SymbolicName: classloader.bundleB
            // Require-Bundle: classloader.bundleA;visibility:=reexport
            // Export-Package: org.jboss.test.osgi.framework.classloader.support.b
            Archive<?> assemblyB = assembleArchive("reexportrequirebundleA", "/bundles/classloader/reexportrequirebundleA", B.class);
            Bundle bundleB = installBundle(assemblyB);

            try {
                bundleB.start();
                assertLoadClass(bundleB, A.class.getName(), bundleA);
                assertLoadClass(bundleB, B.class.getName(), bundleB);
                assertLoadClassFail(bundleB, C.class.getName());

                // Bundle-Name: BundleC
                // Bundle-SymbolicName: classloader.bundleC
                // Require-Bundle: classloader.bundleB
                Archive<?> assemblyC = assembleArchive("reexportrequirebundleB", "/bundles/classloader/reexportrequirebundleB");
                Bundle bundleC = installBundle(assemblyC);

                try {
                    assertLoadClass(bundleC, A.class.getName(), bundleA);
                    assertLoadClass(bundleC, B.class.getName(), bundleB);
                    assertLoadClassFail(bundleC, C.class.getName());
                } finally {
                    bundleC.uninstall();
                }
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testNoReExportRequireBundle() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            assertLoadClass(bundleA, A.class.getName());
            
            // Bundle-SymbolicName: classloader.bundleB
            // Require-Bundle: classloader.bundleA
            // Export-Package: org.jboss.test.osgi.framework.classloader.support.b
            Archive<?> assemblyB = assembleArchive("noreexportrequirebundleA", "/bundles/classloader/noreexportrequirebundleA", B.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                assertLoadClass(bundleB, A.class.getName(), bundleA);
                assertLoadClass(bundleB, B.class.getName(), bundleB);

                // Bundle-SymbolicName: classloader.bundleC
                // Require-Bundle: classloader.bundleB
                Archive<?> assemblyC = assembleArchive("reexportrequirebundleB", "/bundles/classloader/reexportrequirebundleB");
                Bundle bundleC = installBundle(assemblyC);
                try {
                    assertLoadClassFail(bundleC, A.class.getName());
                    assertLoadClass(bundleC, B.class.getName(), bundleB);
                } finally {
                    bundleC.uninstall();
                }
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "classloader.bundleA");
        archive.addClasses(A.class, C.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addExportPackages(A.class.getPackage().getName() + ";version=1.0.0;test=x");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getRequireBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "classloader.bundleB");
        archive.addClasses(B.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addRequireBundle("classloader.bundleA");
                return builder.openStream();
            }
        });
        return archive;
    }
}
