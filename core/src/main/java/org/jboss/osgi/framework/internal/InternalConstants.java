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


import org.jboss.modules.Module;
import org.jboss.osgi.framework.spi.BundleLifecycle;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.spi.AttachmentKey;

/**
 * A collection of propriatary constants.
 *
 * @author thomas.diesler@jboss.com
 * @since 08-Apr-2013
 */
public interface InternalConstants {

    /**
     * Uninstall the bundle internally, skipping the {@link BundleLifecycle} integration.
     */
    int UNINSTALL_INTERNAL = 0x00000100;

    /** The module attachment key */
    AttachmentKey<Module> MODULE_KEY = AttachmentKey.create(Module.class);
    /** The NativeLibraryMetaData attachment key */
    AttachmentKey<NativeLibraryMetaData> NATIVE_LIBRARY_METADATA_KEY = AttachmentKey.create(NativeLibraryMetaData.class);
    /** The revision identifier attachment key */
    AttachmentKey<RevisionIdentifier> REVISION_IDENTIFIER_KEY = AttachmentKey.create(RevisionIdentifier.class);
}