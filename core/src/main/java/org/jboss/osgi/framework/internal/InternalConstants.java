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
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.resolver.XAttachmentKey;
import org.osgi.framework.Bundle;

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

    /** The bundle attachment key */
    XAttachmentKey<Bundle> BUNDLE_KEY = XAttachmentKey.create(Bundle.class);
    /** The lock method attachment key */
    XAttachmentKey<Method> LOCK_METHOD_KEY = XAttachmentKey.create(Method.class);
    /** The module attachment key */
    XAttachmentKey<Module> MODULE_KEY = XAttachmentKey.create(Module.class);
    /** The module identifier attachment key */
    XAttachmentKey<ModuleIdentifier> MODULE_IDENTIFIER_KEY = XAttachmentKey.create(ModuleIdentifier.class);
    /** The storage state attachment key */
    XAttachmentKey<StorageState> STORAGE_STATE_KEY = XAttachmentKey.create(StorageState.class);
}