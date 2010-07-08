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
package org.jboss.test.osgi.container.internals.loading;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.jar.Manifest;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.loading.OSGiModuleClassLoader;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.container.simple.bundleC.SimpleActivator;
import org.jboss.test.osgi.container.simple.bundleC.SimpleService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.log.LogService;

/**
 * Test the bundle content loader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class OSGiModuleClassLoaderTestCase 
{
   private static Module module;
   private static VirtualFile rootFile;
   
   @BeforeClass
   public static void beforeClass() throws Exception
   {
      // Bundle-Version: 1.0.0
      // Bundle-SymbolicName: simple-bundle
      // Bundle-Activator: org.jboss.osgi.msc.framework.simple.bundle.SimpleActivator
      final JavaArchive archive = ShrinkWrap.create("simple-bundle", JavaArchive.class);
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
      
      // Create the {@link ModuleLoader}
      ModuleManager moduleManager = new ModuleManager(Mockito.mock(BundleManager.class));
      
      // Add the framework module to the manager
      XModuleBuilder builder = XResolverFactory.getModuleBuilder();
      builder.createModule(0, Constants.SYSTEM_BUNDLE_SYMBOLICNAME, Version.emptyVersion);
      builder.addPackageCapability("org.osgi.framework", null, null);
      moduleManager.createFrameworkModule(builder.getModule());
      
      // Create the test module 
      rootFile = OSGiTestHelper.toVirtualFile(archive);
      Manifest manifest = VFSUtils.getManifest(rootFile);
      builder = XResolverFactory.getModuleBuilder();
      XModule resModule = builder.createModule(1, manifest);
      
      // Create the ModuleSpec and the Module
      ModuleSpec moduleSpec = moduleManager.createModuleSpec(resModule, rootFile);
      module = moduleManager.createModule(moduleSpec, false);
   }
   
   @AfterClass
   public static void afterClass() throws Exception
   {
      rootFile.close();
   }

   @Test
   public void testLoadBundleActivator() throws Exception
   {
      ClassLoader loader = new OSGiModuleClassLoader(module);
      Class<?> result = loader.loadClass(SimpleActivator.class.getName());
      assertNotNull("Class loaded", result);
      assertTrue("Is assignable", BundleActivator.class.isAssignableFrom(result));
   }

   @Test
   public void testLoadLogServiceFail() throws Exception
   {
      ClassLoader loader = new OSGiModuleClassLoader(module);
      try
      {
         loader.loadClass(LogService.class.getName());
         fail("ClassNotFoundException expected");
      }
      catch (ClassNotFoundException ex)
      {
         // expected
      }
   }
}