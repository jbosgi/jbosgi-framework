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
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.framework.EnvironmentPlugin;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.v2.spi.AbstractWiring;
import org.jboss.osgi.resolver.v2.spi.FrameworkPreferencesComparator;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Requirement;
import org.osgi.framework.resource.Resource;
import org.osgi.framework.resource.Wire;
import org.osgi.framework.resource.Wiring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The default environment plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class DefaultEnvironmentPlugin extends AbstractPluginService<EnvironmentPlugin> implements EnvironmentPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(DefaultEnvironmentPlugin.class);

    private final List<Resource> resources = new ArrayList<Resource>();
    private final Map<Resource, Wiring> wirings = new HashMap<Resource, Wiring>();

    static void addService(ServiceTarget serviceTarget) {
        DefaultEnvironmentPlugin service = new DefaultEnvironmentPlugin();
        ServiceBuilder<EnvironmentPlugin> builder = serviceTarget.addService(Services.ENVIRONMENT_PLUGIN, service);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultEnvironmentPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
    }

    @Override
    public EnvironmentPlugin getValue() {
        return this;
    }

    @Override
    public synchronized void installResources(Resource... resarr) {
        for (Resource res : resarr) {
            if (resources.contains(res))
                throw new IllegalArgumentException("Resource already installed: " + res);
            resources.add(res);
        }
    }

    @Override
    public synchronized void uninstallResources(Resource... resarr) {
        for (Resource res : resarr) {
            resources.remove(res);
            wirings.remove(res);
        }
    }

    @Override
    public synchronized SortedSet<Capability> findProviders(Requirement req) {
        SortedSet<Capability> result = new TreeSet<Capability>(getComparator());
        for (Resource res : resources) {
            for (Capability cap : res.getCapabilities(req.getNamespace())) {
                if (req.matches(cap)) {
                    result.add(cap);
                }
            }
        }
        return result;
    }

    @Override
    public synchronized Map<Resource, Wiring> applyResolverResults(Map<Resource, List<Wire>> wiremap) {
        Map<Resource, Wiring> result = new HashMap<Resource, Wiring>();
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            Resource res = entry.getKey();
            List<Wire> wires = entry.getValue();
            AbstractWiring reqwiring = (AbstractWiring) getWiring(result, res);
            reqwiring.addRequiredWires(wires);
            for (Wire wire : wires) {
                Resource provider = wire.getProvider();
                AbstractWiring provwiring = (AbstractWiring) getWiring(result, provider);
                provwiring.addProvidedWire(wire);
            }
        }
        for (Map.Entry<Resource, Wiring> entry : result.entrySet()) {
            Resource res = entry.getKey();
            Wiring delta = entry.getValue();
            AbstractWiring wiring = (AbstractWiring) wirings.get(res);
            if (wiring == null) {
                wirings.put(res, delta);
            } else {
                for (Wire wire : delta.getProvidedResourceWires(null)) {
                    wiring.addProvidedWire(wire);
                }
            }
        }
        return result;
    }

    @Override
    public synchronized  void refreshResources(Resource... resarr) {
        for (Resource res : resarr) {
            wirings.remove(res);
        }
    }

    @Override
    public boolean isEffective(Requirement req) {
        return true;
    }

    @Override
    public synchronized Map<Resource, Wiring> getWirings() {
        return Collections.unmodifiableMap(new HashMap<Resource, Wiring>(wirings));
    }

    private Wiring getWiring(Map<Resource, Wiring> result, Resource requirer) {
        Wiring wiring = result.get(requirer);
        if (wiring == null) {
            wiring = new AbstractWiring(requirer);
            result.put(requirer, wiring);
        }
        return wiring;
    }

    private Comparator<Capability> getComparator() {
        return new FrameworkPreferencesComparator() {
            @Override
            protected long getResourceIndex(Resource res) {
                return resources.indexOf(res);
            }

            @Override
            protected Wiring getWiring(Resource res) {
                return wirings.get(res);
            }
        };
    }
}