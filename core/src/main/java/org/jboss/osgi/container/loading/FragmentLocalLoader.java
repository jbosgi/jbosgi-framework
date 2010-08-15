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

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.ConcurrentClassLoader;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Resource;
import org.jboss.osgi.container.bundle.FragmentRevision;
import org.jboss.osgi.container.bundle.ModuleManager;

/**
 * A {@link LocalLoader} that loads fragment defined classes/resources.
 * 
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public class FragmentLocalLoader extends ConcurrentClassLoader implements LocalLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(FragmentLocalLoader.class);

   private final FragmentRevision fragmentRev;
   private final VirtualFileResourceLoader resourceLoader;
   private final Set<String> paths;

   public FragmentLocalLoader(FragmentRevision fragmentRev)
   {
      if (fragmentRev == null)
         throw new IllegalArgumentException("Null fragmentRev");

      this.fragmentRev = fragmentRev;
      this.resourceLoader = new VirtualFileResourceLoader(fragmentRev.getContentRoot());
      this.paths = Collections.unmodifiableSet(new HashSet<String>(resourceLoader.getPaths()));
   }

   public Set<String> getPaths()
   {
      return paths;
   }

   @Override
   public Class<?> loadClassLocal(String className, boolean exportOnly)
   {
      try
      {
         return super.loadClass(className);
      }
      catch (ClassNotFoundException ex)
      {
         return null;
      }
   }

   protected Class<?> findClass(final String className, final boolean exportsOnly, final boolean resolve) throws ClassNotFoundException
   {
      // Check if we have already loaded it..
      Class<?> loadedClass = findLoadedClass(className);
      if (loadedClass != null) {
          return loadedClass;
      }
      
      boolean traceEnabled = log.isTraceEnabled();
      if (traceEnabled)
         log.trace("Attempt to find fragment class [" + className + "] in " + fragmentRev + " ...");

      String path = ModuleManager.getPathFromClassName(className);
      if (paths.contains(path) == false)
      {
         if (traceEnabled)
            log.trace("Not found in fragment [" + className + "]");
         return null;
      }

      // Check to see if we can define it locally it
      final ClassSpec classSpec;
      try
      {
         classSpec = resourceLoader.getClassSpec(className);
      }
      catch (Throwable th)
      {
         log.trace("Unexpected exception in module loader", th);
         return null;
      }

      if (classSpec == null)
      {
         log.trace("No local specification found for class [" + className + "] in " + fragmentRev);
         return null;
      }

      final Class<?> result;
      try
      {
         final byte[] bytes = classSpec.getBytes();
         result = defineClass(className, bytes, 0, bytes.length, classSpec.getCodeSource());
      }
      catch (Throwable th)
      {
         log.trace("Failed to define class [" + className + "] in " + fragmentRev, th);
         return null;
      }
      
      if (resolve)
      {
         resolveClass(result);
      }

      return result;
   }

   @Override
   public List<Resource> loadResourceLocal(String name)
   {
      Resource result = resourceLoader.getResource(name);
      if (result == null)
         return Collections.emptyList();
      
      return Collections.singletonList(result);
   }

   @Override
   public Resource loadResourceLocal(String root, String name)
   {
      Resource result = resourceLoader.getResource(root + File.pathSeparator + name);
      return result;
   }
}
