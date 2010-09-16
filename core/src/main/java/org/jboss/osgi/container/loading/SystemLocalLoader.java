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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Resource;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.spi.NotImplementedException;

/**
 * A {@link LocalLoader} that delegates to the system class loader.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Sep-2010
 */
public class SystemLocalLoader implements LocalLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(SystemLocalLoader.class);

   private final ClassLoader systemClassLoader;
   private final Set<String> exportedPaths;

   public SystemLocalLoader(Set<String> exportedPaths)
   {
      if (exportedPaths == null)
         throw new IllegalArgumentException("Null loaderPaths");

      this.exportedPaths = exportedPaths;
      this.systemClassLoader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>()
      {
         public ClassLoader run()
         {
            return ClassLoader.getSystemClassLoader();
         }
      });
   }

   public Set<String> getExportedPaths()
   {
      return Collections.unmodifiableSet(exportedPaths);
   }

   @Override
   public Class<?> loadClassLocal(String className, boolean exportOnly)
   {
      boolean traceEnabled = log.isTraceEnabled();
      if (traceEnabled)
         log.trace("Attempt to find system class [" + className + "] ...");

      String path = ModuleManager.getPathFromClassName(className);
      if (exportedPaths.contains(path))
      {
         Class<?> result = null;
         try
         {
            result = loadSystemClass(className);
            if (traceEnabled)
               log.trace("Found system class [" + className + "]");
            return result;
         }
         catch (ClassNotFoundException ex)
         {
            if (traceEnabled)
               log.trace("Cannot find system class [" + className + "]");
         }
      }
      else
      {
         if (traceEnabled)
            log.trace("Cannot find filtered class [" + className + "]");
      }

      return null;
   }

   public Class<?> loadSystemClass(String className) throws ClassNotFoundException
   {
      return systemClassLoader.loadClass(className);
   }

   @Override
   public List<Resource> loadResourceLocal(String name)
   {
      throw new NotImplementedException();
   }

   @Override
   public Resource loadResourceLocal(String root, String name)
   {
      throw new NotImplementedException();
   }
}
