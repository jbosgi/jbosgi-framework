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
package org.jboss.osgi.framework;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.File;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.jboss.osgi.vfs.VirtualFile;

/**
 * An abstraction of a bundle persistent storage.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class StorageState {

    public static final String PROPERTY_BUNDLE_FILE = "BundleFile";
    public static final String PROPERTY_BUNDLE_ID = "BundleId";
    public static final String PROPERTY_BUNDLE_LOCATION = "Location";
    public static final String PROPERTY_BUNDLE_REV = "BundleRev";
    public static final String PROPERTY_LAST_MODIFIED = "LastModified";
    public static final String PROPERTY_PERSISTENTLY_STARTED = "PersistentlyStarted";
    public static final String PROPERTY_ACTIVATION_POLICY_USED = "ActivationPolicyUsed";
    public static final String BUNDLE_PERSISTENT_PROPERTIES = "bundle-persistent.properties";

    private final File storageDir;
    private final VirtualFile rootFile;
    private final Properties props;
    private final String location;
    private final long bundleId;
    private final int revision;

    static Set<String> requiredProps = new HashSet<String>();
    static {
        requiredProps.add(PROPERTY_BUNDLE_ID);
        requiredProps.add(PROPERTY_BUNDLE_REV);
        requiredProps.add(PROPERTY_BUNDLE_LOCATION);
        requiredProps.add(PROPERTY_LAST_MODIFIED);
    }

    public StorageState(File storageDir, VirtualFile rootFile, Properties props) {
        assert storageDir != null : "Null storageFile";
        assert props != null : "Null properties";
        assert storageDir.isDirectory() : "Not a directory: " + storageDir;

        for (String key : requiredProps) {
            if (props.get(key) == null)
                throw MESSAGES.illegalArgumentRequiredPropertyMissing(key, storageDir);
        }

        this.storageDir = storageDir;
        this.rootFile = rootFile;
        this.props = props;

        this.location = props.getProperty(PROPERTY_BUNDLE_LOCATION);
        this.bundleId = Long.parseLong(props.getProperty(PROPERTY_BUNDLE_ID));
        this.revision = Integer.parseInt(props.getProperty(PROPERTY_BUNDLE_REV));
    }

    public Properties getProperties() {
        return props;
    }

    public File getStorageDir() {
        return storageDir;
    }

    public String getLocation() {
        return location;
    }

    public VirtualFile getRootFile() {
        return rootFile;
    }

    public long getBundleId() {
        return bundleId;
    }

    public int getRevisionId() {
        return revision;
    }

    public long getLastModified() {
        String value = props.getProperty(PROPERTY_LAST_MODIFIED);
        return new Long(value);
    }

    public boolean isPersistentlyStarted() {
        String value = props.getProperty(PROPERTY_PERSISTENTLY_STARTED);
        return value != null ? new Boolean(value) : false;
    }

    public boolean isBundleActivationPolicyUsed() {
        String value = props.getProperty(PROPERTY_ACTIVATION_POLICY_USED);
        return value != null ? new Boolean(value) : false;
    }

    @Override
    public String toString() {
        return "BundleStorageState[id=" + bundleId + ",location=" + location + ",file=" + rootFile + "]";
    }
}