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
package org.jboss.test.osgi.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.FrameworkState;
import org.jboss.osgi.framework.plugin.ResolverPlugin;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.testing.OSGiTest;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Test low level modules use cases.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 28-Sep-2010
 */
public class LocalModuleLoaderTestCase extends OSGiTest
{
   @Test
   public void testCoreModule() throws Exception
   {
      ModuleIdentifier identifier = ModuleIdentifier.create("org.osgi.core");
      ModuleLoader localLoader = Module.getCurrentLoader();
      Module module = localLoader.loadModule(identifier);
      assertNotNull("Core module available", module);
      
      ModuleClassLoader classLoader = module.getClassLoader();
      Class<?> clazz = classLoader.loadClass(BundleActivator.class.getName());
      assertNotNull("BundleActivator loaded", clazz);
   }
   
   @Test
   public void testCompendiumModule() throws Exception
   {
      ModuleIdentifier identifier = ModuleIdentifier.create("org.osgi.compendium");
      ModuleLoader localLoader = Module.getCurrentLoader();
      Module module = localLoader.loadModule(identifier);
      assertNotNull("Compendium module available", module);

      ModuleClassLoader classLoader = module.getClassLoader();
      Class<?> clazz = classLoader.loadClass(ServiceTracker.class.getName());
      assertNotNull("ServiceTracker loaded", clazz);
   }
   
   @Test
   public void testCompendiumInstall() throws Exception
   {
      Map<String, Object> props = new HashMap<String, Object>();
      props.put("org.osgi.framework.storage", new File("target/osgi-store").getAbsolutePath());
      
      BundleManager bundleManager = new BundleManager(props);
      FrameworkState framework = bundleManager.getFrameworkState();
      assertNotNull("Framework not null", framework);
      
      framework.start();
      BundleContext context = framework.getBundleContext();
      assertNotNull("Framework active", context);

      Bundle[] bundles = context.getBundles();
      assertEquals("System bundle available", 1, bundles.length);
      assertEquals("System bundle id", 0, bundles[0].getBundleId());

      ResolverPlugin resolverPlugin = bundleManager.getPlugin(ResolverPlugin.class);
      XResolver resolver = resolverPlugin.getResolver();
      Set<XModule> modules = resolver.getModules();
      assertEquals("System module available", 1, modules.size());
      
      ModuleIdentifier identifier = ModuleIdentifier.create("org.osgi.compendium");
      Bundle bundle = bundleManager.installBundle(identifier);
      assertNotNull("Bundle not null", bundle);
      assertBundleState(Bundle.INSTALLED, bundle.getState());
      assertEquals("Bundle id", 1, bundle.getBundleId());
      
      assertLoadClass(bundle, ServiceTracker.class.getName());
      assertBundleState(Bundle.RESOLVED, bundle.getState());
      
      bundle.start();
      assertBundleState(Bundle.ACTIVE, bundle.getState());
      
      bundle.stop();
      assertBundleState(Bundle.RESOLVED, bundle.getState());
      
      bundle.uninstall();
      assertNull("Bundle null", context.getBundle(1));
   }
}
