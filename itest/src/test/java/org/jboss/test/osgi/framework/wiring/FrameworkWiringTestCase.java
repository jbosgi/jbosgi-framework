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
package org.jboss.test.osgi.framework.wiring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import junit.framework.Assert;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA2;
import org.jboss.test.osgi.framework.bundle.support.x.ObjectX;
import org.jboss.test.osgi.framework.bundle.support.y.ObjectY;
import org.jboss.test.osgi.framework.packageadmin.exported.Exported;
import org.jboss.test.osgi.framework.packageadmin.optimporter.OptionalImport;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.FrameworkWiring;

/**
 * Test the {@link FrameworkWiring}.
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Nov-2012
 */
public class FrameworkWiringTestCase extends OSGiFrameworkTest {

    @Test
    public void testResolveBundles() throws Exception {
        FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleE.getState());
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
            assertTrue(frameworkWiring.resolveBundles(Collections.singleton(bundleE)));
            assertBundleState(Bundle.RESOLVED, bundleE.getState());
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
        } finally {
            bundleI.uninstall();
            bundleE.uninstall();
        }
    }

    @Test
    public void testResolveBundlesNull() throws Exception {
        FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleE.getState());
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
            assertTrue(frameworkWiring.resolveBundles(null));
            assertBundleState(Bundle.RESOLVED, bundleE.getState());
            assertBundleState(Bundle.RESOLVED, bundleI.getState());
        } finally {
            bundleI.uninstall();
            bundleE.uninstall();
        }
    }

    @Test
    public void testCantResolveAllBundles() throws Exception {
        FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
        Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", OptionalImport.class));
        Bundle bundleR = installBundle(assembleArchive("requiring", "/bundles/package-admin/requiring"));
        try {
            assertBundleState(Bundle.INSTALLED, bundleI.getState());
            assertBundleState(Bundle.INSTALLED, bundleR.getState());
            assertFalse(frameworkWiring.resolveBundles(Arrays.asList(bundleR, bundleI)));
            assertTrue(Bundle.RESOLVED == bundleI.getState() || Bundle.INSTALLED == bundleI.getState());
            assertBundleState(Bundle.INSTALLED, bundleR.getState());
        } finally {
            bundleI.uninstall();
            bundleR.uninstall();
        }
    }

    @Test
    public void testSimpleRefreshBundles() throws Exception {
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-importer", "/bundles/package-admin/opt-importer", OptionalImport.class));

        bundleI.start();
        assertLoadClass(bundleI, Exported.class.getName());
        assertNotNull(getImportedFieldValue(bundleI));

        bundleE.uninstall();
        bundleI.uninstall();
        refreshBundles(null);

        Bundle bundleE1 = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Assert.assertNotSame(bundleE, bundleE1);
        Bundle bundleI1 = installBundle(assembleArchive("opt-importer", "/bundles/package-admin/opt-importer", OptionalImport.class));
        Assert.assertNotSame(bundleI, bundleI1);

        bundleI1.start();
        assertLoadClass(bundleI1, Exported.class.getName());
        assertNotNull(getImportedFieldValue(bundleI1));

        bundleE1.uninstall();
        bundleI1.uninstall();
    }

    @Test
    public void testRefreshBundles() throws Exception {
        FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-importer", "/bundles/package-admin/opt-importer", OptionalImport.class));
        try {
            Collection<Bundle> pending = frameworkWiring.getRemovalPendingBundles();
            Assert.assertEquals(0, pending.size());
            Collection<Bundle> closure = frameworkWiring.getDependencyClosure(Collections.singleton(bundleE));
            Assert.assertEquals(1, closure.size());
            Assert.assertTrue("Dependency closure contains BundleE", closure.contains(bundleE));
            closure = frameworkWiring.getDependencyClosure(Collections.singleton(bundleI));
            Assert.assertEquals(1, closure.size());
            Assert.assertTrue("Dependency closure contains BundleI", closure.contains(bundleI));

            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull(getImportedFieldValue(bundleI));

            pending = frameworkWiring.getRemovalPendingBundles();
            Assert.assertEquals(0, pending.size());
            closure = frameworkWiring.getDependencyClosure(Collections.singleton(bundleE));
            Assert.assertEquals(2, closure.size());
            Assert.assertTrue("Dependency closure contains BundleE", closure.contains(bundleE));
            Assert.assertTrue("Dependency closure contains BundleI", closure.contains(bundleI));
            closure = frameworkWiring.getDependencyClosure(Collections.singleton(bundleI));
            Assert.assertEquals(1, closure.size());
            Assert.assertTrue("Dependency closure contains BundleI", closure.contains(bundleI));

            bundleE.uninstall();
            pending = frameworkWiring.getRemovalPendingBundles();
            Assert.assertEquals(1, pending.size());
            Assert.assertTrue("Removal pending contains BundleE", pending.contains(bundleE));

            bundleI.stop();
            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull("The stale bundle E should still be available for classloading so the imported field should have a value", getImportedFieldValue(bundleI));

            frameworkWiring.refreshBundles(Collections.singleton(bundleE), this);
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertEquals(Bundle.ACTIVE, bundleI.getState());
            assertLoadClassFail(bundleI, Exported.class.getName());
            assertNull("Now that the packages are refreshed, bundle E should be no longer available for classloading", getImportedFieldValue(bundleI));
        } finally {
            bundleI.uninstall();
            if (bundleE.getState() != Bundle.UNINSTALLED)
                bundleE.uninstall();
        }
    }

    @Test
    public void testRefreshBundlesNull() throws Exception {
        FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
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
            Collection<Bundle> pending = frameworkWiring.getRemovalPendingBundles();
            Assert.assertTrue("Removal pending BundleA", pending.contains(bundleA));

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

            frameworkWiring.refreshBundles(null, this);
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
            if (bl != null)
                getSystemContext().removeBundleListener(bl);
            bundleY.uninstall();
            bundleX.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testRefreshBundlesNullUninstall() throws Exception {
        FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
        Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
        Bundle bundleI = installBundle(assembleArchive("opt-importer", "/bundles/package-admin/opt-importer", OptionalImport.class));
        try {
            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull(getImportedFieldValue(bundleI));

            bundleE.uninstall();
            bundleI.stop();
            bundleI.start();
            assertLoadClass(bundleI, Exported.class.getName());
            assertNotNull("The stale bundle E should still be available for classloading so the imported field should have a value", getImportedFieldValue(bundleI));

            frameworkWiring.refreshBundles(null, this);
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertBundleState(Bundle.ACTIVE, bundleI.getState());
            assertLoadClassFail(bundleI, Exported.class.getName());
            assertNull("Now that the packages are refreshed, bundle E should be no longer available for classloading", getImportedFieldValue(bundleI));
        } finally {
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
}
