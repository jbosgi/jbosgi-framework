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
package org.jboss.osgi.framework.plugin.internal;

//$Id$

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.BundleStorageState;
import org.jboss.osgi.framework.bundle.FrameworkState;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.vfs.VirtualFile;
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

   // The BundleId generator
   private AtomicLong identityGenerator = new AtomicLong();
   // The Framework storage area
   private String storageArea;

   public BundleStoragePluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public BundleStorageState createStorageState(String location, VirtualFile rootFile) throws IOException
   {
      if (location == null)
         throw new IllegalArgumentException("Null location");

      int revision = 0;
      long bundleId = identityGenerator.incrementAndGet();

      // Make the bundle's storage dir
      String bundlePath = getStorageDir(bundleId).getAbsolutePath();
      File bundleDir = new File(bundlePath);
      bundleDir.mkdirs();

      Properties props = new Properties();
      File propsFile = new File(bundleDir + File.separator + BUNDLE_PERSISTENT_PROPERTIES);
      if (propsFile.exists())
      {
         props.load(new FileInputStream(propsFile));
         revision = Integer.parseInt(props.getProperty(PROPERTY_BUNDLE_REV));
         revision++;
      }

      if (rootFile != null)
      {
         int index = location.lastIndexOf(File.separator);
         String name = index > 0 ? location.substring(index + 1) : location;
         props.put(PROPERTY_BUNDLE_FILE, name);
      }

      /* Make the file copy
      File revisionDir = new File(bundlePath + File.separator + "rev-" + revision);
      revisionDir.mkdirs();
      File fileCopy = new File(revisionDir + File.separator + name);
      rootFile.recursiveCopy(fileCopy);
      */
      
      // Write the bundle properties
      props.put(PROPERTY_BUNDLE_LOCATION, location);
      props.put(PROPERTY_BUNDLE_ID, new Long(bundleId).toString());
      props.put(PROPERTY_BUNDLE_REV, new Integer(revision).toString());
      props.store(new FileOutputStream(propsFile), "Persistent Bundle Properties");

      return new BundleStorageState(bundleDir, rootFile, props);
   }

   @Override
   public File getDataFile(Bundle bundle, String filename)
   {
      File bundleDir = getStorageDir(bundle.getBundleId());
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
   public File getStorageDir(long bundleId)
   {
      File bundleDir = new File(getStorageArea() + "/bundle-" + bundleId);
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
   public void cleanStorage(String propValue)
   {
      // [TODO] Support values other than 'onFirstInit'
      if (Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(propValue))
      {
         File storage = new File(getStorageArea());
         log.tracef("Deleting from storage: %s", storage.getAbsolutePath());

         try
         {
            deleteRecursively(storage);
         }
         catch (IOException ex)
         {
            log.errorf(ex, "Cannot delete storage area");
         }
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
               log.errorf(ex, "Cannot create storage area");
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
         for (File aux : file.listFiles())
            deleteRecursively(aux);
      }
      else
      {
         file.delete();
      }
   }
}