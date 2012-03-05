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
package org.jboss.test.osgi.framework.bundle;

import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA2;
import org.jboss.test.osgi.framework.bundle.support.b.ObjectB;
import org.jboss.test.osgi.framework.bundle.support.x.ObjectX;
import org.jboss.test.osgi.framework.bundle.support.y.ObjectY;
import org.jboss.test.osgi.framework.bundle.update.a.ClassA;
import org.jboss.test.osgi.framework.bundle.update.b.ClassB;
import org.jboss.test.osgi.framework.bundle.update.startexc.BundleStartExActivator;
import org.jboss.test.osgi.framework.bundle.update.stopexc.BundleStopExActivator;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;

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
            getSystemContext().addFrameworkListener(this);
            getPackageAdmin().refreshPackages(new Bundle[] { bundleA });
            assertFrameworkEvent(FrameworkEvent.ERROR, bundleX, BundleException.class);
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
            getSystemContext().removeFrameworkListener(this);
            bundleX.uninstall();
            bundleA.uninstall();
        }
    }

    @Test
    public void testUpdateImportedPackages() throws Exception {
        Archive<?> assemblyx = assembleArchive("bundlex", "/bundles/update/update-bundlex", ObjectX.class);
        Archive<?> assembly1 = assembleArchive("bundle1", new String[] { "/bundles/update/update-bundle1", "/bundles/update/classes1" });
        Archive<?> assembly2 = assembleArchive("bundle2", new String[] { "/bundles/update/update-bundle102", "/bundles/update/classes2" });

        Bundle bundleA = installBundle(assembly1);
        Bundle bundleX = installBundle(assemblyx);
        try {
            BundleContext systemContext = getFramework().getBundleContext();
            int beforeCount = systemContext.getBundles().length;

            bundleA.start();
            bundleX.start();

            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.0"), bundleA.getVersion());
            assertEquals("update-bundle1", bundleA.getSymbolicName());
            assertLoadClass(bundleA, ObjectA.class.getName());
            assertLoadClassFail(bundleA, ObjectA2.class.getName());
            assertLoadClass(bundleX, ObjectA.class.getName());
            assertLoadClassFail(bundleX, ObjectA2.class.getName());

            Class<?> cls = bundleX.loadClass(ObjectX.class.getName());
            Object x1 = cls.newInstance();
            assertEquals("ObjectX contains reference: ObjectA", x1.toString());

            bundleA.update(toInputStream(assembly2));
            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.2"), bundleA.getVersion());
            // Bundle A should see the new version of the packages
            assertLoadClass(bundleA, ObjectA2.class.getName());
            assertLoadClassFail(bundleA, ObjectA.class.getName());
            // Bundle X should still see the old packages of bundle A
            assertLoadClass(bundleX, ObjectA.class.getName());
            assertLoadClassFail(bundleX, ObjectA2.class.getName());
            assertSame(cls, bundleX.loadClass(ObjectX.class.getName()));

            getSystemContext().addFrameworkListener(this);
            getPackageAdmin().refreshPackages(new Bundle[] { bundleA });
            assertFrameworkEvent(FrameworkEvent.PACKAGES_REFRESHED, getSystemContext().getBundle(0), null);

            assertBundleState(Bundle.ACTIVE, bundleA.getState());
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
            assertEquals(Version.parseVersion("1.0.2"), bundleA.getVersion());
            assertLoadClass(bundleA, ObjectA2.class.getName());
            assertLoadClassFail(bundleA, ObjectA.class.getName());
            assertLoadClass(bundleX, ObjectA2.class.getName());
            assertLoadClassFail(bundleX, ObjectA.class.getName());

            Class<?> cls2 = bundleX.loadClass(ObjectX.class.getName());
            assertNotSame("Should have loaded a new class", cls, cls2);
            Object x2 = cls2.newInstance();
            assertEquals("ObjectX contains reference: ObjectA2", x2.toString());

            int afterCount = systemContext.getBundles().length;
            assertEquals("Bundle count", beforeCount, afterCount);
        } finally {
            getSystemContext().removeFrameworkListener(this);
            bundleX.uninstall();
            bundleA.uninstall();
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
}
