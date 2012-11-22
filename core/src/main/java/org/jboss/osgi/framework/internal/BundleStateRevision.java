package org.jboss.osgi.framework.internal;

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

import java.net.URL;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.ResourceBuilderException;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XBundleRevisionBuilderFactory;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResourceBuilder;
import org.jboss.osgi.resolver.XResourceBuilderFactory;
import org.jboss.osgi.resolver.spi.AbstractBundleRevision;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.resource.Wiring;

/**
 * An abstract bundle revision.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
abstract class BundleStateRevision extends AbstractBundleRevision {

    private final OSGiMetaData metadata;
    private final FrameworkState frameworkState;
    private final InternalStorageState storageState;

    private ModuleClassLoader moduleClassLoader;

    BundleStateRevision(FrameworkState frameworkState, OSGiMetaData metadata, InternalStorageState storageState) throws BundleException {
        assert frameworkState != null : "Null frameworkState";
        assert metadata != null : "Null metadata";
        assert storageState != null : "Null storageState";

        this.frameworkState = frameworkState;
        this.storageState = storageState;
        this.metadata = metadata;

        // Initialize the bundle caps/reqs
        try {
            final BundleStateRevision brev = this;
            XBundleRevisionBuilderFactory factory = new XBundleRevisionBuilderFactory() {
                public XBundleRevision createResource() {
                    return brev;
                }
            };
            XResourceBuilder builder = XResourceBuilderFactory.create(factory);
            builder.loadFrom(metadata).getResource();
        } catch (ResourceBuilderException ex) {
            throw new BundleException(ex.getMessage(), ex);
        }

        addAttachment(StorageState.class, storageState);
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    String getCanonicalName() {
        return getSymbolicName() + ":" + getVersion();
    }

    int getRevisionId() {
        return storageState.getRevisionId();
    }

    OSGiMetaData getOSGiMetaData() {
        return metadata;
    }

    InternalStorageState getStorageState() {
        return storageState;
    }

    abstract String getLocation();

    abstract Class<?> loadClass(String className) throws ClassNotFoundException;

    abstract URL getLocalizationEntry(String path);

    @Override
    public XBundle getBundle() {
        return (XBundle) getAttachment(Bundle.class);
    }

    @Override
    public ModuleIdentifier getModuleIdentifier() {
        return getAttachment(ModuleIdentifier.class);
    }

    @Override
    public synchronized ModuleClassLoader getModuleClassLoader() {
        ModuleIdentifier identifier = getModuleIdentifier();
        if (moduleClassLoader == null && identifier != null) {
            try {
                ModuleManagerPlugin moduleManager = frameworkState.getModuleManagerPlugin();
                Module module = moduleManager.loadModule(identifier);
                moduleClassLoader = module.getClassLoader();
            } catch (ModuleLoadException ex) {
                // log the error
                FrameworkLogger.LOGGER.debug("Failed to load module identifier " + identifier, ex);
            }
        }
        return moduleClassLoader;
    }

    void refreshRevision() throws BundleException {
        XEnvironment env = frameworkState.getEnvironment();
        env.refreshResources(this);
        refreshRevisionInternal();
    }

    synchronized void refreshRevisionInternal() {
        removeAttachment(Wiring.class);
        removeAttachment(ModuleIdentifier.class);
        removeAttachment(Module.class);
        moduleClassLoader = null;
    }

    void close() {
        VFSUtils.safeClose(storageState.getRootFile());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getCanonicalName() + "]";
    }
}
