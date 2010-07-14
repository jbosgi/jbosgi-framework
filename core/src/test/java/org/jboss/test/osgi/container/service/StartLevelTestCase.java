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
package org.jboss.test.osgi.container.service;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.Executor;

import org.jboss.osgi.container.plugin.internal.StartLevelPluginImpl;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.startlevel.StartLevel;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class StartLevelTestCase extends OSGiFrameworkTest
{
   @Test
   public void testStartLevel() throws Exception
   {
      BundleContext sc = getFramework().getBundleContext();
      ServiceReference sref = sc.getServiceReference(StartLevel.class.getName());
      StartLevel sls = (StartLevel)sc.getService(sref);
      setTestExecutor(sls);

      assertEquals(1, sls.getInitialBundleStartLevel());
      sls.setInitialBundleStartLevel(5);
      assertEquals(5, sls.getInitialBundleStartLevel());
      
      Bundle bundle = installBundle("b1", createTestBundle("bundle1"));
      assertEquals(Bundle.INSTALLED, bundle.getState());
      assertEquals(5, sls.getBundleStartLevel(bundle));
      bundle.start();
      assertEquals(Bundle.INSTALLED, bundle.getState());

      sls.setStartLevel(5);
      assertEquals(Bundle.ACTIVE, bundle.getState());
      
      sls.setStartLevel(4);
      assertEquals(Bundle.RESOLVED, bundle.getState());
      
      sls.setInitialBundleStartLevel(7);
      assertEquals(7, sls.getInitialBundleStartLevel());

      sls.setStartLevel(10);
      assertEquals(Bundle.ACTIVE, bundle.getState());
      
      Bundle bundle2 = installBundle("b2", createTestBundle("bundle2"));
      assertEquals(Bundle.INSTALLED, bundle2.getState());
      assertEquals(7, sls.getBundleStartLevel(bundle2));
      bundle2.start();
      assertEquals(Bundle.ACTIVE, bundle2.getState());

      sls.setBundleStartLevel(bundle2, 11);
      assertEquals(Bundle.RESOLVED, bundle2.getState());
      sls.setBundleStartLevel(bundle2, 9);
      assertEquals(Bundle.ACTIVE, bundle2.getState());

      sls.setStartLevel(1);
      assertEquals(Bundle.RESOLVED, bundle.getState());
      assertEquals(Bundle.RESOLVED, bundle2.getState());
   }

   @Test
   public void getFrameworkStartLevel() throws Exception
   {
      BundleContext sc = getFramework().getBundleContext();
      ServiceReference sref = sc.getServiceReference(StartLevel.class.getName());
      StartLevel sls = (StartLevel)sc.getService(sref);

      assertEquals(0, sls.getBundleStartLevel(getFramework()));
   }

   private void setTestExecutor(StartLevel sls) throws Exception
   {
      Field ef = StartLevelPluginImpl.class.getDeclaredField("executor");
      ef.setAccessible(true);
      ef.set(sls, new CurrentThreadExecutor());
   }

   private InputStream createTestBundle(String name)
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
            builder.addImportPackages("org.osgi.framework");
            return builder.openStream();
         }
      });
      InputStream bundleStream = archive.as(ZipExporter.class).exportZip();
      return bundleStream;
   }

   private static class CurrentThreadExecutor implements Executor
   {
      @Override
      public void execute(Runnable command)
      {
         command.run();
      }
   }
}
