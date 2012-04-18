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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Constants;

/**
 * A simple implementation of a BundleStorage
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class BundleStoragePlugin extends AbstractPluginService<BundleStoragePlugin> {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final Map<String, InternalStorageState> storageStates = new LinkedHashMap<String, InternalStorageState>();
    private File storageArea;
    private boolean firstInit;

    static void addService(ServiceTarget serviceTarget, boolean firstInit) {
        BundleStoragePlugin service = new BundleStoragePlugin(firstInit);
        ServiceBuilder<BundleStoragePlugin> builder = serviceTarget.addService(InternalServices.BUNDLE_STORAGE_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private BundleStoragePlugin(boolean firstInit) {
        this.firstInit = firstInit;
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);

        try {
            // Cleanup the storage area
            BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
            String storageClean = (String) bundleManager.getProperty(Constants.FRAMEWORK_STORAGE_CLEAN);
            if (firstInit == true && Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(storageClean)) {
                File storage = getStorageArea();
                LOGGER.debugf("Deleting storage: %s", storage.getAbsolutePath());
                deleteRecursive(storage);
            }

            // Initialize storage states
            File[] storageDirs = getStorageArea().listFiles();
            if (storageDirs != null) {
                for (File storageDir : storageDirs) {
                    LOGGER.debugf("Creating storage state from: %s", storageDir);
                    InternalStorageState storageState = InternalStorageState.createStorageState(storageDir);
                    storageStates.put(storageState.getLocation(), storageState);
                }
            }
        } catch (IOException ex) {
            throw new StartException(ex);
        }
    }

    @Override
    public BundleStoragePlugin getValue() {
        return this;
    }

    InternalStorageState createStorageState(long bundleId, String location, VirtualFile rootFile) throws IOException {
        assert location != null : "Null location";

        // Make the bundle's storage dir
        File bundleDir = getStorageDir(bundleId);
        Properties props = InternalStorageState.loadProperties(bundleDir);
        String previousRev = props.getProperty(StorageState.PROPERTY_BUNDLE_REV);
        int revision = (previousRev != null ? Integer.parseInt(previousRev) + 1 : 0);

        // Write the bundle properties
        props.put(StorageState.PROPERTY_BUNDLE_LOCATION, location);
        props.put(StorageState.PROPERTY_BUNDLE_ID, new Long(bundleId).toString());
        props.put(StorageState.PROPERTY_BUNDLE_REV, new Integer(revision).toString());
        props.put(StorageState.PROPERTY_LAST_MODIFIED, new Long(System.currentTimeMillis()).toString());

        InternalStorageState storageState = InternalStorageState.createStorageState(bundleDir, rootFile, props);
        synchronized (storageStates) {
            storageStates.put(storageState.getLocation(), storageState);
        }
        return storageState;
    }

    void deleteStorageState(InternalStorageState storageState) {
        VFSUtils.safeClose(storageState.getRootFile());
        deleteRecursive(storageState.getStorageDir());
        synchronized (storageStates) {
            storageStates.remove(storageState.getLocation());
        }
    }

    List<InternalStorageState> getBundleStorageStates() {
        return Collections.unmodifiableList(new ArrayList<InternalStorageState>(storageStates.values()));
    }

    InternalStorageState getStorageState(String location) {
        return location != null ? storageStates.get(location) : null;
    }

    File getStorageDir(long bundleId) {
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

    File getStorageArea() {
        if (storageArea == null) {
            BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
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

    File getDataFile(long bundleId, String filename) {
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

    static class InternalStorageState extends StorageState {

        private static InternalStorageState createStorageState(File storageDir) throws IOException {
            VirtualFile rootFile = null;
            Properties props = loadProperties(storageDir);
            String vfsLocation = props.getProperty(PROPERTY_BUNDLE_FILE);
            if (vfsLocation != null) {
                File revFile = new File(storageDir + "/" + vfsLocation);
                rootFile = AbstractVFS.toVirtualFile(revFile.toURI());
            }
            return new InternalStorageState(storageDir, rootFile, props);
        }

        private static InternalStorageState createStorageState(File storageDir, VirtualFile rootFile, Properties props) throws IOException {
            InternalStorageState storageState = new InternalStorageState(storageDir, rootFile, props);
            if (rootFile != null) {
                String bundleId = props.getProperty(StorageState.PROPERTY_BUNDLE_ID);
                String revision = props.getProperty(StorageState.PROPERTY_BUNDLE_REV);
                File revFile = new File(storageDir + "/bundle-" + bundleId + "-rev-" + revision + ".jar");
                FileOutputStream output = new FileOutputStream(revFile);
                storageDir.mkdirs();
                InputStream input = rootFile.openStream();
                try {
                    VFSUtils.copyStream(input, output);
                } finally {
                    input.close();
                    output.close();
                }
                props.put(StorageState.PROPERTY_BUNDLE_FILE, revFile.getName());
            }
            storageState.writeProperties();
            return storageState;
        }

        private static Properties loadProperties(File storageDir) throws FileNotFoundException, IOException {
            Properties props = new Properties();
            File propsFile = new File(storageDir + "/" + BUNDLE_PERSISTENT_PROPERTIES);
            if (propsFile.exists()) {
                FileInputStream input = new FileInputStream(propsFile);
                try {
                    props.load(input);
                } finally {
                    VFSUtils.safeClose(input);
                }
            }
            return props;
        }

        private InternalStorageState(File storageDir, VirtualFile rootFile, Properties props) {
            super(storageDir, rootFile, props);
        }

        void updateLastModified() {
            getProperties().setProperty(PROPERTY_LAST_MODIFIED, new Long(System.currentTimeMillis()).toString());
            writeProperties();
        }

        void setPersistentlyStarted(boolean started) {
            getProperties().setProperty(PROPERTY_PERSISTENTLY_STARTED, new Boolean(started).toString());
            writeProperties();
        }

        void setBundleActivationPolicyUsed(boolean usePolicy) {
            getProperties().setProperty(PROPERTY_ACTIVATION_POLICY_USED, new Boolean(usePolicy).toString());
            writeProperties();
        }

        private void writeProperties() {
            try {
                File propsFile = new File(getStorageDir() + "/" + BUNDLE_PERSISTENT_PROPERTIES);
                FileOutputStream output = new FileOutputStream(propsFile);
                try {
                    getProperties().store(output, "Persistent Bundle Properties");
                } finally {
                    VFSUtils.safeClose(output);
                }
            } catch (IOException ex) {
                LOGGER.errorCannotWritePersistentStorage(ex, getStorageDir());
            }
        }

    }
}