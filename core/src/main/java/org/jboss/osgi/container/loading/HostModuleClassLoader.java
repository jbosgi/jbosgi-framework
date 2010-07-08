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
package org.jboss.osgi.container.loading;

import java.util.Collections;

import org.jboss.logging.Logger;
import org.jboss.modules.AssertionSetting;
import org.jboss.modules.ConcurrentClassLoader;
import org.jboss.modules.Module;
import org.jboss.modules.Module.Flag;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.BundleManager;

/**
 * A {@link ConcurrentClassLoader} that has OSGi semantics.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class HostModuleClassLoader extends ModuleClassLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(HostModuleClassLoader.class);

   public HostModuleClassLoader(BundleManager bundleManager, Module module, ModuleSpec moduleSpec)
   {
      super(module, Collections.<Flag> emptySet(), AssertionSetting.INHERIT, moduleSpec.getContentLoader());
   }

   @Override
   protected Class<?> findClass(String className, boolean exportsOnly) throws ClassNotFoundException
   {
      boolean traceEnabled = log.isTraceEnabled();
      if (traceEnabled)
         log.trace("Attempt to find class [" + className + "] in " + getModule() + " ...");

      Class<?> result = null;
      try
      {
         result = super.findClass(className, exportsOnly);
         if (result != null)
         {
            if (traceEnabled)
               log.trace("Found class [" + className + "] in " + getModule());
            return result;
         }
      }
      catch (ClassNotFoundException ex)
      {
         if (traceEnabled)
            log.trace("Cannot find class [" + className + "] in " + getModule());
      }

      throw new ClassNotFoundException(className);
   }
}
