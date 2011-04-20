/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * An abstract bundle revision.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
abstract class AbstractBundleRevision {

    static final Logger log = Logger.getLogger(AbstractBundleRevision.class);

    private final int revision;
    private final AbstractBundleState bundleState;
    private final OSGiMetaData metadata;
    private XModule resolverModule;

    AbstractBundleRevision(AbstractBundleState bundleState, OSGiMetaData metadata, XModule resModule, int revision) throws BundleException {
        if (bundleState == null)
            throw new IllegalArgumentException("Null bundleState");
        if (metadata == null)
            throw new IllegalArgumentException("Null metadata");
        if (resModule == null)
            throw new IllegalArgumentException("Null resModule");

        this.bundleState = bundleState;
        this.metadata = metadata;
        this.revision = revision;
        this.resolverModule = resModule;

        // Add bidirectional one-to-one association between a revision and a resolver module
        resModule.addAttachment(AbstractBundleRevision.class, this);
    }

    int getRevisionId() {
        return revision;
    }

    XModule getResolverModule() {
        return resolverModule;
    }

    AbstractBundleState getBundleState() {
        return bundleState;
    }

    OSGiMetaData getOSGiMetaData() {
        return metadata;
    }

    Version getVersion() {
        return metadata.getBundleVersion();
    }

    abstract Class<?> loadClass(String name) throws ClassNotFoundException;

    abstract URL getResource(String name);

    abstract Enumeration<URL> getResources(String name) throws IOException;

    abstract Enumeration<String> getEntryPaths(String path);

    abstract URL getEntry(String path);

    abstract Enumeration<URL> findEntries(String path, String pattern, boolean recurse);

    abstract String getLocation();

    abstract URL getLocalizationEntry(String path);

    ModuleIdentifier getModuleIdentifier() {
        try {
            ModuleManagerPlugin moduleManager = bundleState.getFrameworkState().getModuleManagerPlugin();
            return moduleManager.getModuleIdentifier(resolverModule);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot get module identifier for: " + resolverModule, ex);
        }
    }

    ModuleClassLoader getModuleClassLoader() throws ModuleLoadException {
        ModuleIdentifier identifier = getModuleIdentifier();
        ModuleManagerPlugin moduleManager = bundleState.getFrameworkState().getModuleManagerPlugin();
        Module module = moduleManager.loadModule(identifier);
        return module.getClassLoader();
    }

    void refreshRevision(OSGiMetaData metadata) throws BundleException {
        // [TODO] In case of an externally provided XModule, we generate dummy OSGiMetaData
        // with considerable data loss. A new XModule cannot get created from that
        // OSGiMetaData. An acceptable fix would be to allow refresh on the XModule
        // or otherwise create a clone of the original XModule.
        // if (refreshAllowed == false)
        // throw new IllegalStateException("External XModule, refresh not allowed");

        resolverModule = createResolverModule(getBundleState(), metadata);
        refreshRevisionInternal(resolverModule);
    }

    abstract void refreshRevisionInternal(XModule resModule);

    XModule createResolverModule(AbstractBundleState bundleState, OSGiMetaData metadata) throws BundleException {
        final String symbolicName = metadata.getBundleSymbolicName();
        final Version version = metadata.getBundleVersion();

        int modulerev = getRevisionId();

        // An UNINSTALLED module with active wires may still be registered in with the Resolver
        // Make sure we have a unique module identifier
        BundleManager bundleManager = bundleState.getBundleManager();
        for (AbstractBundleState aux : bundleManager.getBundles(symbolicName, version.toString())) {
            if (aux.getState() == Bundle.UNINSTALLED) {
                XModule resModule = aux.getResolverModule();
                int auxrev = resModule.getModuleId().getRevision();
                modulerev = Math.max(modulerev + 100, auxrev + 100);
            }
        }
        ResolverPlugin resolverPlugin = bundleState.getFrameworkState().getResolverPlugin();
        XModuleBuilder builder = resolverPlugin.getModuleBuilder();
        XModule resModule = builder.createModule(metadata, modulerev).getModule();
        resModule.addAttachment(AbstractBundleRevision.class, this);
        resModule.addAttachment(Bundle.class, bundleState);
        return resModule;
    }

    @Override
    public String toString() {
        return "Revision[" + resolverModule.getModuleId() + "]";
    }
}
