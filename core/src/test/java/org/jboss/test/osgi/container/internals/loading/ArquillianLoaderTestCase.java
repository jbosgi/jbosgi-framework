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
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.bundle.SystemBundle;
import org.jboss.osgi.container.plugin.SystemPackagesPlugin;
import org.jboss.osgi.container.plugin.internal.SystemPackagesPluginImpl;
import org.jboss.osgi.metadata.internal.OSGiManifestMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test the bundle content loader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public class ArquillianLoaderTestCase
{
   static ModuleClassLoader classLoader;
   
   @BeforeClass
   public static void beforeClass() throws Exception
   {
      // Mock the BundleManager to return the {@link FrameworkState}
      BundleManager bundleManager = Mockito.mock(BundleManager.class);
      FrameworkState frameworkState = Mockito.mock(FrameworkState.class);
      Mockito.when(bundleManager.getFrameworkState()).thenReturn(frameworkState);
      
      // Mock the BundleManager to return an instance of the {@link SystemPackagesPlugin}
      SystemPackagesPluginImpl sysPackagesPlugin = new SystemPackagesPluginImpl(bundleManager);
      Mockito.when(bundleManager.getPlugin(SystemPackagesPlugin.class)).thenReturn(sysPackagesPlugin);

      // Get the resolver module for the SystemBundle
      SystemBundle systemBundle = new SystemBundle(bundleManager);
      XModule resModule = systemBundle.getResolverModule();
      
      // Create the Framework module
      ModuleManager moduleManager = new ModuleManager(bundleManager);
      ModuleSpec moduleSpec = moduleManager.createFrameworkModule(resModule);
      moduleManager.createModule(moduleSpec, false);
      
      // Create the Arquillian resolver module 
      URL url = new OSGiTestHelper().getTestArchiveURL("bundles/arquillian-bundle.jar");
      VirtualFile rootFile = AbstractVFS.getRoot(url);
      
      Manifest manifest = VFSUtils.getManifest(rootFile);
      OSGiManifestMetaData metadata = new OSGiManifestMetaData(manifest);
      XModuleBuilder builder = XResolverFactory.getModuleBuilder();
      resModule = builder.createModule(1, metadata);

      // Add the Bundle-ClassPath to the root virtual files
      rootFile = BundleManager.aggregatedBundleClassPath(rootFile, metadata);
      
      // Create the ModuleSpec and the Module
      moduleSpec = moduleManager.createModuleSpec(resModule, rootFile);
      Module module = moduleManager.createModule(moduleSpec, false);
      classLoader = module.getClassLoader();
   }
   
   @Test
   public void testLoadBundleActivator() throws Exception
   {
      Class<?> result = classLoader.loadClass(ArquillianBundleActivator.class.getName());
      assertNotNull("ArquillianBundleActivator loaded", result);
   }
   
   @Test
   public void testLoadTestEnricher() throws Exception
   {
      Class<?> result = classLoader.loadClass(OSGiTestEnricher.class.getName());
      assertNotNull("OSGiTestEnricher loaded", result);
   }
}