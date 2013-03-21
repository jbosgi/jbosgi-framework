package org.jboss.test.osgi.framework.bundle;

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

import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import junit.framework.Assert;

import org.jboss.logging.Logger;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA2;
import org.jboss.test.osgi.framework.bundle.support.b.ObjectB;
import org.jboss.test.osgi.framework.bundle.support.x.ObjectX;
import org.jboss.test.osgi.framework.bundle.support.y.ObjectY;
import org.jboss.test.osgi.framework.bundle.update.a.ClassA;
import org.jboss.test.osgi.framework.bundle.update.b.ClassB;
import org.jboss.test.osgi.framework.bundle.update.startexc.BundleStartExActivator;
import org.jboss.test.osgi.framework.bundle.update.stopexc.BundleStopExActivator;
import org.jboss.test.osgi.framework.fragments.fragA.FragBeanA;
import org.jboss.test.osgi.framework.fragments.hostA.HostAActivator;
import org.jboss.test.osgi.framework.fragments.subA.SubBeanA;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;
import org.osgi.framework.wiring.FrameworkWiring;

/**
 * BundleUpdateTestCase
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class BundleUpdateTestCase extends OSGiFrameworkTest {

    @Test
    public void testUpdate() throws Exception {
        Archive<?> assembly1 = assembleArchive("bundle1", "/bundles/update/update-bundle1", ObjectA.class);
        Archive<?> assembly2 = assembleArchive("bundle2", "/bundles/update/update-bundle101", ObjectB.class);
        Archive<?> assemblyy = assembleArchive("bundley", "/bundles/update/update-bundley", ObjectY.class);
        Bundle bundle1 = installBundle(assembly1);
        Bundle bundleY = installBundle(assemblyy);
        try {
            BundleContext systemContext = getFramework().getBundleContext();
            int beforeCount = systemContext.getBundles().length;

            bundleY.start();
            assertBundleState(Bundle.ACTIVE, bundleY.getState());

            bundle1.start();
            assertBundleState(Bundle.ACTIVE, bundle1.getState());
            assertEquals(Version.parseVersion("1.0.0"), bundle1.getVersion());
            assertEquals("update-bundle1", bundle1.getSymbolicName());
            assertLoadClass(bundle1, ObjectA.class.getName());
            assertLoadClassFail(bundle1, ObjectB.class.getName());

            Class<?> clsY = bundleY.loadClass(ObjectY.class.getName());

            bundle1.update(toInputStream(assembly2));
            assertBundleState(Bundle.ACTIVE, bundle1.getState());
            assertEquals(Version.parseVersion("1.0.1"), bundle1.getVersion());
            // Nobody depends on the packages, so we can update them straight away
            assertLoadClass(bundle1, ObjectB.class.getName());
            assertLoadClassFail(bundle1, ObjectA.class.getName());

            assertSame(clsY, bundleY.loadClass(ObjectY.class.getName()));
            assertBundleState(Bundle.ACTIVE, bundleY.getState());

            int afterCount = systemContext.getBundles().length;
            assertEquals("Bundle count", beforeCount, afterCount);
        } finally {
            bundleY.uninstall();
            bundle1.uninstall();
        }
    }

    @Test
    public void testUpdateImportedPackagesRemoved() throws Exception {
        Archive<?> assemblyx = assembleArchive("bundlex", "/bundles/update/update-bundlex", ObjectX.class);
        Archive<?> assembly1 = assembleArchive("bundle1", "/bundles/update/update-bundle1", ObjectA.class);
        Archive<?> assembly2 = assembleArchive("bundle2", "/bundles/update/update-bundle101", ObjectB.class);

        Bundle bundleA = installBundle(assembly1);
        Bundle bundleX = installBundle(assemblyx);
        try {
            BundleContext systemContext = getFramework().getBundleContext();
            int beforeCount = systemContext.getBundles().length;

            bundleA.start();
            bundleX.start();

            Class<?> cls = bundleX.loadClass(ObjectX.class.getName());
            cls.newInstance();

            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.0"), bundleA.getVersion());
            assertEquals("update-bundle1", bundleA.getSymbolicName());
            assertLoadClass(bundleA, ObjectA.class.getName());
            assertLoadClassFail(bundleA, ObjectB.class.getName());
            assertLoadClass(bundleX, ObjectA.class.getName());

            bundleA.update(toInputStream(assembly2));
            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.1"), bundleA.getVersion());
            // Assembly X depends on a package in the bundle, this should still be available
            assertLoadClass(bundleX, ObjectA.class.getName());

            assertNoFrameworkEvent();
            FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
            frameworkWiring.refreshBundles(Arrays.asList(bundleA), this);
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            // Bundle X is installed because it cannot be resolved any more
            assertBundleState(Bundle.INSTALLED, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.1"), bundleA.getVersion());
            // Nobody depends on the packages, so we can update them straight away
            assertLoadClass(bundleA, ObjectB.class.getName());
            assertLoadClassFail(bundleA, ObjectA.class.getName());

            int afterCount = systemContext.getBundles().length;
            assertEquals("Bundle count", beforeCount, afterCount);
        } finally {
            bundleX.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testUpdateImportedPackages() throws Exception {
        Archive<?> assemblyx = assembleArchive("bundlex", "/bundles/update/update-bundlex", ObjectX.class);
        Archive<?> assembly1 = assembleArchive("bundle1", new String[] { "/bundles/update/update-bundle1", "/bundles/update/classes1" });
        Archive<?> assembly2 = assembleArchive("bundle2", new String[] { "/bundles/update/update-bundle102", "/bundles/update/classes2" });

        Bundle bundle1 = installBundle(assembly1);
        Bundle bundleX = installBundle(assemblyx);
        try {
            BundleContext systemContext = getFramework().getBundleContext();
            int beforeCount = systemContext.getBundles().length;

            bundle1.start();
            bundleX.start();

            assertBundleState(Bundle.ACTIVE, bundle1.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.0"), bundle1.getVersion());
            assertEquals("update-bundle1", bundle1.getSymbolicName());
            assertLoadClass(bundle1, ObjectA.class.getName());
            assertLoadClassFail(bundle1, ObjectA2.class.getName());
            assertLoadClass(bundleX, ObjectA.class.getName());
            assertLoadClassFail(bundleX, ObjectA2.class.getName());

            Class<?> cls = bundleX.loadClass(ObjectX.class.getName());
            Object x1 = cls.newInstance();
            assertEquals("ObjectX contains reference: ObjectA", x1.toString());

            bundle1.update(toInputStream(assembly2));
            assertBundleState(Bundle.ACTIVE, bundle1.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.2"), bundle1.getVersion());
            // Bundle A should see the new version of the packages
            assertLoadClass(bundle1, ObjectA2.class.getName());
            assertLoadClassFail(bundle1, ObjectA.class.getName());
            // Bundle X should still see the old packages of bundle A
            assertLoadClass(bundleX, ObjectA.class.getName());
            assertLoadClassFail(bundleX, ObjectA2.class.getName());
            assertSame(cls, bundleX.loadClass(ObjectX.class.getName()));

            FrameworkWiring frameworkWiring = getFramework().adapt(FrameworkWiring.class);
            frameworkWiring.refreshBundles(Arrays.asList(bundle1), this);
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertBundleState(Bundle.ACTIVE, bundle1.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.2"), bundle1.getVersion());
            assertLoadClass(bundle1, ObjectA2.class.getName());
            assertLoadClassFail(bundle1, ObjectA.class.getName());
            assertLoadClass(bundleX, ObjectA2.class.getName());
            assertLoadClassFail(bundleX, ObjectA.class.getName());

            Class<?> cls2 = bundleX.loadClass(ObjectX.class.getName());
            assertNotSame("Should have loaded a new class", cls, cls2);
            Object x2 = cls2.newInstance();
            assertEquals("ObjectX contains reference: ObjectA2", x2.toString());

            int afterCount = systemContext.getBundles().length;
            assertEquals("Bundle count", beforeCount, afterCount);
        } finally {
            bundleX.uninstall();
            bundle1.uninstall();
        }
    }

    @Test
    public void testUpdateReadError() throws Exception {
        Archive<?> assembly1 = assembleArchive("bundle1", "/bundles/update/update-bundle1", ObjectA.class);
        Bundle bundle = installBundle(assembly1);
        try {
            BundleContext systemContext = getFramework().getBundleContext();
            int beforeCount = systemContext.getBundles().length;

            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
            assertEquals(Version.parseVersion("1.0.0"), bundle.getVersion());
            assertEquals("update-bundle1", bundle.getSymbolicName());
            assertLoadClass(bundle, ObjectA.class.getName());
            assertLoadClassFail(bundle, ObjectB.class.getName());

            InputStream ismock = mock(InputStream.class);
            when(ismock.read()).thenThrow(new IOException());
            when(ismock.read((byte[]) Mockito.anyObject())).thenThrow(new IOException());
            when(ismock.read((byte[]) Mockito.anyObject(), Mockito.anyInt(), Mockito.anyInt())).thenThrow(new IOException());

            try {
                bundle.update(ismock);
                fail("Should have thrown a BundleException as the InputStream is unreadable");
            } catch (BundleException e) {
                // good
            }
            assertBundleState(Bundle.ACTIVE, bundle.getState());
            assertEquals(Version.parseVersion("1.0.0"), bundle.getVersion());
            assertEquals("update-bundle1", bundle.getSymbolicName());
            assertLoadClass(bundle, ObjectA.class.getName());
            assertLoadClassFail(bundle, ObjectB.class.getName());

            int afterCount = systemContext.getBundles().length;
            assertEquals("Bundle count", beforeCount, afterCount);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testUpdateExceptionStop() throws Exception {
        Archive<?> assembly1 = assembleArchive("update-bundle-stop-exc1", "/bundles/update/update-bundle-stop-exc1", BundleStopExActivator.class);
        Archive<?> assembly2 = assembleArchive("update-bundle-stop-exc2", "/bundles/update/update-bundle-stop-exc2");
        Bundle bundle1 = installBundle(assembly1);
        try {
            bundle1.start();
            assertEquals(Version.parseVersion("1"), bundle1.getVersion());
            try {
                bundle1.update(toInputStream(assembly2));
                fail("Should have thrown a bundle exception.");
            } catch (BundleException be) {
                // good
            }
            assertEquals("Because bundle.stop() throws an exception the update should not have been applied", Version.parseVersion("1"), bundle1.getVersion());
        } finally {
            bundle1.uninstall();
        }
    }

    @Test
    public void testUpdateExceptionStart() throws Exception {
        Archive<?> assembly1 = assembleArchive("update-bundle-start-exc1", "/bundles/update/update-bundle-start-exc1");
        Archive<?> assembly2 = assembleArchive("update-bundle-start-exc2", "/bundles/update/update-bundle-start-exc2", BundleStartExActivator.class);
        Bundle bundle1 = installBundle(assembly1);
        try {
            bundle1.start();
            assertEquals(Version.parseVersion("1"), bundle1.getVersion());

            getSystemContext().addFrameworkListener(this);
            bundle1.update(toInputStream(assembly2));
            assertFrameworkEvent(FrameworkEvent.ERROR, bundle1, BundleException.class);
            assertEquals(Version.parseVersion("2"), bundle1.getVersion());
        } finally {
            getSystemContext().removeFrameworkListener(this);
            bundle1.uninstall();
        }
    }

    @Test
    public void testUpdateSameSymbolicNameAndVersion() throws Exception {
        Archive<?> assembly1 = assembleArchive("bundle1", "/bundles/update/update-bundlea", ClassA.class);
        Archive<?> assembly2 = assembleArchive("bundle2", "/bundles/update/update-bundleb", ClassB.class);
        Bundle bundle1 = installBundle(assembly1);
        try {
            bundle1.start();
            assertBundleState(Bundle.ACTIVE, bundle1.getState());
            assertLoadClass(bundle1, ClassA.class.getName());
            assertLoadClassFail(bundle1, ClassB.class.getName());

            bundle1.update(toInputStream(assembly2));
            assertBundleState(Bundle.ACTIVE, bundle1.getState());
            assertLoadClass(bundle1, ClassB.class.getName());
            assertLoadClassFail(bundle1, ClassA.class.getName());
        } finally {
            bundle1.uninstall();
        }
    }

    @Test
    public void testUpdateHostToFragment() throws Exception {
        Bundle bundleA = installBundle(getHostA());
        try {
            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            BundleRevisions brevs = bundleA.adapt(BundleRevisions.class);
            assertEquals(1, brevs.getRevisions().size());
            BundleRevision brev = brevs.getRevisions().get(0);
            assertEquals(0, brev.getTypes());

            bundleA.update(toInputStream(getFragmentA()));
            brevs = bundleA.adapt(BundleRevisions.class);
            Assert.assertNotSame(brev, brevs.getRevisions().get(0));
            brev = brevs.getRevisions().get(0);
            assertEquals(BundleRevision.TYPE_FRAGMENT, brev.getTypes());
        } finally {
            bundleA.uninstall();
        }
    }

    private JavaArchive getHostA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostA");
        archive.addClasses(HostAActivator.class, SubBeanA.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(HostAActivator.class);
                builder.addImportPackages(BundleActivator.class, Logger.class, FrameworkWiring.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragA");
        archive.addClasses(FragBeanA.class);
        archive.addAsResource(getResourceFile("fragments/fragA.txt"), "resource.txt");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(FragBeanA.class);
                builder.addFragmentHost("simple-hostA");
                return builder.openStream();
            }
        });
        return archive;
    }

}
