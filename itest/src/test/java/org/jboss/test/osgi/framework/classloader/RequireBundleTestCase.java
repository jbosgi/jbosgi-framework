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

// 

import static org.junit.Assert.fail;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.b.B;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * RequireBundleTest.
 *
 * TODO test security
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author thomas.diesler@jboss.com
 * @version $Revision: 1.1 $
 */
public class RequireBundleTestCase extends OSGiFrameworkTest
{
   @Test
   public void testSimpleRequireBundle() throws Exception
   {
      // Bundle-SymbolicName: classloader.bundleA
      // Export-Package: org.jboss.test.osgi.framework.classloader.support.a;version=1.0.0;test=x
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());
         
         // Bundle-SymbolicName: classloader.bundleB
         // Require-Bundle: classloader.bundleA
         Archive<?> assemblyB = assembleArchive("simplerequirebundleA", "/bundles/classloader/simplerequirebundleA", B.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            assertLoadClass(bundleB, B.class.getName(), bundleB);
            assertLoadClass(bundleB, A.class.getName(), bundleA);
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
   public void testSimpleRequireBundleFails() throws Exception
   {
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());
         Archive<?> assemblyB = assembleArchive("simplerequirebundlefails", "/bundles/classloader/simplerequirebundlefails", B.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            fail("Should not be here!");
         }
         catch (BundleException ex)
         {
            // expected
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
   public void testVersionRequireBundle() throws Exception
   {
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());
         Archive<?> assemblyB = assembleArchive("versionrequirebundleA", "/bundles/classloader/versionrequirebundleA", B.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            assertLoadClass(bundleB, A.class.getName(), bundleA);
            assertLoadClass(bundleB, B.class.getName(), bundleB);
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
   public void testVersionRequireBundleFails() throws Exception
   {
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());
         Archive<?> assemblyB = assembleArchive("versionrequirebundlefails", "/bundles/classloader/versionrequirebundlefails", B.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            fail("Should not be here!");
         }
         catch (BundleException rte)
         {
            // expected
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
   public void testOptionalRequireBundle() throws Exception
   {
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());
         Archive<?> assemblyB = assembleArchive("optionalrequirebundleA", "/bundles/classloader/optionalrequirebundleA", B.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            assertLoadClass(bundleB, A.class.getName(), bundleA);
            assertLoadClass(bundleB, B.class.getName(), bundleB);
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
   public void testOptionalRequireBundleFails() throws Exception
   {
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());
         Archive<?> assemblyB = assembleArchive("optionalrequirebundlefails", "/bundles/classloader/optionalrequirebundlefails", B.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            assertLoadClassFail(bundleB, A.class.getName());
            assertLoadClass(bundleB, B.class.getName());
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
   public void testReExportRequireBundle() throws Exception
   {
      //Bundle-Name: BundleA
      //Bundle-Version: 1.0.0
      //Bundle-SymbolicName: classloader.bundleA;test=x
      //Export-Package: org.jboss.test.osgi.framework.classloader.support.a;version=1.0.0;test=x
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);

      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());

         //Bundle-Name: BundleB
         //Bundle-SymbolicName: classloader.bundleB
         //Require-Bundle: classloader.bundleA;visibility:=reexport
         //Export-Package: org.jboss.test.osgi.framework.classloader.support.b
         Archive<?> assemblyB = assembleArchive("reexportrequirebundleA", "/bundles/classloader/reexportrequirebundleA", B.class);
         Bundle bundleB = installBundle(assemblyB);

         try
         {
            bundleB.start();
            assertLoadClass(bundleB, A.class.getName(), bundleA);
            assertLoadClass(bundleB, B.class.getName(), bundleB);

            //Bundle-Name: BundleC
            //Bundle-SymbolicName: classloader.bundleC
            //Require-Bundle: classloader.bundleB
            Archive<?> assemblyC = assembleArchive("reexportrequirebundleB", "/bundles/classloader/reexportrequirebundleB");
            Bundle bundleC = installBundle(assemblyC);

            try
            {
               assertLoadClass(bundleC, A.class.getName(), bundleA);
               assertLoadClass(bundleC, B.class.getName(), bundleB);
            }
            finally
            {
               bundleC.uninstall();
            }
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
   public void testNoReExportRequireBundle() throws Exception
   {
      // Bundle-SymbolicName: classloader.bundleA
      // Export-Package: org.jboss.test.osgi.framework.classloader.support.a;version=1.0.0;test=x
      Archive<?> assemblyA = assembleArchive("bundleA", "/bundles/classloader/bundleA", A.class);
      Bundle bundleA = installBundle(assemblyA);
      try
      {
         bundleA.start();
         assertLoadClass(bundleA, A.class.getName());
         
         // Bundle-SymbolicName: classloader.bundleB
         // Require-Bundle: classloader.bundleA
         // Export-Package: org.jboss.test.osgi.framework.classloader.support.b
         Archive<?> assemblyB = assembleArchive("noreexportrequirebundleA", "/bundles/classloader/noreexportrequirebundleA", B.class);
         Bundle bundleB = installBundle(assemblyB);
         try
         {
            bundleB.start();
            assertLoadClass(bundleB, A.class.getName(), bundleA);
            assertLoadClass(bundleB, B.class.getName(), bundleB);
            
            // Bundle-SymbolicName: classloader.bundleC
            // Require-Bundle: classloader.bundleB
            Archive<?> assemblyC = assembleArchive("reexportrequirebundleB", "/bundles/classloader/reexportrequirebundleB");
            Bundle bundleC = installBundle(assemblyC);
            try
            {
               assertLoadClassFail(bundleC, A.class.getName());
               assertLoadClass(bundleC, B.class.getName(), bundleB);
            }
            finally
            {
               bundleC.uninstall();
            }
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
}
