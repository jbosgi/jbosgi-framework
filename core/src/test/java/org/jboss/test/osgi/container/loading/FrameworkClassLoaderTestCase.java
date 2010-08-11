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
package org.jboss.test.osgi.container.loading;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;

import javax.management.MBeanServer;
import javax.transaction.xa.XAResource;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.bundle.SystemBundle;
import org.jboss.osgi.container.plugin.SystemPackagesPlugin;
import org.jboss.osgi.container.plugin.internal.SystemPackagesPluginImpl;
import org.jboss.osgi.resolver.XModule;
import org.junit.BeforeClass;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineParser;
import org.mockito.Mockito;
import org.osgi.framework.BundleActivator;

/**
 * Test the bundle content loader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class FrameworkClassLoaderTestCase
{
   static ClassLoader classLoader;
   
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
      ModuleSpec moduleSpec = moduleManager.createFrameworkSpec(resModule);
      Module module = moduleManager.loadModule(moduleSpec.getModuleIdentifier());
      classLoader = module.getClassLoader();
   }
   
   @Test
   public void testLoadJavaClass() throws Exception
   {
      Class<?> result = classLoader.loadClass(HashMap.class.getName());
      assertNotNull("HashMap loaded", result);
      assertTrue("Is assignable", HashMap.class.isAssignableFrom(result));
   }
   
   @Test
   public void testLoadJavaXSuccess() throws Exception
   {
      Class<?> result = classLoader.loadClass(MBeanServer.class.getName());
      assertNotNull("MBeanServer loaded", result);
      assertTrue("Is assignable", MBeanServer.class.isAssignableFrom(result));
   }
   
   @Test
   public void testLoadJavaXFail() throws Exception
   {
      try
      {
         classLoader.loadClass(XAResource.class.getName());
         fail("ClassNotFoundException expected");
      }
      catch (ClassNotFoundException ex)
      {
         //expected
      }
   }
   
   @Test
   public void testLoadClassSuccess() throws Exception
   {
      Class<?> result = classLoader.loadClass(BundleActivator.class.getName());
      assertNotNull("BundleActivator loaded", result);
      assertTrue("Is assignable", BundleActivator.class.isAssignableFrom(result));
   }
   
   @Test
   public void testLoadClassFail() throws Exception
   {
      try
      {
         classLoader.loadClass(CmdLineParser.class.getName());
         fail("ClassNotFoundException expected");
      }
      catch (ClassNotFoundException ex)
      {
         //expected
      }
   }
}