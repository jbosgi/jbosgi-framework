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

import java.net.URL;
import java.util.Dictionary;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.framework.FrameworkMessages;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.ResourceBuilderException;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XBundleRevisionBuilder;
import org.jboss.osgi.resolver.XBundleRevisionBuilderFactory;
import org.jboss.osgi.resolver.spi.AbstractBundleRevision;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.BundleException;

/**
 * An abstract bundle revision.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
abstract class BundleStateRevision extends AbstractBundleRevision {

    private final FrameworkState frameworkState;
    private final OSGiMetaData metadata;
    private final StorageState storageState;

    private ModuleClassLoader moduleClassLoader;
    private Dictionary<String, String> headersOnUninstall;
    private String canonicalName;

    BundleStateRevision(FrameworkState frameworkState, OSGiMetaData metadata, StorageState storageState) throws BundleException {
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
                @Override
                public XBundleRevision createResource() {
                    return brev;
                }
            };
            XBundleRevisionBuilder builder = XBundleRevisionBuilderFactory.create(factory);
            builder.loadFrom(metadata).getResource();
        } catch (ResourceBuilderException ex) {
            throw new BundleException(ex.getMessage(), ex);
        }

        putAttachment(IntegrationConstants.STORAGE_STATE_KEY, storageState);
        putAttachment(IntegrationConstants.OSGI_METADATA_KEY, metadata);
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    BundleManagerPlugin getBundleManager() {
        return frameworkState.getBundleManager();
    }

    @Override
    public String getCanonicalName() {
        if (canonicalName == null) {
            String name = metadata.getBundleSymbolicName();
            name = name != null ? name : metadata.getBundleName();
            canonicalName = name + ":" + metadata.getBundleVersion();
        }
        return canonicalName;
    }

    int getRevisionId() {
        return storageState.getRevisionId();
    }

    OSGiMetaData getOSGiMetaData() {
        return metadata;
    }

    StorageState getStorageState() {
        return storageState;
    }

    Dictionary<String, String> getHeadersOnUninstall() {
        return headersOnUninstall;
    }

    void setHeadersOnUninstall(Dictionary<String, String> headers) {
        this.headersOnUninstall = headers;
    }

    abstract String getLocation();

    abstract Class<?> loadClass(String className) throws ClassNotFoundException;

    abstract URL getLocalizationEntry(String path);

    @Override
    public XBundle getBundle() {
        return getAttachment(IntegrationConstants.BUNDLE_KEY);
    }

    @Override
    public ModuleIdentifier getModuleIdentifier() {
        return getAttachment(IntegrationConstants.MODULE_IDENTIFIER_KEY);
    }

    @Override
    public synchronized ModuleClassLoader getModuleClassLoader() {
        if (moduleClassLoader == null && getBundle().isResolved()) {
            ModuleIdentifier identifier = getModuleIdentifier();
            try {
                ModuleManager moduleManager = getFrameworkState().getModuleManager();
                Module module = moduleManager.loadModule(identifier);
                moduleClassLoader = module.getClassLoader();
            } catch (ModuleLoadException ex) {
                throw FrameworkMessages.MESSAGES.illegalStateCannotLoadModule(ex, identifier);
            }
        }
        return moduleClassLoader;
    }

    synchronized void resetModuleClassLoader() {
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
