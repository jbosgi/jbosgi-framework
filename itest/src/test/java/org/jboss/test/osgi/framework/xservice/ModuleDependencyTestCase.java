/*
 * #%L
 * JBossOSGi Framework iTest
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
package org.jboss.test.osgi.framework.xservice;


import java.io.InputStream;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.bundleA.BundleActivatorA;
import org.jboss.test.osgi.framework.xservice.bundleA.BundleServiceA;
import org.jboss.test.osgi.framework.xservice.bundleB.BundleActivatorB;
import org.jboss.test.osgi.framework.xservice.bundleB.BundleServiceB;
import org.jboss.test.osgi.framework.xservice.moduleA.ModuleServiceA;
import org.jboss.test.osgi.framework.xservice.moduleB.ModuleServiceB;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Test that an MSC module can have a dependency on an OSGi bundle and vice versa.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jul-2010
 */
public class ModuleDependencyTestCase extends AbstractModuleIntegrationTest {

    @Test
    public void testSimpleModuleOne() throws Exception {
        // Install module to {@link XEnvironment} directly
        Module moduleA = loadModule(getModuleA());
        XBundle bundleA = installResource(moduleA).getBundle();
        try {
            Assert.assertNotNull("Bundle not null", bundleA);
            Assert.assertEquals("moduleA", bundleA.getSymbolicName());
            Assert.assertEquals(Version.emptyVersion, bundleA.getVersion());

            assertLoadClass(bundleA, ModuleServiceA.class.getName());
            assertLoadClassFail(bundleA, ModuleServiceB.class.getName());
            assertBundleState(Bundle.RESOLVED, bundleA.getState());

            try {
                bundleA.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
        } finally {
            uninstallResource(bundleA.getBundleRevision());
            removeModule(moduleA);
        }
    }

    @Test
    public void testSimpleModuleTwo() throws Exception {
        // Install module through {@link BundleContext}
        Bundle bundleA = installBundle(getModuleA());
        try {
            Assert.assertNotNull("Bundle not null", bundleA);
            Assert.assertEquals("moduleA", bundleA.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), bundleA.getVersion());

            assertLoadClass(bundleA, ModuleServiceA.class.getName());
            assertLoadClassFail(bundleA, ModuleServiceB.class.getName());
            assertBundleState(Bundle.RESOLVED, bundleA.getState());

            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());

            bundleA.stop();
            assertBundleState(Bundle.RESOLVED, bundleA.getState());
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testModuleDependsOnBundleOne() throws Exception {
        // Install module through {@link BundleContext}
        Bundle moduleB = installBundle(getModuleB());
        try {
            Assert.assertNotNull("Bundle not null", moduleB);
            Assert.assertEquals("moduleB", moduleB.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), moduleB.getVersion());

            try {
                moduleB.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // ignore
            }

            // Install the dependent bundle
            Bundle bundleD = installBundle(getBundleD());
            try {
                assertBundleState(Bundle.INSTALLED, bundleD.getState());

                assertLoadClass(bundleD, BundleServiceB.class.getName());
                assertLoadClassFail(bundleD, ModuleServiceB.class.getName());
                assertBundleState(Bundle.RESOLVED, bundleD.getState());

                assertLoadClass(moduleB, ModuleServiceB.class.getName());
                assertLoadClass(moduleB, BundleServiceB.class.getName(), bundleD);
                assertBundleState(Bundle.RESOLVED, moduleB.getState());

                moduleB.start();
                assertBundleState(Bundle.ACTIVE, moduleB.getState());
            } finally {
                bundleD.uninstall();
            }

            moduleB.stop();
            assertBundleState(Bundle.RESOLVED, moduleB.getState());
        } finally {
            moduleB.uninstall();
        }
    }

    @Test
    public void testModuleDependsOnBundleTwo() throws Exception {
        // Install module through {@link BundleContext}
        Bundle moduleB = installBundle(getModuleB());
        try {
            Assert.assertNotNull("Bundle not null", moduleB);
            Assert.assertEquals("moduleB", moduleB.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), moduleB.getVersion());

            try {
                moduleB.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // ignore
            }

            // Install the dependent bundle
            Bundle bundleD = installBundle(getBundleD());
            try {
                assertBundleState(Bundle.INSTALLED, bundleD.getState());

                assertLoadClass(bundleD, BundleServiceB.class.getName());
                assertLoadClassFail(bundleD, ModuleServiceB.class.getName());
                assertBundleState(Bundle.RESOLVED, bundleD.getState());

                assertLoadClass(moduleB, ModuleServiceB.class.getName());
                assertLoadClass(moduleB, BundleServiceB.class.getName(), bundleD);
                assertBundleState(Bundle.RESOLVED, moduleB.getState());

                moduleB.start();
                assertBundleState(Bundle.ACTIVE, moduleB.getState());
            } finally {
                bundleD.uninstall();
            }

            moduleB.stop();
            assertBundleState(Bundle.RESOLVED, moduleB.getState());
        } finally {
            moduleB.uninstall();
        }
    }

    @Test
    public void testBundleDependsOnModule() throws Exception {
        Bundle bundleA = installBundle(getBundleA1());
        try {
            Assert.assertNotNull("Bundle not null", bundleA);
            Assert.assertEquals("bundleA1", bundleA.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), bundleA.getVersion());

            try {
                bundleA.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // ignore
            }

            // Install the dependent module
            Bundle moduleC = installBundle(getModuleA());
            try {
                assertBundleState(Bundle.INSTALLED, moduleC.getState());

                assertLoadClass(moduleC, ModuleServiceA.class.getName());
                assertLoadClassFail(moduleC, BundleServiceA.class.getName());
                assertBundleState(Bundle.RESOLVED, moduleC.getState());

                assertLoadClass(bundleA, BundleServiceA.class.getName());
                assertLoadClass(bundleA, ModuleServiceA.class.getName(), moduleC);
                assertBundleState(Bundle.RESOLVED, bundleA.getState());

                bundleA.start();
                assertBundleState(Bundle.ACTIVE, bundleA.getState());
            } finally {
                moduleC.uninstall();
            }

            bundleA.stop();
            assertBundleState(Bundle.RESOLVED, bundleA.getState());
        } finally {
            bundleA.uninstall();
        }
    }

    // Bundle-SymbolicName: moduleA
    // Bundle-Version: 1.0.0
    // Export-Package: org.jboss.test.osgi.framework.xservice.moduleA
    private JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addAsManifestResource(getResourceFile("xservice/moduleA/META-INF/jbosgi-xservice.properties"));
        archive.addClasses(ModuleServiceA.class);
        return archive;
    }

    // Bundle-SymbolicName: moduleB
    // Bundle-Version: 1.0.0
    // Require-Bundle: bundleD
    private JavaArchive getModuleB() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addAsManifestResource(getResourceFile("xservice/moduleB/META-INF/jbosgi-xservice.properties"));
        archive.addClasses(ModuleServiceB.class);
        return archive;
    }

    private JavaArchive getBundleA1() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA1");
        archive.addClasses(BundleActivatorA.class, BundleServiceA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(BundleActivatorA.class);
                builder.addRequireBundle("moduleA");
                builder.addImportPackages("org.osgi.framework");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleA2() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA2");
        archive.addClasses(BundleActivatorA.class, BundleServiceA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(BundleActivatorA.class);
                builder.addImportPackages(ModuleServiceA.class, BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleD() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleD");
        archive.addClasses(BundleActivatorB.class, BundleServiceB.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(BundleActivatorB.class);
                builder.addExportPackages(BundleServiceB.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
