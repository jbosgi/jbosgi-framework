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
package org.jboss.osgi.framework.bundle;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.ResolverPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XModuleIdentity;
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
public abstract class AbstractRevision {

    static final Logger log = Logger.getLogger(AbstractRevision.class);

    private final int revision;
    private final AbstractBundle bundleState;
    private final OSGiMetaData metadata;
    private XModule resolverModule;
    private boolean refreshAllowed;

    // Cache commonly used plugins
    private final ModuleManagerPlugin moduleManager;
    private final ResolverPlugin resolverPlugin;

    AbstractRevision(AbstractBundle bundleState, OSGiMetaData metadata, XModule resModule, int revision) throws BundleException {
        if (bundleState == null)
            throw new IllegalArgumentException("Null bundleState");
        if (metadata == null)
            throw new IllegalArgumentException("Null metadata");

        this.bundleState = bundleState;
        this.metadata = metadata;
        this.revision = revision;

        BundleManager bundleManager = getBundleManager();
        this.moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
        this.resolverPlugin = bundleManager.getPlugin(ResolverPlugin.class);

        // Create the resolver module
        if (resModule == null) {
            resModule = createResolverModule(metadata);
            refreshAllowed = true;
        }

        resModule.addAttachment(AbstractRevision.class, this);
        resModule.addAttachment(Bundle.class, bundleState);
        refreshRevisionInternal(resModule);

        resolverModule = resModule;
    }

    void refreshRevision(OSGiMetaData metadata) throws BundleException {
        // In case of an externally provided XModule, we generate dummy OSGiMetaData
        // with considerable data loss. A new XModule cannot get created from that
        // OSGiMetaData. An acceptable fix would be to allow refresh on the XModule
        // or otherwise create a clone of the original XModule.
        if (refreshAllowed == false)
            throw new IllegalStateException("External XModule, refresh not allowed");

        resolverModule = createResolverModule(metadata);
        refreshRevisionInternal(resolverModule);
    }

    XModule createResolverModule(OSGiMetaData metadata) throws BundleException {
        String symbolicName = metadata.getBundleSymbolicName();
        Version version = metadata.getBundleVersion();
        int modulerev = revision;

        // An UNINSTALLED module with active wires may still be registered in with the Resolver
        // Make sure we have a unique module identifier
        XModuleBuilder builder = resolverPlugin.getModuleBuilder();
        builder.createModule(symbolicName, version, modulerev);
        XModuleIdentity moduleId = builder.getModuleIdentity();
        XModule resModule = resolverPlugin.getModuleById(moduleId);
        while (resModule != null) {
            builder = resolverPlugin.getModuleBuilder();
            builder.createModule(symbolicName, version, modulerev += 100);
            moduleId = builder.getModuleIdentity();
            resModule = resolverPlugin.getModuleById(moduleId);
        }

        resModule = builder.createModule(metadata, modulerev).getModule();
        resModule.addAttachment(AbstractRevision.class, this);
        resModule.addAttachment(Bundle.class, bundleState);
        return resModule;
    }

    abstract void refreshRevisionInternal(XModule resModule);

    public int getRevisionCount() {
        return revision;
    }

    public XModule getResolverModule() {
        return resolverModule;
    }

    public AbstractBundle getBundleState() {
        return bundleState;
    }

    public ModuleIdentifier getModuleIdentifier() {
        return moduleManager.getModuleIdentifier(resolverModule);
    }

    public ModuleClassLoader getModuleClassLoader() {
        ModuleIdentifier identifier = getModuleIdentifier();
        Module module = moduleManager.getModule(identifier);
        return module != null ? module.getClassLoader() : null;
    }

    BundleManager getBundleManager() {
        return bundleState.getBundleManager();
    }

    OSGiMetaData getOSGiMetaData() {
        return metadata;
    }

    String getSymbolicName() {
        return bundleState.getSymbolicName();
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

    @Override
    public String toString() {
        return "Revision[" + resolverModule.getModuleId() + "]";
    }
}
