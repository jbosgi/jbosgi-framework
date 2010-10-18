/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.framework.bundle;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;

// $Id$

/**
 * An abstraction of a bundle persistent storage.
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public final class BundleStorageState
{
   // Provide logging
   final Logger log = Logger.getLogger(BundleStorageState.class);

   private final File bundleDir;
   private final VirtualFile rootFile;
   private final Properties props;

   public BundleStorageState(File bundleDir, VirtualFile rootFile, Properties props) throws IOException
   {
      if (bundleDir == null)
         throw new IllegalArgumentException("Null storageDir");
      if (bundleDir.isDirectory() == false)
         throw new IllegalArgumentException("Not a directory: " + bundleDir);
      if (props == null)
         throw new IllegalArgumentException("Null properties");

      this.bundleDir = bundleDir;
      this.rootFile = rootFile;
      this.props = props;
      
      updateLastModified();
   }

   public File getBundleStorageDir()
   {
      return bundleDir;
   }

   public String getLocation()
   {
      String location = props.getProperty(BundleStoragePlugin.PROPERTY_BUNDLE_LOCATION);
      return location;
   }

   public VirtualFile getRootFile()
   {
      return rootFile;
   }

   public long getBundleId()
   {
      String value = props.getProperty(BundleStoragePlugin.PROPERTY_BUNDLE_ID);
      return new Long(value);
   }

   public int getRevision()
   {
      String value = props.getProperty(BundleStoragePlugin.PROPERTY_BUNDLE_REV);
      return new Integer(value);
   }

   public long getLastModified()
   {
      String value = props.getProperty(BundleStoragePlugin.PROPERTY_LAST_MODIFIED);
      return new Long(value);
   }

   public void updateLastModified()
   {
      props.setProperty(BundleStoragePlugin.PROPERTY_LAST_MODIFIED, new Long(System.currentTimeMillis()).toString());
   }
   
   public boolean isPersistentlyStarted()
   {
      String value = props.getProperty(BundleStoragePlugin.PROPERTY_PERSISTENTLY_STARTED);
      return value != null ? new Boolean(value) : false;
   }

   public void setPersistentlyStarted(boolean started)
   {
      props.setProperty(BundleStoragePlugin.PROPERTY_PERSISTENTLY_STARTED, new Boolean(started).toString());
   }

   public void deleteBundleStorage()
   {
      deleteInternal(bundleDir);
   }

   public void deleteRevisionStorage()
   {
      VFSUtils.safeClose(rootFile);
   }

   void deleteInternal(File file)
   {
      if (file.isDirectory())
      {
         for (File aux : file.listFiles())
            deleteInternal(aux);
      }
      file.delete();
   }
}