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
package org.jboss.test.osgi.container.storage;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;

import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.plugin.BundleStoragePlugin;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * Test bundle storage
 * 
 * @author thomas.diesler@jboss.com
 * @since 13-Aug-2010
 */
public class BundleStorageTestCase extends OSGiFrameworkTest 
{
   @Test
   public void testBundleStreamFile() throws Exception
   {
      // Bundle-Version: 1.0.0
      // Bundle-SymbolicName: simple-bundle
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleVersion("1.0.0");
            return builder.openStream();
         }
      });
      
      Bundle systemBundle = getSystemContext().getBundle();
      BundleManager bundleManager = AbstractBundle.assertBundleState(systemBundle).getBundleManager();
      BundleStoragePlugin plugin = bundleManager.getPlugin(BundleStoragePlugin.class);
      assertNotNull("BundleStoragePlugin not null", plugin);
      
      File storageDir = plugin.getStorageDir(systemBundle);
      assertTrue(storageDir + " exists", storageDir.exists());
      
      File streamDir = new File (storageDir + File.separator + "bundle-streams");
      assertFalse(streamDir + " exists", streamDir.exists());
      
      Bundle bundle = installBundle(archive);
      assertBundleState(Bundle.INSTALLED, bundle.getState());
      assertTrue(streamDir + " exists", streamDir.exists());
      
      String[] files = streamDir.list();
      assertNotNull("stream files not null", files);
      assertEquals("one stream file", 1, files.length);
      
      String pattern = bundle.getSymbolicName() + "-" + bundle.getVersion();
      assertTrue("contains " + pattern + ": " + files[0], files[0].contains(pattern));

      File file = new File(streamDir + File.separator + files[0]);
      assertTrue(file + " exists", file.exists());
      
      bundle.uninstall();
      assertBundleState(Bundle.UNINSTALLED, bundle.getState());
      assertTrue(file + " exists", file.exists());
      
      refreshPackages(null);
      assertFalse(file + " deleted", file.exists());
   }
}