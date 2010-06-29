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
import java.util.Collection;
import java.util.Collections;

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * A host bundle.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class VirtualFileResourceLoader extends AbstractResourceLoader
{
   private VirtualFile virtualFile;

   public VirtualFileResourceLoader(VirtualFile virtualFile)
   {
      if (virtualFile == null)
         throw new IllegalArgumentException("Null virtualFile");
      this.virtualFile = virtualFile;
   }

   @Override
   public ClassSpec getClassSpec(String name) throws IOException
   {
      throw new NotImplementedException();
   }

   @Override
   public PackageSpec getPackageSpec(String name) throws IOException
   {
      throw new NotImplementedException();
   }

   @Override
   public Resource getResource(String name)
   {
      try
      {
         VirtualFile child = virtualFile.getChild(name);
         return new VirtualResource(child);
      }
      catch (IOException ex)
      {
         return null;
      }
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

   static class VirtualResource implements Resource
   {
      VirtualFile child;

      VirtualResource(VirtualFile child)
      {
         this.child = child;
      }

      @Override
      public String getName()
      {
         return child.getName();
      }

      @Override
      public URL getURL()
      {
         try
         {
            return child.toURL();
         }
         catch (IOException ex)
         {
            throw new IllegalStateException("Cannot obtain URL for: " + child);
         }
      }

      @Override
      public InputStream openStream() throws IOException
      {
         return child.openStream();
      }

      @Override
      public long getSize()
      {
         return 0;
      }
   }
}
