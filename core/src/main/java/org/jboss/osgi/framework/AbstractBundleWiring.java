/*
 * #%L
 * JBossOSGi Framework Core
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
package org.jboss.osgi.framework;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.spi.AbstractWiring;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Wire;

/**
 * The {@link BundleWiring} implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Feb-2012
 */
class AbstractBundleWiring extends AbstractWiring implements BundleWiring {

    AbstractBundleWiring(XBundleRevision brev, List<Wire> wires) {
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
            if (requirer.getBundle().getState() != Bundle.UNINSTALLED)
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
}