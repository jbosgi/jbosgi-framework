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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * An abstraction of a bundle persistent storage.
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class BundleStorageState {

    static final String PROPERTY_BUNDLE_FILE = "BundleFile";
    static final String PROPERTY_BUNDLE_ID = "BundleId";
    static final String PROPERTY_BUNDLE_LOCATION = "Location";
    static final String PROPERTY_BUNDLE_REV = "BundleRev";
    static final String PROPERTY_LAST_MODIFIED = "LastModified";
    static final String PROPERTY_PERSISTENTLY_STARTED = "PersistentlyStarted";
    static final String PROPERTY_ACTIVATION_POLICY_USED = "ActivationPolicyUsed";
    static final String BUNDLE_PERSISTENT_PROPERTIES = "bundle-persistent.properties";

    private final File bundleDir;
    private final VirtualFile rootFile;
    private final Properties props;
    private final String location;
    private final long bundleId;
    private final int revision;

    private long lastModified;

    static Set<String> requiredProps = new HashSet<String>();
    static {
        requiredProps.add(PROPERTY_BUNDLE_ID);
        requiredProps.add(PROPERTY_BUNDLE_REV);
        requiredProps.add(PROPERTY_BUNDLE_LOCATION);
        requiredProps.add(PROPERTY_LAST_MODIFIED);
    }

    static BundleStorageState createFromStorage(File storageDir) throws IOException {

        Properties props = loadProperties(storageDir);

        VirtualFile rootFile = null;
        String vfsLocation = props.getProperty(PROPERTY_BUNDLE_FILE);
        if (vfsLocation != null) {
            File revFile = new File(storageDir + "/" + vfsLocation);
            rootFile = AbstractVFS.toVirtualFile(revFile.toURI());
        }

        return new BundleStorageState(storageDir, rootFile, props);
    }

    static Properties loadProperties(File storageDir) throws FileNotFoundException, IOException {
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

    static BundleStorageState createBundleStorageState(File storageDir, VirtualFile rootFile, Properties props) throws IOException {
        BundleStorageState storageState = new BundleStorageState(storageDir, rootFile, props);
        storageState.writeProperties();
        return storageState;
    }

    private BundleStorageState(File storageFile, VirtualFile rootFile, Properties props) throws IOException {
        assert storageFile != null : "Null storageFile";
        assert props != null : "Null properties";
        assert storageFile.isDirectory() : "Not a directory: " + storageFile;

        for (String key : requiredProps) {
            if (props.get(key) == null)
                throw MESSAGES.illegalArgumentRequiredPropertyMissing(key, storageFile);
        }

        this.bundleDir = storageFile;
        this.rootFile = rootFile;
        this.props = props;

        this.location = props.getProperty(PROPERTY_BUNDLE_LOCATION);
        this.bundleId = Long.parseLong(props.getProperty(PROPERTY_BUNDLE_ID));
        this.revision = Integer.parseInt(props.getProperty(PROPERTY_BUNDLE_REV));
        this.lastModified = Long.parseLong(props.getProperty(PROPERTY_LAST_MODIFIED));
    }

    File getBundleStorageDir() {
        return bundleDir;
    }

    String getLocation() {
        return location;
    }

    VirtualFile getRootFile() {
        return rootFile;
    }

    long getBundleId() {
        return bundleId;
    }

    int getRevisionId() {
        return revision;
    }

    long getLastModified() {
        String value = props.getProperty(PROPERTY_LAST_MODIFIED);
        return new Long(value);
    }

    void updateLastModified() {
        lastModified = System.currentTimeMillis();
        props.setProperty(PROPERTY_LAST_MODIFIED, new Long(lastModified).toString());
        writeProperties();
    }

    boolean isPersistentlyStarted() {
        String value = props.getProperty(PROPERTY_PERSISTENTLY_STARTED);
        return value != null ? new Boolean(value) : false;
    }

    void setPersistentlyStarted(boolean started) {
        props.setProperty(PROPERTY_PERSISTENTLY_STARTED, new Boolean(started).toString());
        writeProperties();
    }

    boolean isBundleActivationPolicyUsed() {
        String value = props.getProperty(PROPERTY_ACTIVATION_POLICY_USED);
        return value != null ? new Boolean(value) : false;
    }

    void setBundleActivationPolicyUsed(boolean usePolicy) {
        props.setProperty(PROPERTY_ACTIVATION_POLICY_USED, new Boolean(usePolicy).toString());
        writeProperties();
    }

    void deleteBundleStorage() {
        VFSUtils.safeClose(rootFile);
        deleteInternal(bundleDir);
    }

    void deleteRevisionStorage() {
        VFSUtils.safeClose(rootFile);
    }

    void deleteInternal(File file) {
        if (file.isDirectory()) {
            for (File aux : file.listFiles())
                deleteInternal(aux);
        }
        file.delete();
    }

    private void writeProperties() {
        try {
            File propsFile = new File(bundleDir + "/" + BUNDLE_PERSISTENT_PROPERTIES);
            FileOutputStream output = new FileOutputStream(propsFile);
            try {
                props.store(output, "Persistent Bundle Properties");
            } finally {
                VFSUtils.safeClose(output);
            }
        } catch (IOException ex) {
            LOGGER.errorCannotWritePersistentStorage(ex, bundleDir);
        }
    }

    @Override
    public String toString() {
        return "BundleStorageState[id=" + bundleId + ",location=" + location + ",file=" + rootFile + "]";
    }
}