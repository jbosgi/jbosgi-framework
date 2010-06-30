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
package org.jboss.osgi.msc.loading;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.logging.Logger;
import org.jboss.modules.ConcurrentClassLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;

/**
 * A {@link ConcurrentClassLoader} that has OSGi semantics.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class OSGiModuleClassLoader extends ConcurrentClassLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(OSGiModuleClassLoader.class);
   
   private Module module;

   public OSGiModuleClassLoader(Module module)
   {
      this.module = module;
   }

   @Override
   protected Class<?> findClass(String className, boolean exportsOnly) throws ClassNotFoundException
   {
      log.debug("Find class '" + className + "' with exportsOnly=" + exportsOnly);
      
      ModuleClassLoader moduleLoader = module.getClassLoader();
      log.debug("Attempt to load '" + className + "' from: " + moduleLoader);
      try
      {
         Class<?>  result;
         if (exportsOnly == true)
         {
            result = moduleLoader.loadExportedClass(className);
         }
         else
         {
            result = moduleLoader.loadClass(className);
         }
         log.debug("Found '" + className + "' in: " + moduleLoader);
         return result;
      }
      catch (ClassNotFoundException ex)
      {
         log.debug("Cannot find '" + className + "' in: " + moduleLoader);
      }
      
      throw new ClassNotFoundException(className);
   }

   @Override
   protected URL findResource(String name, boolean exportsOnly)
   {
      log.debug("Find resource '" + name + "' with exportsOnly=" + exportsOnly);
      URL result = super.findResource(name, exportsOnly);
      return result;
   }

   @Override
   protected Enumeration<URL> findResources(String name, boolean exportsOnly) throws IOException
   {
      log.debug("Find resources '" + name + "' with exportsOnly=" + exportsOnly);
      Enumeration<URL> result = super.findResources(name, exportsOnly);
      return result;
   }

   @Override
   protected InputStream findResourceAsStream(String name, boolean exportsOnly)
   {
      log.debug("Find resource as stream '" + name + "' with exportsOnly=" + exportsOnly);
      InputStream result = super.findResourceAsStream(name, exportsOnly);
      return result;
   }
}
