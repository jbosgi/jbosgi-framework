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
package org.jboss.test.osgi.framework.xservice;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;

/**
 * Test the {@link XBundle} API for module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jun-2012
 */
public class ModuleBundleTestCase extends AbstractModuleIntegrationTest {

    Module moduleA;
    Module moduleB;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        moduleA = loadModule(getModuleA());
        moduleB = loadModule(getModuleB());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        removeModule(moduleA);
        removeModule(moduleB);
    }

    @Test
    public void testBundle() throws Exception {

        XBundle bundleA = installResource(moduleA).getBundle();
        try {
            // getBundleContext
            BundleContext context = bundleA.getBundleContext();
            Assert.assertEquals(getSystemContext(), context);

            // getBundleId, getLastModified, getSymbolicName, getVersion, getLocation
            Assert.assertTrue("Bundle id > 0", bundleA.getBundleId() > 0);
            Assert.assertTrue("Last modified > 0", bundleA.getLastModified() > 0);
            Assert.assertEquals(moduleA.getIdentifier().getName(), bundleA.getSymbolicName());
            Assert.assertEquals(Version.emptyVersion, bundleA.getVersion());
            Assert.assertEquals(moduleA.getIdentifier().getName(), bundleA.getLocation());

            // getState
            Assert.assertEquals(Bundle.RESOLVED, bundleA.getState());

            // [TODO] Add support for manifest header related APIs on Module adaptors
            // https://issues.jboss.org/browse/JBOSGI-567
            Assert.assertTrue(bundleA.getHeaders().isEmpty());
            Assert.assertTrue(bundleA.getHeaders(null).isEmpty());

            String resname = ModuleServiceX.class.getName().replace('.', '/').concat(".class");
            Assert.assertNotNull(bundleA.getResource(resname));
            Assert.assertNotNull(bundleA.getResources(resname));
            Assert.assertNull(bundleA.findEntries(resname, null, true));
            Assert.assertNotNull(bundleA.getEntry(resname));
            Assert.assertNotNull(bundleA.getEntryPaths(resname));

            // getRegisteredServices, getServicesInUse
            Assert.assertNull(bundleA.getRegisteredServices());
            Assert.assertNull(bundleA.getServicesInUse());

            // loadClass
            OSGiTestHelper.assertLoadClass(bundleA, ModuleServiceX.class.getName());

            bundleA.start();
            Assert.assertEquals(Bundle.ACTIVE, bundleA.getState());

            bundleA.stop();
            Assert.assertEquals(Bundle.RESOLVED, bundleA.getState());

            try {
                bundleA.update();
                Assert.fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }

            // uninstall
            bundleA.uninstall();
            Assert.assertEquals(Bundle.UNINSTALLED, bundleA.getState());
        } finally {
            uninstallResource(bundleA.getBundleRevision());
        }
    }

    @Test
    @Ignore
    public void testStartLevel() throws Exception {
        FrameworkStartLevel fwStartLevel = getFramework().adapt(FrameworkStartLevel.class);
        int orgStartLevel = fwStartLevel.getStartLevel();
        int orgInitialStartlevel = fwStartLevel.getInitialBundleStartLevel();
        try {
            Assert.assertEquals(1, fwStartLevel.getInitialBundleStartLevel());
            fwStartLevel.setInitialBundleStartLevel(5);
            Assert.assertEquals(5, fwStartLevel.getInitialBundleStartLevel());

            XBundle bundleA = installResource(moduleA).getBundle();
            try {
                assertBundleState(Bundle.RESOLVED, bundleA.getState());
                BundleStartLevel startLevelA = bundleA.adapt(BundleStartLevel.class);
                Assert.assertEquals(5, startLevelA.getStartLevel());
                bundleA.start();
                assertBundleState(Bundle.RESOLVED, bundleA.getState());

                fwStartLevel.setStartLevel(5);
                assertBundleState(Bundle.ACTIVE, bundleA.getState());

                fwStartLevel.setStartLevel(4);
                assertBundleState(Bundle.RESOLVED, bundleA.getState());

                fwStartLevel.setInitialBundleStartLevel(7);
                Assert.assertEquals(7, fwStartLevel.getInitialBundleStartLevel());

                fwStartLevel.setStartLevel(10);
                assertBundleState(Bundle.ACTIVE, bundleA.getState());

                XBundle bundleB = installResource(moduleB).getBundle();
                try {
                    assertBundleState(Bundle.RESOLVED, bundleB.getState());
                    //Assert.assertFalse(fwStartLevel.isBundlePersistentlyStarted(bundleB));
                    //Assert.assertEquals(7, fwStartLevel.getBundleStartLevel(bundleB));
                    bundleB.start();
                    assertBundleState(Bundle.ACTIVE, bundleB.getState());
                    //Assert.assertTrue(fwStartLevel.isBundlePersistentlyStarted(bundleB));

                    BundleStartLevel startLevelB = bundleB.adapt(BundleStartLevel.class);
                    startLevelB.setStartLevel(11);
                    assertBundleState(Bundle.RESOLVED, bundleB.getState());
                    startLevelB.setStartLevel(9);
                    assertBundleState(Bundle.ACTIVE, bundleB.getState());

                    fwStartLevel.setStartLevel(1);
                    assertBundleState(Bundle.RESOLVED, bundleA.getState());
                    assertBundleState(Bundle.RESOLVED, bundleB.getState());
                } finally {
                    bundleB.uninstall();
                }
            } finally {
                bundleA.uninstall();
            }
        } finally {
            fwStartLevel.setInitialBundleStartLevel(orgInitialStartlevel);
            fwStartLevel.setStartLevel(orgStartLevel);
        }
    }

    private JavaArchive getModuleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }

    private JavaArchive getModuleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }
}
