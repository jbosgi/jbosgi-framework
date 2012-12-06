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
package org.jboss.osgi.framework.spi;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.spi.AbstractHostedCapability;
import org.jboss.osgi.resolver.spi.AbstractWiring;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.HostedCapability;

/**
 * The {@link BundleWiring} implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Feb-2012
 */
public class AbstractBundleWiring extends AbstractWiring implements BundleWiring {

    public AbstractBundleWiring(XBundleRevision brev, List<Wire> required, List<Wire> provided) {
        super(brev, required, provided);
    }

    @Override
    protected HostedCapability getHostedCapability(XCapability cap) {
        return new AbstractHostedBundleCapability((XResource) getResource(), cap);
    }

    @Override
    public boolean isCurrent() {
        Bundle bundle = getBundle();
        BundleRevision current = bundle.adapt(BundleRevision.class);
        return bundle.getState() != Bundle.UNINSTALLED && current == getRevision();
    }

    @Override
    public boolean isInUse() {
        return transistiveInUse(this, new HashSet<BundleWiring>());
    }

    private boolean transistiveInUse(BundleWiring wiring, Set<BundleWiring> visited) {
        if (wiring.isCurrent()) {
            return true;
        }
        if (visited.contains(wiring) == false) {
            visited.add(wiring);
            for (BundleWire wire : wiring.getProvidedWires(null)) {
                BundleRevision requirer = wire.getRequirer();
                BundleWiring reqwiring = requirer.getWiring();
                if (transistiveInUse(reqwiring, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<BundleCapability> getCapabilities(String namespace) {
        List<BundleCapability> result = new ArrayList<BundleCapability>();
        for (Capability cap : getResourceCapabilities(namespace)) {
            result.add((BundleCapability) cap);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<BundleRequirement> getRequirements(String namespace) {
        List<BundleRequirement> result = new ArrayList<BundleRequirement>();
        for (Requirement req : getResourceRequirements(namespace)) {
            result.add((BundleRequirement) req);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<BundleWire> getProvidedWires(String namespace) {
        List<BundleWire> providedWires = new ArrayList<BundleWire>();
        for (Wire wire : super.getProvidedResourceWires(namespace)) {
            providedWires.add((BundleWire) wire);
        }
        return Collections.unmodifiableList(providedWires);
    }

    @Override
    public List<BundleWire> getRequiredWires(String namespace) {
        List<BundleWire> requiredWires = new ArrayList<BundleWire>();
        for (Wire wire : super.getRequiredResourceWires(namespace)) {
            requiredWires.add((BundleWire) wire);
        }
        return Collections.unmodifiableList(requiredWires);
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
        XBundleRevision brev = (XBundleRevision) getRevision();
        return brev.getModuleClassLoader();
    }

    @Override
    public List<URL> findEntries(String path, String filePattern, int options) {
        List<URL> result = new ArrayList<URL>();
        XBundleRevision brev = (XBundleRevision)getRevision();
        Enumeration<URL> entries = brev.findEntries(path, filePattern, options == FINDENTRIES_RECURSE);
        while (entries.hasMoreElements()) {
            result.add(entries.nextElement());
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public Collection<String> listResources(String path, String filePattern, int options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getBundle() {
        return getRevision().getBundle();
    }

    static class AbstractHostedBundleCapability extends AbstractHostedCapability implements BundleCapability {

        AbstractHostedBundleCapability(XResource resource, XCapability capability) {
            super(resource, capability);
        }

        @Override
        public BundleRevision getRevision() {
            return (BundleRevision) super.getResource();
        }

        @Override
        public BundleRevision getResource() {
            return (BundleRevision) super.getResource();
        }
    }
}
