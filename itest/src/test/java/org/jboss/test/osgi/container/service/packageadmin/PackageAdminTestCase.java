/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.test.osgi.container.service.packageadmin;

// $Id: $

import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.container.bundle.support.a.ObjectA;
import org.jboss.test.osgi.container.bundle.support.a.ObjectA2;
import org.jboss.test.osgi.container.bundle.support.x.ObjectX;
import org.jboss.test.osgi.container.packageadmin.exported.Exported;
import org.jboss.test.osgi.container.packageadmin.optimporter.Importing;
import org.jboss.test.osgi.container.service.support.a.PA;
import org.jboss.test.osgi.container.service.support.b.Other;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Test PackageAdmin service.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class PackageAdminTestCase extends OSGiFrameworkTest
{
   @Test
   public void testGetBundle() throws Exception
   {
      Archive<?> assemblyA = assembleArchive("smoke-assembled", "/bundles/smoke/smoke-assembled", PA.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         Class<?> paClass = assertLoadClass(bundleA, PA.class.getName());

         PackageAdmin pa = getPackageAdmin();

         Bundle found = pa.getBundle(paClass);
         assertSame(bundleA, found);

         Bundle notFound = pa.getBundle(getClass());
         assertNull(notFound);

         Archive<?> assemblyB = assembleArchive("simple", "/bundles/simple/simple-bundle1", Other.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            Class<?> otherClass = assertLoadClass(bundleB, Other.class.getName());

            found = pa.getBundle(otherClass);
            assertSame(bundleB, found);
         }
         finally
         {
            bundleB.uninstall();
         }
      }
      finally
      {
         bundleA.uninstall();
      }
   }

   @Test
   public void testGetBundles() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testGetBundleType() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testGetExportedPackage() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testGetExportedPackagesByBundle() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testGetExportedPackagesByName() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testGetFragments() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testGetHosts() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testGetRequiredBundles() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }

   @Test
   public void testRefreshPackages() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Bundle bundleE = installBundle(assembleArchive("exporter", "/bundles/package-admin/exporter", Exported.class));
      Bundle bundleI = installBundle(assembleArchive("opt-imporer", "/bundles/package-admin/opt-importer", Importing.class));

      try
      {
         bundleI.start();
         assertLoadClass(bundleI, Exported.class.getName());
         Assert.assertNotNull(getImportedFieldValue(bundleI));

         bundleE.uninstall();
         bundleI.stop();
         bundleI.start();
         assertLoadClass(bundleI, Exported.class.getName());
         Assert.assertNotNull("The stale bundle E should still be available for classloading so the imported field should have a value",
               getImportedFieldValue(bundleI));

         pa.refreshPackages(new Bundle[] { bundleE });
         Assert.assertEquals(Bundle.ACTIVE, bundleI.getState());
         assertLoadClassFail(bundleI, Exported.class.getName());
         assertNull("Now that the packages are refreshed, bundle E should be no longer available for classloading",
               getImportedFieldValue(bundleI));
      }
      finally
      {
         bundleI.uninstall();
         if (bundleE.getState() != Bundle.UNINSTALLED)
            bundleE.uninstall();
      }
   }

   @Test
   public void testRefreshPackagesNull() throws Exception
   {
      PackageAdmin pa = getPackageAdmin();
      Archive<?> assemblyx = assembleArchive("bundlex", "/bundles/update/update-bundlex", ObjectX.class);
      Archive<?> assembly1 = assembleArchive("bundle1", new String[] { "/bundles/update/update-bundle1", "/bundles/update/classes1" });
      Archive<?> assembly2 = assembleArchive("bundle2", new String[] { "/bundles/update/update-bundle102", "/bundles/update/classes2" });

      Bundle bundleA = installBundle(assembly1);
      Bundle bundleX = installBundle(assemblyx);
      try
      {
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

         bundleA.update(toVirtualFile(assembly2).openStream());
         assertBundleState(Bundle.ACTIVE, bundleA.getState());
         assertBundleState(Bundle.ACTIVE, bundleX.getState());
         assertEquals(Version.parseVersion("1.0.0"), bundleA.getVersion());
         // Assembly X depends on a package in the bundle, so don't update the packages yet.
         assertLoadClass(bundleA, ObjectA.class.getName());
         assertLoadClassFail(bundleA, ObjectA2.class.getName());
         assertLoadClass(bundleX, ObjectA.class.getName());
         assertLoadClassFail(bundleX, ObjectA2.class.getName());
         assertSame(cls, bundleX.loadClass(ObjectX.class.getName()));

         pa.refreshPackages(null);
         assertBundleState(Bundle.ACTIVE, bundleA.getState());
         assertBundleState(Bundle.ACTIVE, bundleX.getState());
         assertEquals(Version.parseVersion("1.0.2"), bundleA.getVersion());
         assertLoadClass(bundleA, ObjectA2.class.getName());
         assertLoadClassFail(bundleA, ObjectA.class.getName());
         assertLoadClass(bundleX, ObjectA2.class.getName());
         assertLoadClassFail(bundleX, ObjectA.class.getName());

         Class<?> cls2 = bundleX.loadClass(ObjectX.class.getName());
         assertNotSame("Should have loaded a new class", cls, cls2);

         int afterCount = systemContext.getBundles().length;
         assertEquals("Bundle count", beforeCount, afterCount);
      }
      finally
      {
         bundleX.uninstall();
         bundleA.uninstall();
      }
   }

   private Object getImportedFieldValue(Bundle bundleI) throws Exception
   {
      Class<?> iCls = bundleI.loadClass(Importing.class.getName());
      Object importing = iCls.newInstance();
      Field field = iCls.getDeclaredField("imported");
      return field.get(importing);
   }

   @Test
   @Ignore
   public void testRemoveUninstalledBundleOnRefreshPackages()
   {
   }

   @Test
   public void testResolveBundles() throws Exception
   {
      System.out.println("FIXME [JBOSGI-343] Comprehensive PackageAdmin test coverage");
   }
}
