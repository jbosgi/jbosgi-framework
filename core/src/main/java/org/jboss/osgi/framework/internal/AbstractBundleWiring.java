/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.resolver.spi.AbstractWiring;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Wire;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * The {@link BundleWiring} implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Feb-2012
 */
class AbstractBundleWiring extends AbstractWiring implements BundleWiring {

    // Provide logging
    static final Logger log = Logger.getLogger(AbstractBundleWiring.class);

    AbstractBundleWiring(AbstractBundleRevision brev, List<Wire> wires) {
        super(brev, wires);
    }

    @Override
    public boolean isCurrent() {
        return true;
    }

    @Override
    public boolean isInUse() {
        for (Wire wire : getProvidedResourceWires(null)) {
            BundleRevision requirer = (BundleRevision) wire.getRequirer();
            AbstractBundleState importer = AbstractBundleState.assertBundleState(requirer.getBundle());
            if (importer.getState() != Bundle.UNINSTALLED)
                return true;
        }
        return false;
    }

    @Override
    public List<BundleCapability> getCapabilities(String namespace) {
        return getRevision().getDeclaredCapabilities(namespace);
    }

    @Override
    public List<BundleRequirement> getRequirements(String namespace) {
        return getRevision().getDeclaredRequirements(namespace);
    }

    @Override
    public List<BundleWire> getProvidedWires(String namespace) {
        throw new NotImplementedException();
    }

    @Override
    public List<BundleWire> getRequiredWires(String namespace) {
        throw new NotImplementedException();
    }

    @Override
    public BundleRevision getRevision() {
        return getResource();
    }

    @Override
    public BundleRevision getResource() {
        return (BundleRevision) super.getResource();
    }

    @Override
    public ClassLoader getClassLoader() {
        try {
            AbstractBundleRevision brev = (AbstractBundleRevision) getRevision();
            return brev.getModuleClassLoader();
        } catch (ModuleLoadException e) {
            return null;
        }
    }

    @Override
    public List<URL> findEntries(String path, String filePattern, int options) {
        List<URL> result = new ArrayList<URL>();
        AbstractBundleRevision brev = (AbstractBundleRevision) getRevision();
        Enumeration<URL> entries = brev.findEntries(path, filePattern, options == FINDENTRIES_RECURSE);
        while(entries.hasMoreElements()) {
            result.add(entries.nextElement());
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Collection<String> listResources(String path, String filePattern, int options) {
        throw new NotImplementedException();
    }

    @Override
    public Bundle getBundle() {
        AbstractBundleRevision brev = (AbstractBundleRevision) getRevision();
        return brev.getBundle();
    }

    private AbstractBundleWire toBundleWire(Wire wire) {
        BundleCapability bcap = toBundleCapability(wire.getCapability());
        BundleRequirement breq = toBundleRequirement(wire.getRequirement());
        BundleRevision provider = (BundleRevision) wire.getProvider();
        BundleRevision requirer = (BundleRevision) wire.getRequirer();
        return new AbstractBundleWire(bcap, breq, provider, requirer);
    }

   private BundleCapability toBundleCapability(Capability cap) {
        BundleRevision brev = (BundleRevision) cap.getResource();
        return new AbstractBundleCapability(brev, cap.getNamespace(), cap.getAttributes(), cap.getDirectives());
    }

    private BundleRequirement toBundleRequirement(Requirement req) {
        BundleRevision brev = (BundleRevision) req.getResource();
        return new AbstractBundleRequirement(brev, req.getNamespace(), req.getAttributes(), req.getDirectives());
    }
}