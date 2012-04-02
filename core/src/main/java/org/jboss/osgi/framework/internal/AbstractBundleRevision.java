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
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.ResourceBuilderException;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XResourceBuilderFactory;
import org.jboss.osgi.resolver.spi.AbstractResource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wiring;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;

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

    AbstractBundleRevision(AbstractBundleState bundleState, OSGiMetaData metadata, int revision) throws BundleException {
        if (bundleState == null)
            throw new IllegalArgumentException("Null bundleState");
        if (metadata == null)
            throw new IllegalArgumentException("Null metadata");

        this.bundleState = bundleState;
        this.metadata = metadata;
        this.revision = revision;

        // Initialize the bundle caps/reqs
        try {
            final AbstractBundleRevision brev = this;
            XResourceBuilderFactory factory = new XResourceBuilderFactory() {
                public AbstractResource createResource() {
                    return brev;
                }
            };
            XResourceBuilderFactory.create(factory).loadFrom(metadata);
        } catch (ResourceBuilderException ex) {
            throw new BundleException(ex. getMessage(), ex);
        }
    }

    /**
     * Assert that the given resource is an instance of {@link AbstractBundleRevision}
     *
     * @throws IllegalArgumentException if the given resource is not an instance of {@link AbstractBundleRevision}
     */
    static AbstractBundleRevision assertBundleRevision(Resource resource) {
        if (resource instanceof AbstractBundleRevision == false)
            throw new IllegalArgumentException("Not an AbstractBundleRevision: " + resource);

        return (AbstractBundleRevision) resource;
    }

    @Override
    public String getSymbolicName() {
        return metadata.getBundleSymbolicName();
    }

    @Override
    public Version getVersion() {
        return metadata.getBundleVersion();
    }

    String getCanonicalName() {
        return getSymbolicName() + ":" + getVersion();
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
        boolean isfragment = IdentityNamespace.TYPE_FRAGMENT.equals(idcap.getType());
        return isfragment ? TYPE_FRAGMENT : 0;
    }

    @Override
    public BundleWiring getWiring() {
        return (BundleWiring) getAttachment(Wiring.class);
    }

    @Override
    public Bundle getBundle() {
        return bundleState;
    }

    int getRevisionId() {
        return revision;
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

    boolean isResolved() {
        return getWiring() != null;
    }

    ModuleIdentifier getModuleIdentifier() {
        return getAttachment(ModuleIdentifier.class);
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

        XEnvironment env = bundleState.getFrameworkState().getEnvironment();
        env.refreshResources(this);
        refreshRevisionInternal();
    }

    void refreshRevisionInternal() {
        removeAttachment(Wiring.class);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getCanonicalName() + "]";
    }
}
