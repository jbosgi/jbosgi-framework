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
package org.jboss.test.osgi.framework.launch;


import java.io.InputStream;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleC.SimpleActivator;
import org.jboss.test.osgi.framework.simple.bundleC.SimpleService;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;

/**
 * Test persistent bundle startup
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Oct-2010
 */
public class PersistentBundleStartupTestCase extends OSGiFrameworkTest
{
   @BeforeClass
   public static void beforeClass()
   {
      // prevent framework creation
   }
   
   @Test
   public void testInstalledBundle() throws Exception
   {
      Framework framework = createFramework();
      framework.start();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      JavaArchive archive = getBundleArchive();
      BundleContext context = framework.getBundleContext();
      Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
      assertBundleState(Bundle.INSTALLED, bundle.getState());
      
      framework.stop();
      framework.waitForStop(2000);
      assertBundleState(Bundle.RESOLVED, framework.getState());
      assertBundleState(Bundle.INSTALLED, bundle.getState());
      
      // Restart the Framework
      framework.start();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      context = framework.getBundleContext();
      bundle = context.getBundle(bundle.getBundleId());
      assertBundleState(Bundle.INSTALLED, bundle.getState());

      framework.stop();
      framework.waitForStop(2000);
      assertBundleState(Bundle.RESOLVED, framework.getState());
   }

   @Test
   public void testActiveBundle() throws Exception
   {
      Framework framework = createFramework();
      framework.start();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      JavaArchive archive = getBundleArchive();
      BundleContext context = framework.getBundleContext();
      Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
      assertBundleState(Bundle.INSTALLED, bundle.getState());
      
      bundle.start();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      framework.stop();
      framework.waitForStop(2000);
      assertBundleState(Bundle.RESOLVED, framework.getState());
      assertBundleState(Bundle.RESOLVED, bundle.getState());
      
      // Restart the Framework
      framework.start();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      context = framework.getBundleContext();
      bundle = context.getBundle(bundle.getBundleId());
      assertBundleState(Bundle.ACTIVE, bundle.getState());

      framework.stop();
      framework.waitForStop(2000);
      assertBundleState(Bundle.RESOLVED, framework.getState());
   }

   private JavaArchive getBundleArchive()
   {
      // Bundle-Version: 1.0.0
      // Bundle-SymbolicName: simple-bundle
      // Bundle-Activator: org.jboss.osgi.msc.framework.simple.bundle.SimpleActivator
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
      archive.addClasses(SimpleService.class, SimpleActivator.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleVersion("1.0.0");
            builder.addBundleActivator(SimpleActivator.class);
            builder.addImportPackages(BundleActivator.class);
            return builder.openStream();
         }
      });
      return archive;
   }
}