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

import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.bundleA.BundleActivatorA;
import org.jboss.test.osgi.framework.xservice.bundleA.BundleServiceA;
import org.jboss.test.osgi.framework.xservice.bundleB.BundleActivatorB;
import org.jboss.test.osgi.framework.xservice.bundleB.BundleServiceB;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.jboss.test.osgi.framework.xservice.moduleY.ModuleServiceY;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Test dependency scenarios between modules with included 
 * jbosgi-xservice.properties and bundles.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jul-2010
 */
public class FakeBundleDependencyTestCase extends AbstractModuleIntegrationTest {

    @Test
    public void testSimpleModule() throws Exception {
        // Install module through {@link BundleContext}
        Bundle bundleX = installBundle(getModuleX());
        try {
            Assert.assertNotNull("Bundle not null", bundleX);
            Assert.assertEquals("moduleX", bundleX.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), bundleX.getVersion());

            assertLoadClass(bundleX, ModuleServiceX.class.getName());
            assertLoadClassFail(bundleX, ModuleServiceY.class.getName());
            assertBundleState(Bundle.RESOLVED, bundleX.getState());

            bundleX.start();
            assertBundleState(Bundle.ACTIVE, bundleX.getState());

            bundleX.stop();
            assertBundleState(Bundle.RESOLVED, bundleX.getState());
        } finally {
            bundleX.uninstall();
        }
    }

    @Test
    public void testBundleDependsOnModuleOne() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            Assert.assertNotNull("Bundle not null", bundleA);
            Assert.assertEquals("bundleA", bundleA.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), bundleA.getVersion());

            try {
                bundleA.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // ignore
            }

            // Install the dependent module
            Bundle bundleX = installBundle(getModuleX());
            try {
                assertBundleState(Bundle.INSTALLED, bundleX.getState());

                assertLoadClass(bundleX, ModuleServiceX.class.getName());
                assertLoadClassFail(bundleX, BundleServiceA.class.getName());
                assertBundleState(Bundle.RESOLVED, bundleX.getState());

                assertLoadClass(bundleA, BundleServiceA.class.getName());
                assertLoadClass(bundleA, ModuleServiceX.class.getName(), bundleX);
                assertBundleState(Bundle.RESOLVED, bundleA.getState());

                bundleA.start();
                assertBundleState(Bundle.ACTIVE, bundleA.getState());
            } finally {
                bundleX.uninstall();
            }

            bundleA.stop();
            assertBundleState(Bundle.RESOLVED, bundleA.getState());
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testBundleDependsOnModuleTwo() throws Exception {
        Bundle bundleA = installBundle(getBundleAA());
        try {
            Assert.assertNotNull("Bundle not null", bundleA);
            Assert.assertEquals("bundleAA", bundleA.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), bundleA.getVersion());

            try {
                bundleA.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // ignore
            }

            // Install the dependent module
            Bundle bundleX = installBundle(getModuleX());
            try {
                assertBundleState(Bundle.INSTALLED, bundleX.getState());

                assertLoadClass(bundleX, ModuleServiceX.class.getName());
                assertLoadClassFail(bundleX, BundleServiceA.class.getName());
                assertBundleState(Bundle.RESOLVED, bundleX.getState());

                assertLoadClass(bundleA, BundleServiceA.class.getName());
                assertLoadClass(bundleA, ModuleServiceX.class.getName(), bundleX);
                assertBundleState(Bundle.RESOLVED, bundleA.getState());

                bundleA.start();
                assertBundleState(Bundle.ACTIVE, bundleA.getState());
            } finally {
                bundleX.uninstall();
            }

            bundleA.stop();
            assertBundleState(Bundle.RESOLVED, bundleA.getState());
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testModuleDependsOnBundle() throws Exception {
        // Install module through {@link BundleContext}
        Bundle moduleB = installBundle(getModuleY());
        try {
            Assert.assertNotNull("Bundle not null", moduleB);
            Assert.assertEquals("moduleY", moduleB.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), moduleB.getVersion());

            try {
                moduleB.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // ignore
            }

            // Install the dependent bundle
            Bundle bundleB = installBundle(getBundleB());
            try {
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                assertLoadClass(bundleB, BundleServiceB.class.getName());
                assertLoadClassFail(bundleB, ModuleServiceY.class.getName());
                assertBundleState(Bundle.RESOLVED, bundleB.getState());

                assertLoadClass(moduleB, ModuleServiceY.class.getName());
                assertLoadClass(moduleB, BundleServiceB.class.getName(), bundleB);
                assertBundleState(Bundle.RESOLVED, moduleB.getState());

                moduleB.start();
                assertBundleState(Bundle.ACTIVE, moduleB.getState());
            } finally {
                bundleB.uninstall();
            }

            moduleB.stop();
            assertBundleState(Bundle.RESOLVED, moduleB.getState());
        } finally {
            moduleB.uninstall();
        }
    }

    // Bundle-SymbolicName: moduleX
    // Bundle-Version: 1.0.0
    // Export-Package: org.jboss.test.osgi.framework.xservice.moduleX
    private JavaArchive getModuleX() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleX");
        archive.addAsManifestResource(getResourceFile("xservice/moduleX/META-INF/jbosgi-xservice.properties"));
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }

    // Bundle-SymbolicName: moduleY
    // Bundle-Version: 1.0.0
    // Require-Bundle: bundleB
    private JavaArchive getModuleY() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleY");
        archive.addAsManifestResource(getResourceFile("xservice/moduleY/META-INF/jbosgi-xservice.properties"));
        archive.addClasses(ModuleServiceY.class);
        return archive;
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.addClasses(BundleActivatorA.class, BundleServiceA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(BundleActivatorA.class);
                builder.addRequireBundle("moduleX;bundle-version:=1.0.0");
                builder.addImportPackages("org.osgi.framework");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleAA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleAA");
        archive.addClasses(BundleActivatorA.class, BundleServiceA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(BundleActivatorA.class);
                builder.addImportPackages(ModuleServiceX.class, BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleB");
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
