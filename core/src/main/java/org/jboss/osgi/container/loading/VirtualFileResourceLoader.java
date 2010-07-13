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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.modules.AbstractResourceLoader;
import org.jboss.modules.ClassSpec;
import org.jboss.modules.PackageSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * An {@link ResourceLoader} that is backed by a {@link VirtualFile} pointing to an archive.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class VirtualFileResourceLoader extends AbstractResourceLoader
{
   private VirtualFile virtualFile;
   private List<String> filteredPaths;

   public VirtualFileResourceLoader(VirtualFile virtualFile, List<String> paths)
   {
      if (virtualFile == null)
         throw new IllegalArgumentException("Null virtualFile");
      
      this.virtualFile = virtualFile;
      
      filteredPaths = new ArrayList<String>();
      if (paths != null)
         filteredPaths.addAll(paths);
   }

   @Override
   public ClassSpec getClassSpec(String name) throws IOException
   {
      String fileName = name.replace('.', File.separatorChar) + ".class";
      VirtualFile child = virtualFile.getChild(fileName);
      if (child == null)
         return null;

      ClassSpec spec = new ClassSpec();
      InputStream is = child.openStream();
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
      Manifest manifest = VFSUtils.getManifest(virtualFile);
      if (manifest == null)
      {
         return spec;
      }
      Attributes mainAttribute = manifest.getAttributes(name);
      Attributes entryAttribute = manifest.getAttributes(name);
      spec.setSpecTitle(getDefinedAttribute(Attributes.Name.SPECIFICATION_TITLE, entryAttribute, mainAttribute));
      spec.setSpecVersion(getDefinedAttribute(Attributes.Name.SPECIFICATION_VERSION, entryAttribute, mainAttribute));
      spec.setSpecVendor(getDefinedAttribute(Attributes.Name.SPECIFICATION_VENDOR, entryAttribute, mainAttribute));
      spec.setImplTitle(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_TITLE, entryAttribute, mainAttribute));
      spec.setImplVersion(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VERSION, entryAttribute, mainAttribute));
      spec.setImplVendor(getDefinedAttribute(Attributes.Name.IMPLEMENTATION_VENDOR, entryAttribute, mainAttribute));
      if (Boolean.parseBoolean(getDefinedAttribute(Attributes.Name.SEALED, entryAttribute, mainAttribute)))
      {
         spec.setSealBase(virtualFile.toURL());
      }
      return spec;
   }

   private static String getDefinedAttribute(Attributes.Name name, Attributes entryAttribute, Attributes mainAttribute)
   {
      final String value = entryAttribute == null ? null : entryAttribute.getValue(name);
      return value == null ? mainAttribute == null ? null : mainAttribute.getValue(name) : value;
   }

   @Override
   public Resource getResource(String name)
   {
      try
      {
         VirtualFile child = virtualFile.getChild(name);
         if (child == null)
            return null;
         
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
      return Collections.unmodifiableList(filteredPaths);
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

   static class VirtualResource implements Resource
   {
      VirtualFile child;

      VirtualResource(VirtualFile child)
      {
         if (child == null)
            throw new IllegalArgumentException("Null child");
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
