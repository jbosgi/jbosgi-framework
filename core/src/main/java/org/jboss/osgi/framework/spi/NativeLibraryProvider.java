package org.jboss.osgi.framework.spi;
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

import java.io.File;
import java.io.IOException;

/**
 * Provides the local file location for a native library.
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Aug-2010
 */
public interface NativeLibraryProvider {

    /**
     * Get the library name.
     *
     * As it is used in the call to {@link System#loadLibrary(String)}
     *
     * @return the library path
     */
    String getLibraryName();

    /**
     * Get the library path.
     *
     * Relative to the deployment root.
     *
     * @return the library path
     */
    String getLibraryPath();

    /**
     * Get the local library file location.
     *
     * This may be proved lazily.
     *
     * @return The native library file
     * @throws IOException for any error
     */
    File getLibraryLocation() throws IOException;
}
