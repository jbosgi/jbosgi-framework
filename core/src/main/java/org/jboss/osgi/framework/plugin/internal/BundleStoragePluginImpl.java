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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.jboss.logging.Logger;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.BundleStorageState;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * A simple implementation of a BundleStorage
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class BundleStoragePluginImpl extends AbstractPlugin implements BundleStoragePlugin {

    // Provide logging
    final Logger log = Logger.getLogger(BundleStoragePluginImpl.class);

    // The Framework storage area
    private File storageArea;

    public BundleStoragePluginImpl(BundleManager bundleManager) {
        super(bundleManager);
    }

    @Override
    public BundleStorageState createStorageState(long bundleId, String location, VirtualFile rootFile) throws IOException {
        if (location == null)
            throw new IllegalArgumentException("Null location");

        // Make the bundle's storage dir
        String bundlePath = getStorageDir(bundleId).getAbsolutePath();
        File bundleDir = new File(bundlePath);
        bundleDir.mkdirs();

        Properties props = BundleStorageState.loadProperties(bundleDir);
        String previousRev = props.getProperty(BundleStorageState.PROPERTY_BUNDLE_REV);
        int revision = (previousRev != null ? Integer.parseInt(previousRev) + 1 : 0);

        if (rootFile != null) {
            File revFile = new File(bundlePath + "/bundle-" + bundleId + "-rev-" + revision + ".jar");
            FileOutputStream output = new FileOutputStream(revFile);
            VFSUtils.copyStream(rootFile.openStream(), output);
            output.close();
            props.put(BundleStorageState.PROPERTY_BUNDLE_FILE, revFile.getName());
        }

        // Write the bundle properties
        props.put(BundleStorageState.PROPERTY_BUNDLE_LOCATION, location);
        props.put(BundleStorageState.PROPERTY_BUNDLE_ID, new Long(bundleId).toString());
        props.put(BundleStorageState.PROPERTY_BUNDLE_REV, new Integer(revision).toString());
        props.put(BundleStorageState.PROPERTY_LAST_MODIFIED, new Long(System.currentTimeMillis()).toString());

        return BundleStorageState.createBundleStorageState(bundleDir, rootFile, props);
    }

    @Override
    public List<BundleStorageState> getBundleStorageStates() throws IOException {
        List<BundleStorageState> states = new ArrayList<BundleStorageState>();
        File[] storageDirs = getStorageArea().listFiles();
        if (storageDirs != null) {
            for (File bundleDir : storageDirs) {
                BundleStorageState storageState = BundleStorageState.createFromStorage(bundleDir);
                states.add(storageState);
            }
        }
        return Collections.unmodifiableList(states);
    }

    @Override
    public File getDataFile(Bundle bundle, String filename) {
        File bundleDir = getStorageDir(bundle.getBundleId());
        File dataFile = new File(bundleDir.getAbsolutePath() + "/" + filename);
        dataFile.getParentFile().mkdirs();

        String filePath = dataFile.getAbsolutePath();
        try {
            filePath = dataFile.getCanonicalPath();
        } catch (IOException ex) {
            // ignore
        }
        return new File(filePath);
    }

    @Override
    public File getStorageDir(long bundleId) {
        File bundleDir = new File(getStorageArea() + "/bundle-" + bundleId);
        if (bundleDir.exists() == false)
            bundleDir.mkdirs();

        String filePath = bundleDir.getAbsolutePath();
        try {
            filePath = bundleDir.getCanonicalPath();
        } catch (IOException ex) {
            // ignore
        }
        return new File(filePath);
    }

    @Override
    public void cleanStorage() {
        File storage = getStorageArea();
        log.tracef("Deleting from storage: %s", storage.getAbsolutePath());

        try {
            deleteRecursively(storage);
        } catch (IOException ex) {
            log.errorf(ex, "Cannot delete storage area");
        }
    }

    private File getStorageArea() {
        if (storageArea == null) {
            String dirName = (String) getBundleManager().getProperty(Constants.FRAMEWORK_STORAGE);
            if (dirName == null) {
                try {
                    File storageDir = new File("./osgi-store");
                    dirName = storageDir.getCanonicalPath();
                } catch (IOException ex) {
                    log.errorf(ex, "Cannot create storage area");
                    throw new IllegalStateException("Cannot create storage area");
                }
            }
            storageArea = new File(dirName).getAbsoluteFile();
        }
        return storageArea;
    }

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            for (File aux : file.listFiles())
                deleteRecursively(aux);
        }
        file.delete();
    }
}