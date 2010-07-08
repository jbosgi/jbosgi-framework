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
import java.io.IOException;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.BundleStoragePlugin;
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
         storage.delete();
      }
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