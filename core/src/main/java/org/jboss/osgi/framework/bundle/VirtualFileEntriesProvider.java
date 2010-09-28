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
package org.jboss.osgi.framework.bundle;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.logging.Logger;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * A bundle entries provider.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Sep-2010
 */
public class VirtualFileEntriesProvider implements EntriesProvider
{
   static final Logger log = Logger.getLogger(VirtualFileEntriesProvider.class);
   
   private final VirtualFile rootFile;

   VirtualFileEntriesProvider(VirtualFile rootFile)
   {
      if (rootFile == null)
         throw new IllegalArgumentException("Null rootFile");
      this.rootFile = rootFile;
   }

   @Override
   public Enumeration<String> getEntryPaths(String path)
   {
      try
      {
         return rootFile.getEntryPaths(path);
      }
      catch (IOException ex)
      {
         return null;
      }
   }

   @Override
   public URL getEntry(String path)
   {
      try
      {
         VirtualFile child = rootFile.getChild(path);
         return child != null ? child.toURL() : null;
      }
      catch (IOException ex)
      {
         log.errorv(ex, "Cannot get entry: {0}", path);
         return null;
      }
   }

   @Override
   public Enumeration<URL> findEntries(String path, String pattern, boolean recurse)
   {
      try
      {
         return rootFile.findEntries(path, pattern, recurse);
      }
      catch (IOException ex)
      {
         return null;
      }
   }
}
