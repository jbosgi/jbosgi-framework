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

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.osgi.msc.bundle.BundleManager;
import org.jboss.osgi.msc.bundle.ModuleManager;
import org.jboss.osgi.resolver.XModule;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

/**
 * Test the bundle content loader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class FrameworkModuleTestCase
{
   @Test
   public void testClassLoad() throws Exception
   {
      XModule resModule = Mockito.mock(XModule.class);
      Mockito.when(resModule.getName()).thenReturn(Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
      Mockito.when(resModule.getVersion()).thenReturn(Version.emptyVersion);
      
      ModuleManager moduleManager = new ModuleManager(Mockito.mock(BundleManager.class));
      Module module = moduleManager.createFrameworkModule(resModule);
      ModuleClassLoader classLoader = module.getClassLoader();
      Class<?> result = classLoader.loadClass(BundleActivator.class.getName());
      assertNotNull("BundleActivator loaded", result);
      assertTrue("Is assignable", BundleActivator.class.isAssignableFrom(result));
   }
}