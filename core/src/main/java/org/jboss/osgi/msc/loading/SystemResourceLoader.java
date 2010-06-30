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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.vfs.VFSUtils;

/**
 * An {@link ResourceLoader} that is backed by the system classloader.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class SystemResourceLoader extends AbstractResourceLoader
{
   private ClassLoader classLoader;

   public SystemResourceLoader(ClassLoader classLoader)
   {
      if (classLoader == null)
         throw new IllegalArgumentException("Null classLoader");
      this.classLoader = classLoader;
   }

   @Override
   public ClassSpec getClassSpec(String name) throws IOException
   {
      String fileName = name.replace('.', File.separatorChar) + ".class";
      InputStream is = classLoader.getResourceAsStream(fileName);
      if (is == null)
         return null;

      ClassSpec spec = new ClassSpec();
      try
      {
         ByteArrayOutputStream os = new ByteArrayOutputStream(1024);
         VFSUtils.copyStream(is, os);
         spec.setBytes(os.toByteArray());
         return spec;
      }
      finally
      {
         safeClose(is);
      }
   }

   @Override
   public PackageSpec getPackageSpec(String name) throws IOException
   {
      PackageSpec spec = new PackageSpec();
      return spec;
   }

   @Override
   public Resource getResource(String name)
   {
      return null;
   }

   @Override
   public String getLibrary(String name)
   {
      throw new NotImplementedException();
   }

   @Override
   public Collection<String> getPaths()
   {
      return Collections.singleton("/");
   }

   private void safeClose(final Closeable closeable)
   {
      if (closeable != null)
      {
         try
         {
            closeable.close();
         }
         catch (IOException e)
         {
            // ignore
         }
      }
   }
}
