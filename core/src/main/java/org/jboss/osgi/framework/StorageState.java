package org.jboss.osgi.framework;
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
    public static final String PROPERTY_START_LEVEL = "StartLevel";
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
        requiredProps.add(PROPERTY_START_LEVEL);
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
        return Long.parseLong(value);
    }

    public boolean isPersistentlyStarted() {
        String value = props.getProperty(PROPERTY_PERSISTENTLY_STARTED);
        return Boolean.parseBoolean(value);
    }

    public boolean isBundleActivationPolicyUsed() {
        String value = props.getProperty(PROPERTY_ACTIVATION_POLICY_USED);
        return Boolean.parseBoolean(value);
    }

    public int getStartLevel() {
        String value = props.getProperty(PROPERTY_START_LEVEL);
        return Integer.parseInt(value);
    }

    @Override
    public String toString() {
        int startlevel = getStartLevel();
        boolean started = isPersistentlyStarted();
        return "StorageState[id=" + bundleId + ",rev=" + revision + ",startlevel=" + startlevel + ",started=" + started + ",location=" + location + "]";
    }
}