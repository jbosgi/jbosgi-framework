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

import java.util.HashMap;
import java.util.Map;

import org.jboss.modules.ClassifyingModuleLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleDependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.internal.ModuleManagerPluginImpl;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Version;

/**
 * Test the {@link ModuleLoader} hirarchy.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 30-Sep-2010
 */
public class ModuleLoaderTestCase extends ModulesTestBase
{
   private ModuleManagerPlugin moduleManager;
   private ModuleLoaderSupport defaultLoader;
   private ModuleLoaderSupport jbosgiLoader;

   @Before
   public void setUp()
   {
      super.setUp();
      defaultLoader = (ModuleLoaderSupport)Module.getCurrentLoader();
      jbosgiLoader = new ModuleLoaderSupport("jbosgi");
      Module.setModuleLoaderSelector(new ModuleLoaderSelector()
      {
         @Override
         public ModuleLoader getCurrentLoader()
         {
            Map<String, ModuleLoader> delegates = new HashMap<String, ModuleLoader>();
            delegates.put("jbosgi", jbosgiLoader);
            return new ClassifyingModuleLoader(delegates, defaultLoader);
         }
      });
      BundleManager bundleManager = Mockito.mock(BundleManager.class);
      moduleManager = new ModuleManagerPluginImpl(bundleManager);
   }

   @Test
   public void testBundleIdentifier() throws Exception
   {
      XResolverFactory factory = XResolverFactory.getInstance();
      XModuleBuilder builder = factory.newModuleBuilder();
      builder.createModule("bundleA", Version.parseVersion("1.0.0"), 0);
      
      ModuleIdentifier bundleId = moduleManager.getModuleIdentifier(builder.getModule());
      assertEquals("jbosgi.bundleA", bundleId.getName());
      assertEquals("1.0.0-rev0", bundleId.getSlot());
      
      ModuleSpec.Builder specBuilder = ModuleSpec.build(bundleId);
      ModuleSpec bundleSpec = specBuilder.create();
      jbosgiLoader.addModuleSpec(bundleSpec);
      
      Module bundle = Module.getModule(bundleId);
      assertEquals(jbosgiLoader, bundle.getModuleLoader());
   }

   @Test
   public void testBundleDependendsOnModule() throws Exception
   {
      ModuleIdentifier moduleId = ModuleIdentifier.create("moduleA");
      ModuleSpec.Builder specBuilder = ModuleSpec.build(moduleId);
      ModuleSpec moduleSpec = specBuilder.create();
      defaultLoader.addModuleSpec(moduleSpec);
      
      XResolverFactory factory = XResolverFactory.getInstance();
      XModuleBuilder builder = factory.newModuleBuilder();
      builder.createModule("bundleA", Version.parseVersion("1.0.0"), 0);
      
      ModuleIdentifier bundleId = moduleManager.getModuleIdentifier(builder.getModule());
      specBuilder = ModuleSpec.build(bundleId);
      ModuleDependencySpec.Builder depBuilder = ModuleDependencySpec.build(moduleId);
      specBuilder.addModuleDependency(depBuilder.create());
      ModuleSpec bundleSpec = specBuilder.create();
      jbosgiLoader.addModuleSpec(bundleSpec);

      Module bundle = Module.getModule(bundleId);
      assertEquals(jbosgiLoader, bundle.getModuleLoader());
      
      Module module = Module.getModule(moduleId);
      assertEquals(defaultLoader, module.getModuleLoader());
   }

   @Test
   public void testBundleDependendsOnBundle() throws Exception
   {
      XResolverFactory factory = XResolverFactory.getInstance();
      XModuleBuilder builder = factory.newModuleBuilder();
      builder.createModule("bundleA", Version.parseVersion("1.0.0"), 0);
      ModuleIdentifier bundleIdA = moduleManager.getModuleIdentifier(builder.getModule());
      
      builder = factory.newModuleBuilder();
      builder.createModule("bundleB", Version.parseVersion("1.0.0"), 0);
      ModuleIdentifier bundleIdB = moduleManager.getModuleIdentifier(builder.getModule());
      
      ModuleSpec.Builder specBuilderA = ModuleSpec.build(bundleIdA);
      ModuleSpec bundleSpecA = specBuilderA.create();
      jbosgiLoader.addModuleSpec(bundleSpecA);
      
      ModuleSpec.Builder specBuilderB = ModuleSpec.build(bundleIdB);
      ModuleDependencySpec.Builder depBuilder = ModuleDependencySpec.build(bundleIdA);
      specBuilderB.addModuleDependency(depBuilder.create());
      ModuleSpec bundleSpecB = specBuilderB.create();
      jbosgiLoader.addModuleSpec(bundleSpecB);
      
      Module bundleA = Module.getModule(bundleIdA);
      assertEquals(jbosgiLoader, bundleA.getModuleLoader());
      
      Module bundleB = Module.getModule(bundleIdB);
      assertEquals(jbosgiLoader, bundleB.getModuleLoader());
   }

   @Test
   public void testModuleDependendsOnModule() throws Exception
   {
      ModuleIdentifier moduleIdA = ModuleIdentifier.create("moduleA");
      ModuleSpec.Builder specBuilderA = ModuleSpec.build(moduleIdA);
      ModuleSpec moduleSpecA = specBuilderA.create();
      defaultLoader.addModuleSpec(moduleSpecA);
      
      ModuleIdentifier moduleIdB = ModuleIdentifier.create("moduleB");
      ModuleSpec.Builder specBuilderB = ModuleSpec.build(moduleIdB);
      ModuleDependencySpec.Builder depBuilder = ModuleDependencySpec.build(moduleIdA);
      specBuilderB.addModuleDependency(depBuilder.create());
      ModuleSpec moduleSpecB = specBuilderB.create();
      defaultLoader.addModuleSpec(moduleSpecB);

      Module moduleA = Module.getModule(moduleIdA);
      assertEquals(defaultLoader, moduleA.getModuleLoader());

      Module moduleB = Module.getModule(moduleIdB);
      assertEquals(defaultLoader, moduleB.getModuleLoader());
   }

   @Test
   public void testModuleDependendsOnBundle() throws Exception
   {
      XResolverFactory factory = XResolverFactory.getInstance();
      XModuleBuilder builder = factory.newModuleBuilder();
      builder.createModule("bundleA", Version.parseVersion("1.0.0"), 0);
      ModuleIdentifier bundleIdA = moduleManager.getModuleIdentifier(builder.getModule());
      
      ModuleSpec.Builder specBuilderA = ModuleSpec.build(bundleIdA);
      ModuleSpec bundleSpecA = specBuilderA.create();
      jbosgiLoader.addModuleSpec(bundleSpecA);
      
      ModuleIdentifier moduleIdB = ModuleIdentifier.create("moduleB");
      ModuleSpec.Builder specBuilderB = ModuleSpec.build(moduleIdB);
      ModuleDependencySpec.Builder depBuilder = ModuleDependencySpec.build(bundleIdA);
      specBuilderB.addModuleDependency(depBuilder.create());
      ModuleSpec moduleSpecB = specBuilderB.create();
      defaultLoader.addModuleSpec(moduleSpecB);

      Module moduleB = Module.getModule(moduleIdB);
      assertEquals(defaultLoader, moduleB.getModuleLoader());

      Module bundleA = Module.getModule(bundleIdA);
      assertEquals(jbosgiLoader, bundleA.getModuleLoader());
   }
}
