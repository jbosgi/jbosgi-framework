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
package org.jboss.osgi.container.plugin.internal;

//$Id$

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.BundleStoragePlugin;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * A simple implementation of a BundleStorage
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class BundleStoragePluginImpl extends AbstractPlugin implements BundleStoragePlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(BundleStoragePluginImpl.class);

   private String storageArea;
   private File bundleStreamDir;

   public BundleStoragePluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public File getDataFile(Bundle bundle, String filename)
   {
      File bundleDir = getStorageDir(bundle);
      File dataFile = new File(bundleDir.getAbsolutePath() + "/" + filename);
      dataFile.getParentFile().mkdirs();

      String filePath = dataFile.getAbsolutePath();
      try
      {
         filePath = dataFile.getCanonicalPath();
      }
      catch (IOException ex)
      {
         // ignore
      }
      return new File(filePath);
   }

   @Override
   public File getStorageDir(Bundle bundle)
   {
      File bundleDir = new File(getStorageArea() + "/bundle-" + bundle.getBundleId());
      if (bundleDir.exists() == false)
         bundleDir.mkdirs();

      String filePath = bundleDir.getAbsolutePath();
      try
      {
         filePath = bundleDir.getCanonicalPath();
      }
      catch (IOException ex)
      {
         // ignore
      }
      return new File(filePath);
   }

   @Override
   public File storeBundleStream(String location, InputStream input, int revisionCount) throws IOException
   {
      if (location == null)
         throw new IllegalArgumentException("Null location");
      if (input == null)
         throw new IllegalArgumentException("Null input");

      // Generate the filename from the location
      String filename;
      try
      {
         URL url = new URL(location);
         filename = url.getPath();
      }
      catch (IOException ex)
      {
         filename = location;
      }
      String testdir = "target" + File.separator + "test-libs";
      int testlibsIndex = filename.indexOf(testdir);
      if (testlibsIndex > 0)
         filename = filename.substring(testlibsIndex + testdir.length() + 1);
      String currentPath = new File(".").getCanonicalPath();
      if (filename.startsWith(currentPath))
         filename = filename.substring(currentPath.length() + 1);
      if (filename.endsWith("jar"))
         filename = filename.substring(0, filename.length() - 4);

      filename = filename.replace('/', '.');
      if (revisionCount > 0)
         filename += "-rev" + revisionCount;

      File streamdir = getBundleStreamDir();
      streamdir.mkdirs();

      File file = new File(streamdir + File.separator + filename + ".jar");
      if (file.exists() && revisionCount > 0)
         throw new IllegalStateException("File already exists: " + file);

      int dupcount = 0;
      while (file.exists())
      {
         int dupindex = filename.lastIndexOf("-dup");
         if (dupcount > 0 && dupindex > 0)
            filename = filename.substring(0, dupindex);

         filename += "-dup" + (++dupcount);
         file = new File(streamdir + File.separator + filename + ".jar");
      }

      FileOutputStream fos = new FileOutputStream(file);
      try
      {
         log.debug("Store bundle stream: " + file);
         VFSUtils.copyStream(input, fos);
      }
      catch (IOException ex)
      {
         file.delete();
         throw ex;
      }
      finally
      {
         fos.close();
      }

      return file;
   }

   @Override
   public File getBundleStreamDir()
   {
      if (bundleStreamDir == null)
      {
         String path;
         try
         {
            path = getStorageDir(getBundleManager().getSystemBundle()).getCanonicalPath();
         }
         catch (IOException ex)
         {
            throw new IllegalStateException("Cannot obtain bundle stream dir", ex);
         }
         bundleStreamDir = new File(path + File.separator + "bundle-streams");
      }
      return bundleStreamDir;
   }

   @Override
   public void cleanStorage(String propValue)
   {
      // [TODO] Support values other than 'onFirstInit'
      if (Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(propValue))
      {
         File storage = new File(getStorageArea());
         try
         {
            deleteRecursively(storage);
         }
         catch (IOException ex)
         {
            log.error("Cannot delete storage area", ex);
         }
      }

      // Always delete the bundle stream dir
      try
      {
         deleteRecursively(getBundleStreamDir());
      }
      catch (IOException ex)
      {
         log.error("Cannot delete bundle stream dir", ex);
      }
   }

   private String getStorageArea()
   {
      if (storageArea == null)
      {
         FrameworkState frameworkState = getBundleManager().getFrameworkState();
         String dirName = frameworkState.getProperty(Constants.FRAMEWORK_STORAGE);
         if (dirName == null)
         {
            try
            {
               File storageDir = new File("./osgi-store");
               dirName = storageDir.getCanonicalPath();
            }
            catch (IOException ex)
            {
               log.error("Cannot create storage area", ex);
               throw new IllegalStateException("Cannot create storage area");
            }
         }
         storageArea = dirName;
      }
      return storageArea;
   }

   private void deleteRecursively(File file) throws IOException
   {
      if (file.isDirectory())
      {
         String[] fileList = file.list();
         if (fileList != null)
         {
            for (String name : fileList)
            {
               File child = new File(file.getCanonicalPath() + File.separator + name);
               deleteRecursively(child);
            }
         }
      }

      if (log.isTraceEnabled())
         log.trace("Deleting from storage: " + file);

      file.delete();
   }
}