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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.PathFilter;
import org.jboss.modules.PathFilters;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;

/**
 * Test low level modules use cases.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 15-Sep-2010
 */
public abstract class ModulesTestBase
{
   private ModuleLoaderSupport moduleLoader;
   private ModuleLoader currentLoader;

   @Before
   public void setUp()
   {
      currentLoader = Module.getCurrentLoader();
      moduleLoader = new ModuleLoaderSupport();
      Module.setModuleLoaderSelector(new ModuleLoaderSelector()
      {
         @Override
         public ModuleLoader getCurrentLoader()
         {
            return moduleLoader;
         }
      });
   }

   @After
   public void tearDown()
   {
      Module.setModuleLoaderSelector(new ModuleLoaderSelector()
      {
         @Override
         public ModuleLoader getCurrentLoader()
         {
            return currentLoader;
         }
      });
   }

   protected void addModuleSpec(ModuleSpec moduleSpec)
   {
      moduleLoader.addModuleSpec(moduleSpec);
   }

   protected Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      return moduleLoader.loadModule(identifier);
   }

   protected PathFilter getPathFilter(Class<?>... classes)
   {
      Set<String> paths = getFilterPaths(classes);
      return PathFilters.in(paths);
   }

   protected Set<String> getFilterPaths(Class<?>... classes)
   {
      Set<String> paths = new HashSet<String>();
      for (Class<?> clazz : classes)
      {
         paths.add(clazz.getPackage().getName().replace('.', '/'));
      }
      return Collections.unmodifiableSet(paths);
   }

   protected void assertLoadClass(ModuleIdentifier loaderId, String className) throws Exception
   {
      Class<?> clazz = Module.loadClass(loaderId, className);
      assertNotNull(clazz);
   }

   protected void assertLoadClass(ModuleIdentifier loaderId, String className, ModuleIdentifier exporterId) throws Exception
   {
      Class<?> clazz = Module.loadClass(loaderId, className);
      assertEquals(loadModule(exporterId).getClassLoader(), clazz.getClassLoader());
   }

   protected void assertLoadClassFails(ModuleIdentifier loader, String className) throws Exception
   {
      try
      {
         Module.loadClass(loader, className);
         fail("ClassNotFoundException expected");
      }
      catch (ClassNotFoundException ex)
      {
         // expected
      }
   }

   protected VirtualFile toVirtualFile(JavaArchive archive) throws IOException
   {
      return OSGiTestHelper.toVirtualFile(archive);
   }

   static class ModuleLoaderSupport extends ModuleLoader
   {
      private Map<ModuleIdentifier, ModuleSpec> modules = new HashMap<ModuleIdentifier, ModuleSpec>();

      void addModuleSpec(ModuleSpec moduleSpec)
      {
         modules.put(moduleSpec.getModuleIdentifier(), moduleSpec);
      }

      @Override
      protected ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException
      {
         ModuleSpec moduleSpec = modules.get(identifier);
         return moduleSpec;
      }
   }
}
