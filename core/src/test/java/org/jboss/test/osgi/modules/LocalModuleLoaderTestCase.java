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

import static org.junit.Assert.assertNotNull;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.junit.Test;
import org.osgi.framework.BundleActivator;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Test low level modules use cases.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 28-Sep-2010
 */
public class LocalModuleLoaderTestCase 
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
}
