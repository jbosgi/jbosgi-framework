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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;

/**
 * A simple implementation of a BundleStorage
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class DefaultBundleStorageProvider extends AbstractPluginService<BundleStorageProvider> implements BundleStorageProvider {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private boolean firstInit;
    private File storageArea;

    static void addService(ServiceTarget serviceTarget, boolean firstInit) {
        DefaultBundleStorageProvider service = new DefaultBundleStorageProvider(firstInit);
        ServiceBuilder<BundleStorageProvider> builder = serviceTarget.addService(InternalServices.BUNDLE_STORAGE_PLUGIN, service);
        builder.addDependency(org.jboss.osgi.framework.Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultBundleStorageProvider(boolean firstInit) {
        this.firstInit = firstInit;
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        // Cleanup the storage area
        BundleManager bundleManager = injectedBundleManager.getValue();
        String storageClean = (String) bundleManager.getProperty(Constants.FRAMEWORK_STORAGE_CLEAN);
        if (firstInit == true && Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT.equals(storageClean))
            cleanStorage();

    }

    @Override
    public BundleStorageProvider getValue() {
        return this;
    }

    @Override
    public BundleStorageState createStorageState(long bundleId, String location, VirtualFile rootFile) throws IOException {
        assert location != null : "Null location";

        // Make the bundle's storage dir
        File bundleDir = getStorageDir(bundleId);
        Properties props = BundleStorageState.loadProperties(bundleDir);
        String previousRev = props.getProperty(BundleStorageState.PROPERTY_BUNDLE_REV);
        int revision = (previousRev != null ? Integer.parseInt(previousRev) + 1 : 0);

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
            for (File storageDir : storageDirs) {
                LOGGER.debugf("Creating storage state from: %s", storageDir);
                BundleStorageState storageState = BundleStorageState.createFromStorage(storageDir);
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

    private void cleanStorage() {
        File storage = getStorageArea();
        LOGGER.debugf("Deleting storage: %s", storage.getAbsolutePath());
        try {
            deleteRecursively(storage);
        } catch (IOException ex) {
            LOGGER.errorCannotDeleteStorageArea(ex);
        }
    }

    private File getStorageArea() {
        if (storageArea == null) {
            BundleManager bundleManager = injectedBundleManager.getValue();
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

    private void deleteRecursively(File file) throws IOException {
        if (file.isDirectory()) {
            for (File aux : file.listFiles())
                deleteRecursively(aux);
        }
        file.delete();
    }
}