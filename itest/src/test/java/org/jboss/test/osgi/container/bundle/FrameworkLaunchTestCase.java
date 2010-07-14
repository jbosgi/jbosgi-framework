/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.test.osgi.container.bundle;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.util.Properties;

import org.jboss.osgi.container.plugin.StartLevelPlugin;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.startlevel.StartLevel;

/**
 * Test framework bootstrap options.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class FrameworkLaunchTestCase extends OSGiFrameworkTest
{
   @BeforeClass
   public static void beforeClass()
   {
      // prevent framework creation
   }
   
   @Test
   public void frameworkStartStop() throws Exception
   {
      Framework framework = createFramework();
      assertNotNull("Framework not null", framework);
      
      assertBundleState(Bundle.INSTALLED, framework.getState());
      
      framework.start();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      framework.stop();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      framework.waitForStop(2000);
      assertBundleState(Bundle.RESOLVED, framework.getState());
   }

   @Test
   public void testDefaultInitialStartLevel() throws Exception
   {
      Framework framework = createFramework();
      framework.start();
      
      BundleContext bc = framework.getBundleContext();
      ServiceReference sref = bc.getServiceReference(StartLevel.class.getName());
      StartLevel sl = (StartLevel)bc.getService(sref);
      assertEquals(1, sl.getStartLevel());
   }

   @Test
   public void testNonDefaultInitialStartLevel() throws Exception
   {
      Properties oldProps = new Properties();
      oldProps.putAll(System.getProperties());
      try
      {
         System.setProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL, "51");
         Framework framework = createFramework();
         framework.start();

         BundleContext bc = framework.getBundleContext();
         ServiceReference sref = bc.getServiceReference(StartLevel.class.getName());
         StartLevel sl = (StartLevel)bc.getService(sref);
         assertEquals(51, sl.getStartLevel());
      }
      finally
      {
         System.setProperties(oldProps);
      }
   }

   @Test
   public void testOrderedStop() throws Exception
   {
      InputStream b1IS = createTestBundle("b1.jar",
            org.jboss.test.osgi.container.bundle.support.lifecycle1.Activator.class);
      InputStream b2IS = createTestBundle("b2.jar",
            org.jboss.test.osgi.container.bundle.support.lifecycle2.Activator.class);
      InputStream b3IS = createTestBundle("b3.jar",
            org.jboss.test.osgi.container.bundle.support.lifecycle3.Activator.class);

      Framework framework = createFramework();
      framework.start();

      BundleContext bc = framework.getBundleContext();
      ServiceReference sref = bc.getServiceReference(StartLevel.class.getName());
      StartLevelPlugin sl = (StartLevelPlugin)bc.getService(sref);

      Bundle b1 = bc.installBundle("b1", b1IS);
      assertEquals(Bundle.INSTALLED, b1.getState());
      b1.start();
      assertEquals(Bundle.ACTIVE, b1.getState());
            
      sl.setInitialBundleStartLevel(7);
      Bundle b2 = bc.installBundle("b2", b2IS);
      b2.start();
      assertEquals("Start level of 7 should have prevented the bundle from starting right now",
            Bundle.INSTALLED, b2.getState());
      
      sl.setInitialBundleStartLevel(5);
      Bundle b3 = bc.installBundle("b3", b3IS);
      b3.start();
      assertEquals("Start level of 5 should have prevented the bundle from starting right now",
            Bundle.INSTALLED, b3.getState());

      sl.increaseStartLevel(10);
      assertEquals(Bundle.ACTIVE, b1.getState());
      assertEquals(Bundle.ACTIVE, b2.getState());
      assertEquals(Bundle.ACTIVE, b3.getState());

      synchronized ("LifecycleOrdering")
      {
         assertEquals("start1start3start2", System.getProperty("LifecycleOrdering"));
      }

      framework.stop();
      framework.waitForStop(2000);

      synchronized ("LifecycleOrdering")
      {
         assertEquals("start1start3start2stop2stop3stop1", System.getProperty("LifecycleOrdering"));
      }
   }

   private InputStream createTestBundle(String name, final Class<? extends BundleActivator> activator)
   {
      final JavaArchive archive = ShrinkWrap.create(name, JavaArchive.class);
      archive.setManifest(new Asset()
      {
         @Override
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleManifestVersion(2);
            builder.addBundleActivator(activator);
            builder.addImportPackages("org.osgi.framework");
            return builder.openStream();
         }
      });
      archive.addClass(activator);
      InputStream bundleStream = archive.as(ZipExporter.class).exportZip();
      return bundleStream;
   }
}