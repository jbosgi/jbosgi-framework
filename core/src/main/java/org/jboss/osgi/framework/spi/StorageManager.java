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
package org.jboss.osgi.framework.spi;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.jboss.osgi.vfs.VirtualFile;

/**
 * An integration point for bundle storage operations
 *
 * @author thomas.diesler@jboss.com
 * @since 26-Nov-2012
 */
public interface StorageManager {

    void initialize(Map<String, Object> props, boolean firstInit) throws IOException;

    StorageState createStorageState(long bundleId, String location, Integer initialStartlevel, VirtualFile rootFile) throws IOException;

    void deleteStorageState(StorageState storageState);

    Set<StorageState> getStorageStates();

    StorageState getStorageState(String location);

    File getStorageDir(long bundleId);

    File getStorageArea();

    File getDataFile(long bundleId, String filename);

}