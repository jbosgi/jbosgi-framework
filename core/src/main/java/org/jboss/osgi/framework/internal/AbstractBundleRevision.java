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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.framework.EnvironmentPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.v2.XIdentityCapability;
import org.jboss.osgi.resolver.v2.XResourceBuilder;
import org.jboss.osgi.resolver.v2.spi.AbstractBundleCapability;
import org.jboss.osgi.resolver.v2.spi.AbstractBundleRequirement;
import org.jboss.osgi.resolver.v2.spi.AbstractResource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Requirement;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;

import static org.osgi.framework.resource.ResourceConstants.IDENTITY_TYPE_FRAGMENT;

/**
 * An abstract bundle revision.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
abstract class AbstractBundleRevision extends AbstractResource implements BundleRevision {

    static final Logger log = Logger.getLogger(AbstractBundleRevision.class);

    private final int revision;
    private final AbstractBundleState bundleState;
    private final OSGiMetaData metadata;
    private Map<String, List<BundleCapability>> bundleCapabilities;
    private Map<String, List<BundleRequirement>> bundleRequirements;
    private BundleWiring wiring;
    private XModule resModule;

    AbstractBundleRevision(AbstractBundleState bundleState, OSGiMetaData metadata, XModule resModule, int revision) throws BundleException {
        if (bundleState == null)
            throw new IllegalArgumentException("Null bundleState");
        if (metadata == null)
            throw new IllegalArgumentException("Null metadata");

        this.bundleState = bundleState;
        this.metadata = metadata;
        this.revision = revision;
        this.resModule = resModule;

        // Initialize the bundle caps/reqs
        XResourceBuilder.create(this).load(metadata);

        // Add bidirectional one-to-one association between a revision and a resolver module
        resModule.addAttachment(AbstractBundleRevision.class, this);
    }

    @Override
    public String getSymbolicName() {
        return metadata.getBundleSymbolicName();
    }

    @Override
    public Version getVersion() {
        return metadata.getBundleVersion();
    }

    @Override
    public List<BundleCapability> getDeclaredCapabilities(String namespace) {
        if (bundleCapabilities == null) {
            Map<String, List<BundleCapability>> capmap = new HashMap<String, List<BundleCapability>>();
            List<BundleCapability> allcaps = new ArrayList<BundleCapability>();
            for (Capability cap : getCapabilities(null)) {
                String capns = cap.getNamespace();
                List<BundleCapability> caps = capmap.get(capns);
                if (caps == null) {
                    caps = new ArrayList<BundleCapability>();
                    capmap.put(capns, caps);
                }
                BundleCapability bcap = new AbstractBundleCapability(this, capns, cap.getAttributes(), cap.getDirectives());
                allcaps.add(bcap);
                caps.add(bcap);
            }
            capmap.put(null, allcaps);
            bundleCapabilities = capmap;
        }
        List<BundleCapability> result = bundleCapabilities.get(namespace);
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<BundleRequirement> getDeclaredRequirements(String namespace) {
        if (bundleRequirements == null) {
            Map<String, List<BundleRequirement>> reqmap = new HashMap<String, List<BundleRequirement>>();
            List<BundleRequirement> allreqs = new ArrayList<BundleRequirement>();
            for (Requirement req : getRequirements(null)) {
                String reqns = req.getNamespace();
                List<BundleRequirement> reqs = reqmap.get(reqns);
                if (reqs == null) {
                    reqs = new ArrayList<BundleRequirement>();
                    reqmap.put(reqns, reqs);
                }
                BundleRequirement bcap = new AbstractBundleRequirement(this, reqns, req.getAttributes(), req.getDirectives());
                allreqs.add(bcap);
                reqs.add(bcap);
            }
            reqmap.put(null, allreqs);
            bundleRequirements = reqmap;
        }
        List<BundleRequirement> result = bundleRequirements.get(namespace);
        return Collections.unmodifiableList(result);
    }

    @Override
    public int getTypes() {
        XIdentityCapability idcap = getIdentityCapability();
        boolean isfragment = IDENTITY_TYPE_FRAGMENT.equals(idcap.getType());
        return isfragment ? TYPE_FRAGMENT : 0;
    }

    @Override
    public BundleWiring getWiring() {
        return wiring;
    }

    void setWiring(BundleWiring wiring) {
        this.wiring = wiring;
    }

    @Override
    public Bundle getBundle() {
        return bundleState;
    }

    int getRevisionId() {
        return revision;
    }

    XModule getResolverModule() {
        return resModule;
    }

    AbstractBundleState getBundleState() {
        return bundleState;
    }

    OSGiMetaData getOSGiMetaData() {
        return metadata;
    }

    abstract Class<?> loadClass(String className) throws ClassNotFoundException;

    abstract URL getResource(String resourceName);

    abstract Enumeration<URL> getResources(String resourceName) throws IOException;

    abstract Enumeration<String> getEntryPaths(String path);

    abstract URL getEntry(String path);

    abstract Enumeration<URL> findEntries(String path, String pattern, boolean recurse);

    abstract String getLocation();

    abstract URL getLocalizationEntry(String path);

    ModuleIdentifier getModuleIdentifier() {
        try {
            ModuleManagerPlugin moduleManager = bundleState.getFrameworkState().getModuleManagerPlugin();
            return moduleManager.getModuleIdentifier(resModule);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot get module identifier for: " + resModule, ex);
        }
    }

    ModuleClassLoader getModuleClassLoader() throws ModuleLoadException {
        ModuleIdentifier identifier = getModuleIdentifier();
        ModuleManagerPlugin moduleManager = bundleState.getFrameworkState().getModuleManagerPlugin();
        Module module = moduleManager.loadModule(identifier);
        return module.getClassLoader();
    }

    void refreshRevision() throws BundleException {
        // [TODO] In case of an externally provided XModule, we generate dummy OSGiMetaData
        // with considerable data loss. A new XModule cannot get created from that
        // OSGiMetaData. An acceptable fix would be to allow refresh on the XModule
        // or otherwise create a clone of the original XModule.
        // if (refreshAllowed == false)
        // throw new IllegalStateException("External XModule, refresh not allowed");

        createResolverModule(getBundleState(), getOSGiMetaData());

        EnvironmentPlugin envPlugin = bundleState.getFrameworkState().getEnvironmentPlugin();
        envPlugin.getEnvironment().refreshResources(this);
        refreshRevisionInternal();
    }

    abstract void refreshRevisionInternal();

    void createResolverModule(AbstractBundleState bundleState, OSGiMetaData metadata) throws BundleException {
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
        LegacyResolverPlugin legacyResolver = bundleState.getFrameworkState().getLegacyResolverPlugin();
        XModuleBuilder builder = legacyResolver.getModuleBuilder();
        resModule = builder.createModule(metadata, modulerev).getModule();
        resModule.addAttachment(AbstractBundleRevision.class, this);
        resModule.addAttachment(Bundle.class, bundleState);
    }

    @Override
    public String toString() {
        return "Revision[" + resModule.getModuleId() + "]";
    }
}
