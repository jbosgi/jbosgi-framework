package org.jboss.test.osgi.framework.xservice;
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

import static org.osgi.framework.namespace.BundleNamespace.BUNDLE_NAMESPACE;
import static org.osgi.framework.namespace.PackageNamespace.PACKAGE_NAMESPACE;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
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
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Test that an MSC module can have a dependency on an OSGi bundle and vice versa.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jul-2010
 */
public class ModuleDependencyTestCase extends AbstractModuleIntegrationTest {

    @Test
    public void testSimpleModule() throws Exception {
        // Install module to {@link XEnvironment} directly
        Module moduleX = loadModule(getModuleX());
        XBundle bundleX = installResource(moduleX).getBundle();
        try {
            Assert.assertNotNull("Bundle not null", bundleX);
            Assert.assertEquals("moduleX", bundleX.getSymbolicName());
            Assert.assertEquals(Version.parseVersion("1.0"), bundleX.getVersion());

            assertLoadClass(bundleX, ModuleServiceX.class.getName());
            assertLoadClassFail(bundleX, ModuleServiceY.class.getName());
            assertBundleState(Bundle.RESOLVED, bundleX.getState());

            try {
                bundleX.start();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
        } finally {
            XBundleRevision brev = bundleX.getBundleRevision();
            uninstallResource(brev);
            removeModule(brev, moduleX);
        }
    }

    @Test
    public void testBundleDependsOnModuleOne() throws Exception {
        XBundle bundleA = (XBundle) installBundle(getBundleA());
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

            // Install module to {@link XEnvironment} directly
            Module moduleX = loadModule(getModuleX());
            XBundle bundleX = installResource(moduleX).getBundle();
            try {
                assertLoadClass(bundleX, ModuleServiceX.class.getName());
                assertLoadClassFail(bundleX, BundleServiceA.class.getName());
                assertBundleState(Bundle.RESOLVED, bundleX.getState());

                assertLoadClass(bundleA, BundleServiceA.class.getName());
                assertLoadClass(bundleA, ModuleServiceX.class.getName(), bundleX);
                assertBundleState(Bundle.RESOLVED, bundleA.getState());

                // Verify wiring A
                BundleWiring wiringA = bundleA.getBundleRevision().getWiring();
                Assert.assertEquals(0, wiringA.getProvidedWires(null).size());
                List<BundleWire> wires = wiringA.getRequiredWires(null);
                Assert.assertEquals(2, wires.size());
                BundleWire wire = wires.get(0);
                Assert.assertEquals(PACKAGE_NAMESPACE, wire.getRequirement().getNamespace());
                Assert.assertEquals("org.osgi.framework", wire.getCapability().getAttributes().get(PACKAGE_NAMESPACE));
                Assert.assertSame(bundleA.getBundleRevision(), wire.getRequirer());
                Assert.assertSame(getSystemBundle().getBundleRevision(), wire.getProvider());
                Assert.assertSame(getSystemBundle().getBundleRevision().getWiring(), wire.getProviderWiring());
                wire = wires.get(1);
                Assert.assertEquals(BUNDLE_NAMESPACE, wire.getRequirement().getNamespace());
                Assert.assertEquals("moduleX", wire.getCapability().getAttributes().get(BUNDLE_NAMESPACE));
                Assert.assertSame(bundleA.getBundleRevision(), wire.getRequirer());
                Assert.assertSame(bundleX.getBundleRevision(), wire.getProvider());
                Assert.assertSame(bundleX.getBundleRevision().getWiring(), wire.getProviderWiring());

                // Verify wiring X
                BundleWiring wiringX = bundleX.getBundleRevision().getWiring();
                Assert.assertEquals(0, wiringX.getRequiredWires(null).size());
                wires = wiringX.getProvidedWires(null);
                Assert.assertEquals(1, wires.size());
                wire = wires.get(0);
                Assert.assertEquals(BUNDLE_NAMESPACE, wire.getRequirement().getNamespace());
                Assert.assertEquals("moduleX", wire.getCapability().getAttributes().get(BUNDLE_NAMESPACE));
                Assert.assertSame(bundleA.getBundleRevision(), wire.getRequirer());
                Assert.assertSame(bundleX.getBundleRevision(), wire.getProvider());
                Assert.assertSame(bundleX.getBundleRevision().getWiring(), wire.getProviderWiring());
            } finally {
                XBundleRevision brev = bundleX.getBundleRevision();
                uninstallResource(brev);
                removeModule(brev, moduleX);
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testBundleDependsOnModuleTwo() throws Exception {
        XBundle bundleA = (XBundle) installBundle(getBundleAA());
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

            // Install module to {@link XEnvironment} directly
            Module moduleX = loadModule(getModuleX());
            XBundle bundleX = installResource(moduleX).getBundle();
            try {
                assertLoadClass(bundleX, ModuleServiceX.class.getName());
                assertLoadClassFail(bundleX, BundleServiceA.class.getName());
                assertBundleState(Bundle.RESOLVED, bundleX.getState());

                assertLoadClass(bundleA, BundleServiceA.class.getName());
                assertLoadClass(bundleA, ModuleServiceX.class.getName(), bundleX);
                assertBundleState(Bundle.RESOLVED, bundleA.getState());

                // Verify wiring A
                BundleWiring wiringA = bundleA.getBundleRevision().getWiring();
                Assert.assertEquals(0, wiringA.getProvidedWires(null).size());
                List<BundleWire> wires = wiringA.getRequiredWires(null);
                Assert.assertEquals(2, wires.size());
                BundleWire wire = wires.get(0);
                Assert.assertEquals(PACKAGE_NAMESPACE, wire.getRequirement().getNamespace());
                Assert.assertEquals("org.osgi.framework", wire.getCapability().getAttributes().get(PACKAGE_NAMESPACE));
                Assert.assertSame(bundleA.getBundleRevision(), wire.getRequirer());
                Assert.assertSame(getSystemBundle().getBundleRevision(), wire.getProvider());
                Assert.assertSame(getSystemBundle().getBundleRevision().getWiring(), wire.getProviderWiring());
                wire = wires.get(1);
                Assert.assertEquals(PACKAGE_NAMESPACE, wire.getRequirement().getNamespace());
                Assert.assertEquals(ModuleServiceX.class.getPackage().getName(), wire.getCapability().getAttributes().get(PACKAGE_NAMESPACE));
                Assert.assertSame(bundleA.getBundleRevision(), wire.getRequirer());
                Assert.assertSame(bundleX.getBundleRevision(), wire.getProvider());
                Assert.assertSame(bundleX.getBundleRevision().getWiring(), wire.getProviderWiring());

                // Verify wiring X
                BundleWiring wiringX = bundleX.getBundleRevision().getWiring();
                Assert.assertEquals(0, wiringX.getRequiredWires(null).size());
                wires = wiringX.getProvidedWires(null);
                Assert.assertEquals(1, wires.size());
                wire = wires.get(0);
                Assert.assertEquals(PACKAGE_NAMESPACE, wire.getRequirement().getNamespace());
                Assert.assertEquals(ModuleServiceX.class.getPackage().getName(), wire.getCapability().getAttributes().get(PACKAGE_NAMESPACE));
                Assert.assertSame(bundleA.getBundleRevision(), wire.getRequirer());
                Assert.assertSame(bundleX.getBundleRevision(), wire.getProvider());
                Assert.assertSame(bundleX.getBundleRevision().getWiring(), wire.getProviderWiring());
            } finally {
                XBundleRevision brev = bundleX.getBundleRevision();
                uninstallResource(brev);
                removeModule(brev, moduleX);
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testModuleDependsOnBundle() throws Exception {

        // Install the dependent bundle
        XBundle bundleB = (XBundle) installBundle(getBundleB());
        try {
            assertBundleState(Bundle.INSTALLED, bundleB.getState());

            assertLoadClass(bundleB, BundleActivatorB.class.getName());
            assertLoadClassFail(bundleB, ModuleServiceY.class.getName());
            assertBundleState(Bundle.RESOLVED, bundleB.getState());

            // Install module to {@link XEnvironment} directly
            ModuleIdentifier depid = ModuleIdentifier.fromString("jbosgi.bundleB:1.0.0");
            Module moduleY = loadModule(getModuleY(), Collections.singletonList(depid));
            XBundle bundleY = installResource(moduleY).getBundle();
            try {
                Assert.assertNotNull("Bundle not null", bundleY);
                Assert.assertEquals("moduleY", bundleY.getSymbolicName());
                Assert.assertEquals(Version.parseVersion("1.0"), bundleY.getVersion());

                assertLoadClass(bundleY, ModuleServiceY.class.getName());
                assertLoadClass(bundleY, BundleServiceB.class.getName(), bundleB);
                assertBundleState(Bundle.RESOLVED, bundleY.getState());

                // Verify wiring B
                BundleWiring wiringB = bundleB.getBundleRevision().getWiring();
                Assert.assertEquals(0, wiringB.getProvidedWires(null).size()); // [TODO] required wires for adapted modules
                List<BundleWire> wires = wiringB.getRequiredWires(null);
                Assert.assertEquals(1, wires.size());
                BundleWire wire = wires.get(0);
                Assert.assertEquals(PACKAGE_NAMESPACE, wire.getRequirement().getNamespace());
                Assert.assertEquals("org.osgi.framework", wire.getCapability().getAttributes().get(PACKAGE_NAMESPACE));
                Assert.assertSame(bundleB.getBundleRevision(), wire.getRequirer());
                Assert.assertSame(getSystemBundle().getBundleRevision(), wire.getProvider());
                Assert.assertSame(getSystemBundle().getBundleRevision().getWiring(), wire.getProviderWiring());

                // Verify wiring X
                BundleWiring wiringY = bundleY.getBundleRevision().getWiring();
                Assert.assertEquals(0, wiringY.getRequiredWires(null).size()); // [TODO] required wires for adapted modules
                Assert.assertEquals(0, wiringY.getProvidedWires(null).size());
            } finally {
                XBundleRevision brev = bundleY.getBundleRevision();
                uninstallResource(brev);
                removeModule(brev, moduleY);
            }
        } finally {
            bundleB.uninstall();
        }
    }

    private JavaArchive getModuleX() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleX:1.0.0");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }

    private JavaArchive getModuleY() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleY:1.0.0");
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
                builder.addImportPackages(BundleActivator.class);
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
                builder.addImportPackages(BundleActivator.class, ModuleServiceX.class);
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
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
