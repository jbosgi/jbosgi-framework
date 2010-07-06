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
package org.jboss.test.osgi.msc.loading;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.jar.Manifest;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.msc.bundle.ModuleManager;
import org.jboss.osgi.msc.loading.OSGiModuleClassLoader;
import org.jboss.osgi.msc.metadata.internal.OSGiManifestMetaData;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.Archives;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleC.SimpleActivator;
import org.jboss.test.osgi.framework.simple.bundleC.SimpleService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.BundleActivator;

/**
 * Test the bundle content loader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class OSGiModuleClassLoaderTestCase 
{
   @Test
   public void testLoadClass() throws Exception
   {
      ClassLoader loader = new OSGiModuleClassLoader(module);
      Class<?> result = loader.loadClass(SimpleActivator.class.getName());
      assertNotNull("Class loaded", result);
      assertTrue("Is assignable", BundleActivator.class.isAssignableFrom(result));
   }
   
   private static Module module;
   private static VirtualFile rootFile;
   
   @BeforeClass
   public static void beforeClass() throws Exception
   {
      // Bundle-Version: 1.0.0
      // Bundle-SymbolicName: simple-bundle
      // Bundle-Activator: org.jboss.osgi.msc.framework.simple.bundle.SimpleActivator
      final JavaArchive archive = Archives.create("simple-bundle", JavaArchive.class);
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
            return builder.openStream();
         }
      });
      
      // [TODO] workaround for competing URLStreamHandlerFactory registration
      ModuleManager moduleManager = new ModuleManager();
      
      // Get the OSGiMetaData
      rootFile = OSGiTestHelper.toVirtualFile(archive);
      Manifest manifest = VFSUtils.getManifest(rootFile);
      OSGiMetaData metadata = new OSGiManifestMetaData(manifest);
      
      // Create the ModuleSpec and the Module
      ModuleSpec moduleSpec = moduleManager.createModuleSpec(metadata, rootFile);
      module = moduleManager.createModule(moduleSpec);
   }
   
   @AfterClass
   public static void afterClass() throws Exception
   {
      rootFile.close();
   }
}