/*
* JBoss, Home of Professional Open Source
* Copyright 2006, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.container.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;


/**
 * An aggregated VirtualFile.
 * 
 * For child operations, iterate over all roots.
 * 
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public final class AggregatedVirtualFile implements VirtualFile
{
   // Provide logging
   private static final Logger log = Logger.getLogger(AggregatedVirtualFile.class);
   
   private VirtualFile[] roots;
   
   private AggregatedVirtualFile(VirtualFile[] roots)
   {
      if (roots == null || roots.length == 0)
         throw new IllegalArgumentException("Null roots");
      
      this.roots = roots;
   }

   public static VirtualFile aggregatedBundleClassPath(VirtualFile rootFile, OSGiMetaData metadata)
   {
      VirtualFile result = rootFile;
      
      // Add the Bundle-ClassPath to the root virtual files
      if (metadata.getBundleClassPath().size() > 0)
      {
         List<VirtualFile> rootList = new ArrayList<VirtualFile>(Collections.singleton(rootFile));
         for (String path : metadata.getBundleClassPath())
         {
            if (path.equals("."))
               continue;
            
            try
            {
               VirtualFile child = rootFile.getChild(path);
               if (child != null)
               {
                  VirtualFile root = AbstractVFS.getRoot(child.toURL()); 
                  rootList.add(root);
               }
            }
            catch (IOException ex)
            {
               log.error("Cannot get class path element: " + path, ex);
            }
         }
         VirtualFile[] roots = rootList.toArray(new VirtualFile[rootList.size()]);
         result = new AggregatedVirtualFile(roots);
      }
      return result;
   }
   
   @Override
   public String getName()
   {
      return roots[0].getName();
   }

   @Override
   public String getPathName()
   {
      return roots[0].getPathName();
   }

   @Override
   public boolean isFile() throws IOException
   {
      return roots[0].isFile();
   }

   @Override
   public boolean isDirectory() throws IOException
   {
      return roots[0].isDirectory();
   }

   @Override
   public URL toURL() throws IOException
   {
      return roots[0].toURL();
   }

   @Override
   public URL getStreamURL() throws IOException
   {
      return roots[0].getStreamURL();
   }

   @Override
   public VirtualFile getParent() throws IOException
   {
      return roots[0].getParent();
   }

   @Override
   public VirtualFile getChild(String path) throws IOException
   {
      for (VirtualFile root : roots)
      {
         VirtualFile child = root.getChild(path);
         if (child != null)
            return child;
      }
      return null;
   }

   @Override
   public List<VirtualFile> getChildrenRecursively() throws IOException
   {
      List<VirtualFile> result = new ArrayList<VirtualFile>();
      for (VirtualFile root : roots)
      {
         result.addAll(root.getChildrenRecursively());
      }
      return Collections.unmodifiableList(result);
   }

   @Override
   public Enumeration<URL> findEntries(String path, String pattern, boolean recurse) throws IOException
   {
      return roots[0].findEntries(path, pattern, recurse);
   }

   @Override
   public Enumeration<String> getEntryPaths(String path) throws IOException
   {
      return roots[0].getEntryPaths(path);
   }

   @Override
   public InputStream openStream() throws IOException
   {
      return roots[0].openStream();
   }

   @Override
   public void close()
   {
      for (VirtualFile root : roots)
      {
         root.close();
      }
   }
}
