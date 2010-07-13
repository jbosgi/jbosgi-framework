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

import java.io.InputStream;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.container.servicemix.bundleA.BundleActivatorA;
import org.jboss.test.osgi.container.servicemix.bundleA.BundleServiceA;
import org.jboss.test.osgi.container.servicemix.bundleB.BundleActivatorB;
import org.jboss.test.osgi.container.servicemix.bundleB.BundleServiceB;
import org.jboss.test.osgi.container.servicemix.moduleA.ModuleActivatorA;
import org.jboss.test.osgi.container.servicemix.moduleA.ModuleServiceA;
import org.jboss.test.osgi.container.servicemix.moduleB.ModuleActivatorB;
import org.jboss.test.osgi.container.servicemix.moduleB.ModuleServiceB;
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
   public void testSimpleModule() throws Exception
   {
      Bundle moduleA = installBundle(getModuleA());
      try
      {
         assertNotNull("Bundle not null", moduleA);
         assertEquals("moduleA", moduleA.getSymbolicName());
         assertEquals(Version.parseVersion("1.0"), moduleA.getVersion());
         
         moduleA.start();
         assertBundleState(Bundle.ACTIVE, moduleA.getState());
         
         moduleA.stop();
         assertBundleState(Bundle.RESOLVED, moduleA.getState());
      }
      finally
      {
         moduleA.uninstall();
      }
   }

   @Test
   public void testModuleDependsOnBundle() throws Exception
   {
      Bundle moduleB = installBundle(getModuleB());
      try
      {
         assertNotNull("Bundle not null", moduleB);
         assertEquals("moduleB", moduleB.getSymbolicName());
         assertEquals(Version.parseVersion("1.0"), moduleB.getVersion());
         
         try
         {
            moduleB.start();
            fail("BundleException expected");
         }
         catch (BundleException ex)
         {
            // ignore
         }
         
         // Install the dependent bundle
         Bundle bundleB = installBundle(getBundleB());
         try
         {
            assertBundleState(Bundle.INSTALLED, bundleB.getState());
            
            moduleB.start();
            assertBundleState(Bundle.ACTIVE, moduleB.getState());
         }
         finally
         {
            bundleB.uninstall();
         }
         
         moduleB.stop();
         assertBundleState(Bundle.RESOLVED, moduleB.getState());
      }
      finally
      {
         moduleB.uninstall();
      }
   }

   @Test
   public void testBundleDependsOnModule() throws Exception
   {
      Bundle bundleA = installBundle(getBundleA());
      try
      {
         assertNotNull("Bundle not null", bundleA);
         assertEquals("bundleA", bundleA.getSymbolicName());
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
         Bundle moduleA = installBundle(getModuleA());
         try
         {
            assertBundleState(Bundle.INSTALLED, moduleA.getState());
            
            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());
         }
         finally
         {
            moduleA.uninstall();
         }
         
         bundleA.stop();
         assertBundleState(Bundle.RESOLVED, bundleA.getState());
      }
      finally
      {
         bundleA.uninstall();
      }
   }
   
   private JavaArchive getBundleA()
   {
      // Bundle-SymbolicName: bundleA
      // Bundle-Activator: org.jboss.test.osgi.container.servicemix.bundleA.BundleActivatorA
      // Require-Bundle: moduleA;bundle-version:=1.0.0
      final JavaArchive archive = ShrinkWrap.create("bundleA", JavaArchive.class);
      archive.addClasses(BundleActivatorA.class, BundleServiceA.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleVersion("1.0.0");
            builder.addBundleActivator(BundleActivatorA.class);
            builder.addRequireBundle("moduleA;bundle-version:=1.0.0");
            return builder.openStream();
         }
      });
      return archive;
   }
   
   private JavaArchive getBundleB()
   {
      // Bundle-Version: 1.0.0
      // Bundle-SymbolicName: servicemix.bundleB
      // Bundle-Activator: org.jboss.test.osgi.container.servicemix.bundleB.BundleActivatorB
      final JavaArchive archive = ShrinkWrap.create("servicemix.bundleB", JavaArchive.class);
      archive.addClasses(BundleActivatorB.class, BundleServiceB.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleVersion("1.0.0");
            builder.addBundleActivator(BundleActivatorB.class);
            return builder.openStream();
         }
      });
      return archive;
   }
   
   private JavaArchive getModuleA()
   {
      JavaArchive archive = ShrinkWrap.create("moduleA", JavaArchive.class);
      archive.addManifestResource(getResourceFile("servicemix/moduleA/META-INF/module.xml"));
      archive.addClasses(ModuleActivatorA.class, ModuleServiceA.class);
      return archive;
   }

   private JavaArchive getModuleB()
   {
      JavaArchive archive = ShrinkWrap.create("moduleB", JavaArchive.class);
      archive.addManifestResource(getResourceFile("servicemix/moduleB/META-INF/module.xml"));
      archive.addClasses(ModuleActivatorB.class, ModuleServiceB.class);
      return archive;
   }
}
