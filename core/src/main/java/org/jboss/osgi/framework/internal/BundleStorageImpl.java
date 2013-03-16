/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.BundleStorage;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Constants;

/**
 * A simple implementation of a BundleStorage
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public final class BundleStorageImpl implements BundleStorage {

    private final BundleManager bundleManager;
    private final Map<String, StorageState> storageStates = new HashMap<String, StorageState>();
    private File storageArea;

    public BundleStorageImpl(BundleManager bundleManager) {
        this.bundleManager = bundleManager;
    }

    @Override
    public void initialize(Map<String, Object> props, boolean firstInit) throws IOException {
        // Cleanup the storage area
        String storageClean = (String) props.get(Constants.FRAMEWORK_STORAGE_CLEAN);
        if (firstInit == true && Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(storageClean)) {
            File storage = getStorageArea();
            LOGGER.debugf("Deleting storage: %s", storage.getAbsolutePath());
            deleteRecursive(storage);
        }

        // Initialize storage states
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(StorageState.BUNDLE_DIRECTORY_PREFIX);
            }
        };
        File[] storageDirs = getStorageArea().listFiles(filter);
        if (storageDirs != null) {
            for (File storageDir : storageDirs) {
                StorageState storageState = StorageState.createStorageState(storageDir);
                if (storageState.getBundleId() != 0) {
                    storageStates.put(storageState.getLocation(), storageState);
                }
            }
        }
    }

    @Override
    public StorageState createStorageState(long bundleId, String location, int startlevel, VirtualFile rootFile) throws IOException {
        assert location != null : "Null location";

        // Make the bundle's storage dir
        File bundleDir = getStorageDir(bundleId);
        Properties props = StorageState.loadProperties(bundleDir);
        String previousRev = props.getProperty(StorageState.PROPERTY_BUNDLE_REV);
        int revision = (bundleId != 0 && previousRev != null ? Integer.parseInt(previousRev) + 1 : 0);

        // Write the bundle properties
        props.put(StorageState.PROPERTY_BUNDLE_LOCATION, location);
        props.put(StorageState.PROPERTY_BUNDLE_ID, new Long(bundleId).toString());
        props.put(StorageState.PROPERTY_BUNDLE_REV, new Integer(revision).toString());
        props.put(StorageState.PROPERTY_START_LEVEL, new Integer(startlevel).toString());
        props.put(StorageState.PROPERTY_LAST_MODIFIED, new Long(System.currentTimeMillis()).toString());

        StorageState storageState = StorageState.createStorageState(bundleDir, rootFile, props);
        synchronized (storageStates) {
            if (storageState.getBundleId() != 0) {
                storageStates.put(storageState.getLocation(), storageState);
            }
        }
        return storageState;
    }

    @Override
    public void deleteStorageState(StorageState storageState) {
        LOGGER.debugf("Deleting storage state: %s", storageState);
        VFSUtils.safeClose(storageState.getRootFile());
        deleteRecursive(storageState.getStorageDir());
        synchronized (storageStates) {
            storageStates.remove(storageState.getLocation());
        }
    }

    @Override
    public Set<StorageState> getStorageStates() {
        return Collections.unmodifiableSet(new HashSet<StorageState>(storageStates.values()));
    }

    @Override
    public StorageState getStorageState(String location) {
        return location != null ? storageStates.get(location) : null;
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
    public File getStorageArea() {
        if (storageArea == null) {
            String dirName = (String) bundleManager.getProperty(Constants.FRAMEWORK_STORAGE);
            if (dirName == null) {
                try {
                    File storageDir = new File("./osgi-store");
                    dirName = storageDir.getCanonicalPath();
                } catch (IOException ex) {
                    throw MESSAGES.illegalStateCannotCreateStorageArea(ex);
                }
            }
            storageArea = new File(dirName).getAbsoluteFile();
        }
        return storageArea;
    }

    @Override
    public File getDataFile(long bundleId, String filename) {
        File bundleDir = getStorageDir(bundleId);
        File dataFile = new File(bundleDir.getAbsolutePath() + File.separator + filename);
        dataFile.getParentFile().mkdirs();

        String filePath = dataFile.getAbsolutePath();
        try {
            filePath = dataFile.getCanonicalPath();
        } catch (IOException ex) {
            // ignore
        }
        return new File(filePath);
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File aux : file.listFiles())
                deleteRecursive(aux);
        }
        file.delete();
    }
}