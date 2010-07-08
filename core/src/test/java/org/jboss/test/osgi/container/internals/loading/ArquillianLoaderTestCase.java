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

import java.net.URL;
import java.util.jar.Manifest;

import org.jboss.arquillian.osgi.ArquillianBundleActivator;
import org.jboss.arquillian.testenricher.osgi.OSGiTestEnricher;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.loading.OSGiModuleClassLoader;
import org.jboss.osgi.metadata.internal.OSGiManifestMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * Test the bundle content loader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public class ArquillianLoaderTestCase
{
   private static Module module;
   private static VirtualFile rootFile;
   
   @BeforeClass
   public static void beforeClass() throws Exception
   {
      // Create the {@link ModuleLoader}
      ModuleManager moduleManager = new ModuleManager(Mockito.mock(BundleManager.class));
      
      // Add the framework module to the manager
      XModuleBuilder builder = XResolverFactory.getModuleBuilder();
      builder.createModule(0, Constants.SYSTEM_BUNDLE_SYMBOLICNAME, Version.emptyVersion);
      builder.addPackageCapability("org.osgi.framework", null, null);
      moduleManager.createFrameworkModule(builder.getModule());
      
      // Create the Arquillian resolver module 
      URL url = new OSGiTestHelper().getTestArchiveURL("bundles/arquillian-bundle.jar");
      rootFile = AbstractVFS.getRoot(url);
      
      Manifest manifest = VFSUtils.getManifest(rootFile);
      OSGiManifestMetaData metadata = new OSGiManifestMetaData(manifest);
      builder = XResolverFactory.getModuleBuilder();
      XModule resModule = builder.createModule(1, metadata);

      // Add the Bundle-ClassPath to the root virtual files
      rootFile = BundleManager.aggregatedBundleClassPath(rootFile, metadata);
      
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
      Class<?> result = loader.loadClass(ArquillianBundleActivator.class.getName());
      assertNotNull("Class loaded", result);
   }
   
   @Test
   public void testLoadTestEnricher() throws Exception
   {
      ClassLoader loader = new OSGiModuleClassLoader(module);
      Class<?> result = loader.loadClass(OSGiTestEnricher.class.getName());
      assertNotNull("Class loaded", result);
   }
}