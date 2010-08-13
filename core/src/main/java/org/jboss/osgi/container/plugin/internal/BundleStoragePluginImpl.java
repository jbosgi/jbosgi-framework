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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.BundleStoragePlugin;
import org.jboss.osgi.metadata.internal.OSGiManifestMetaData;
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

   public BundleStoragePluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

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
   }

   private void deleteRecursively(File file) throws IOException
   {
      if (file.isDirectory())
      {
         for (String name : file.list())
         {
            File child = new File(file.getCanonicalPath() + File.separator + name);
            deleteRecursively(child);
         }
      }

      if (log.isTraceEnabled())
         log.trace("Deleting from storage: " + file);

      file.delete();
   }

   public File getDataFile(Bundle bundle, String filename)
   {
      File bundleDir = getStorageDir(bundle);
      File dataFile = new File(bundleDir.getAbsolutePath() + "/" + filename);
      dataFile.getParentFile().mkdirs();
      return dataFile;
   }

   public File getStorageDir(Bundle bundle)
   {
      File bundleDir = new File(getStorageArea() + "/bundle-" + bundle.getBundleId());
      if (bundleDir.exists() == false)
         bundleDir.mkdirs();

      return bundleDir;
   }

   public URL storeBundleStream(InputStream input) throws IOException
   {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try
      {
         VFSUtils.copyStream(input, baos);
      }
      finally
      {
         input.close();
         baos.close();
      }

      String filename;
      JarInputStream jis = new JarInputStream(new ByteArrayInputStream(baos.toByteArray()));
      Manifest manifest = jis.getManifest();
      if (manifest != null)
      {
         OSGiManifestMetaData metadata = new OSGiManifestMetaData(manifest);
         filename = metadata.getBundleSymbolicName() + "-" + metadata.getBundleVersion();
      }
      else
      {
         filename = "generic-bundle";
      }

      String path = getStorageDir(getBundleManager().getSystemBundle()).getCanonicalPath();
      File streamdir = new File(path + File.separator + "bundle-streams");
      streamdir.mkdirs();

      File file = new File(streamdir + File.separator + filename + "--" + System.currentTimeMillis() + ".jar");
      if (file.exists())
         throw new IllegalStateException("File already exists: " + file);

      FileOutputStream fos = new FileOutputStream(file);
      try
      {
         log.debug("Store bundle stream: " + file);
         VFSUtils.copyStream(new ByteArrayInputStream(baos.toByteArray()), fos);
      }
      finally
      {
         fos.close();
      }

      return file.toURI().toURL();
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
               File tmpFile = File.createTempFile("Constants.FRAMEWORK_STORAGE", null);
               dirName = tmpFile.getParent();
               tmpFile.delete();
            }
            catch (IOException ex)
            {
               throw new IllegalStateException("Cannot create temp storage file", ex);
            }
         }
         storageArea = dirName;
      }
      return storageArea;
   }
}