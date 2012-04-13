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
package org.jboss.osgi.framework.internal;

import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.subA.SimpleActivator;
import org.jboss.test.osgi.framework.subA.SimpleService;
import org.junit.Test;
import org.osgi.framework.BundleActivator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test bundle storage
 *
 * @author thomas.diesler@jboss.com
 * @since 13-Aug-2010
 */
public class BundleStorageTestCase extends AbstractFrameworkTest {

    @Test
    public void testBundleStorageForInputStream() throws Exception {
        BundleManager bundleManager = getBundleManager();
        BundleStorageProvider plugin = getFrameworkState().getBundleStorageProvider();
        assertNotNull("BundleStoragePlugin not null", plugin);

        JavaArchive archive = getArchive();
        BundleStorageState storageState = plugin.createStorageState(bundleManager.nextBundleId(), archive.getName(), toVirtualFile(archive));
        assertStorageState(storageState);

        storageState.deleteBundleStorage();
        File storageDir = storageState.getBundleStorageDir();
        assertFalse("Storage dir deleted", storageDir.exists());

        // Try this a second time
        storageState = plugin.createStorageState(bundleManager.nextBundleId(), archive.getName(), toVirtualFile(archive));
        assertStorageState(storageState);

        storageState.deleteBundleStorage();
        storageDir = storageState.getBundleStorageDir();
        assertFalse("Storage dir deleted", storageDir.exists());
    }

    @Test
    public void testBundleStorageForExternalFile() throws Exception {
        BundleManager bundleManager = getBundleManager();
        BundleStorageProvider plugin = bundleManager.getFrameworkState().getBundleStorageProvider();
        assertNotNull("BundleStoragePlugin not null", plugin);

        File file = new File(plugin.getStorageDir(0) + "/testBundleExternalFile.jar");
        FileOutputStream fos = new FileOutputStream(file);
        VFSUtils.copyStream(toInputStream(getArchive()), fos);
        fos.close();

        VirtualFile rootFile = AbstractVFS.toVirtualFile(file.toURI().toURL());
        BundleStorageState storageState = plugin.createStorageState(bundleManager.nextBundleId(), file.getAbsolutePath(), rootFile);
        assertStorageState(storageState);

        storageState.deleteBundleStorage();
        File storageDir = storageState.getBundleStorageDir();
        assertFalse("Storage dir deleted", storageDir.exists());
    }

    private void assertStorageState(BundleStorageState storageState) {
        assertNotNull("BundleStorageState not null", storageState);

        File storageDir = storageState.getBundleStorageDir();
        assertNotNull("Storage dir not null", storageDir);
        assertNotNull("Location not null", storageState.getLocation());
        assertTrue("Storage dir exists", storageDir.exists());

        File propertiesFile = new File(storageDir + "/" + BundleStorageState.BUNDLE_PERSISTENT_PROPERTIES);
        assertTrue("Properties file exists", propertiesFile.exists());
    }

    private JavaArchive getArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
        archive.addClasses(SimpleService.class, SimpleActivator.class);
        archive.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(SimpleActivator.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}