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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.container.service.support.a.PA;
import org.jboss.test.osgi.container.service.support.b.Other;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
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
      System.out.println("FIXME [JBOSGI-336] Implement PackageAdmin.refreshPackages(Bundle[])");
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
