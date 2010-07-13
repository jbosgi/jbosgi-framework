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
package org.jboss.test.osgi.container.servicemix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Test service-mix functionality.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jul-2010
 */
public class ServiceMixTestCase extends OSGiFrameworkTest
{
   @Test
   public void testDeployTestArchive() throws Exception
   {
      Bundle bundle = installBundle(getTestArchive());
      try
      {
         assertNotNull("Bundle not null", bundle);
         assertEquals("test", bundle.getSymbolicName());
         assertEquals(Version.parseVersion("1.0"), bundle.getVersion());
         
         bundle.start();
         assertBundleState(Bundle.ACTIVE, bundle.getState());
         
         bundle.stop();
         assertBundleState(Bundle.RESOLVED, bundle.getState());
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testDeployArchiveWithDeps() throws Exception
   {
      Bundle bundleA = installBundle(getArchiveWithDeps());
      try
      {
         assertNotNull("Bundle not null", bundleA);
         assertEquals("test-with-deps", bundleA.getSymbolicName());
         assertEquals(Version.parseVersion("1.0"), bundleA.getVersion());
         
         try
         {
            bundleA.start();
            fail("BundleException expected");
         }
         catch (BundleException ex)
         {
            // ignore
         }
         
         // Install the dependent bundle
         Bundle bundleB = installBundle(getTestArchive());
         try
         {
            assertBundleState(Bundle.INSTALLED, bundleB.getState());
            
            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());
         }
         finally
         {
            bundleB.uninstall();
         }
         
         bundleA.stop();
         assertBundleState(Bundle.RESOLVED, bundleA.getState());
      }
      finally
      {
         bundleA.uninstall();
      }
   }

   private JavaArchive getTestArchive()
   {
      JavaArchive archive = ShrinkWrap.create("test", JavaArchive.class);
      archive.addManifestResource(getResourceFile("servicemix/simple-module/META-INF/module.xml"));
      return archive;
   }

   private JavaArchive getArchiveWithDeps()
   {
      JavaArchive archive = ShrinkWrap.create("test-with-deps", JavaArchive.class);
      archive.addManifestResource(getResourceFile("servicemix/module-with-deps/META-INF/module.xml"));
      return archive;
   }
}
