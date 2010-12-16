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
package org.jboss.osgi.framework.loading;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

/**
 * The {@link ModuleClassLoader} for a bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Dec-2010
 */
public class BundleModuleClassLoader extends ModuleClassLoader implements BundleReference
{
   private AbstractBundle bundleState;
   
   private BundleModuleClassLoader(AbstractBundle bundleState, Configuration configuration)
   {
      super(configuration);
      this.bundleState = bundleState;
   }

   @Override
   public Bundle getBundle()
   {
      return bundleState.getBundleWrapper();
   }
   
   public static class Factory implements ModuleClassLoaderFactory
   {
      private AbstractBundle bundleState;
      public Factory(AbstractBundle bundleState)
      {
         this.bundleState = bundleState;
      }

      @Override
      public ModuleClassLoader create(Configuration configuration)
      {
         return new BundleModuleClassLoader(bundleState, configuration);
      }
   }
}
