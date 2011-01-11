/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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
package org.jboss.test.osgi.framework.loading;

import static org.junit.Assert.assertTrue;

import java.net.URLStreamHandlerFactory;
import java.util.ServiceLoader;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.framework.bundle.SystemBundle;
import org.jboss.osgi.framework.plugin.internal.URLHandlerFactory;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class FrameworkServiceLoaderTestCase extends OSGiFrameworkTest
{
   @Test
   public void testServiceLoader() throws Exception
   {
      // The ModularURLStreamHandlerFactory follows a pattern similar to this.
      BundleContext ctx = getSystemContext();
      Bundle sb = ctx.getBundle();
      SystemBundle systemBundle = SystemBundle.assertBundleState(sb);
      ModuleIdentifier systemModuleID = systemBundle.getBundleManager().getSystemBundle().getModuleIdentifier();
      Module systemModule = systemBundle.getBundleManager().getSystemModuleLoader().loadModule(systemModuleID);
      ServiceLoader<URLStreamHandlerFactory> sl = systemModule.loadService(URLStreamHandlerFactory.class);

      boolean factoryFound = false;
      for (URLStreamHandlerFactory f : sl)
      {
         if (f instanceof URLHandlerFactory)
            factoryFound = true;
      }

      assertTrue("Expected an instance of " + URLHandlerFactory.class.getName(), factoryFound);
   }
}
