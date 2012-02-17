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
import org.jboss.osgi.framework.EnvironmentPlugin;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.v2.spi.AbstractEnvironment;
import org.jboss.osgi.resolver.v2.spi.FrameworkPreferencesComparator;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Requirement;
import org.osgi.framework.resource.Resource;
import org.osgi.framework.resource.Wire;
import org.osgi.framework.resource.Wiring;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import static org.osgi.framework.resource.ResourceConstants.IDENTITY_TYPE_FRAGMENT;
import static org.osgi.framework.resource.ResourceConstants.WIRING_HOST_NAMESPACE;

/**
 * The default delegate plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class DefaultEnvironmentPlugin extends AbstractPluginService<EnvironmentPlugin> implements EnvironmentPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(DefaultEnvironmentPlugin.class);

    private final AbstractEnvironment delegate;

    static void addService(ServiceTarget serviceTarget) {
        DefaultEnvironmentPlugin service = new DefaultEnvironmentPlugin();
        ServiceBuilder<EnvironmentPlugin> builder = serviceTarget.addService(Services.ENVIRONMENT_PLUGIN, service);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultEnvironmentPlugin() {
        delegate = new AbstractEnvironment() {
            @Override
            public Comparator<Capability> getComparator() {
                final AbstractEnvironment env = this;
                return new FrameworkPreferencesComparator() {
                    @Override
                    protected Wiring getWiring(Resource res) {
                        return env.getWiring(res);
                    }

                    @Override
                    public long getResourceIndex(Resource res) {
                        return env.getResourceIndex(res);
                    }
                };
            }
        };
    }

    @Override
    public EnvironmentPlugin getValue() {
        return this;
    }

    @Override
    public void installResources(Resource... resarr) {
        delegate.installResources(resarr);
    }

    @Override
    public void uninstallResources(Resource... resarr) {
        delegate.uninstallResources(resarr);
    }

    @Override
    public SortedSet<Capability> findProviders(Requirement req) {
        return delegate.findProviders(req);
    }

    @Override
    public Collection<? extends Resource> findAttachableFragments(Collection<? extends Capability> hostcaps) {
        Set<Resource> result = new HashSet<Resource>();
        for (Resource res : delegate.getResources(IDENTITY_TYPE_FRAGMENT)) {
            Requirement req = res.getRequirements(WIRING_HOST_NAMESPACE).get(0);
            for (Capability cap : hostcaps) {
                if (req.matches(cap)) {
                    result.add(res);
                }
            }
        }
        log.debugf("attachable fragments: %s", result);
        return result;
    }

    @Override
    public boolean isEffective(Requirement req) {
        return delegate.isEffective(req);
    }

    @Override
    public Map<Resource, Wiring> getWirings() {
        return delegate.getWirings();
    }

    @Override
    public void refreshResources(Resource... resarr) {
        delegate.refreshResources(resarr);
    }

    @Override
    public synchronized Map<Resource, Wiring> applyResolverResults(Map<Resource, List<Wire>> wiremap) {
        // Construct the resource wiring map
        Map<Resource, Wiring> result = delegate.getResourceWiringMap(wiremap);

        // Attach the fragments to host
        attachFragmentsToHost(wiremap);

        // Resolve native code libraries if there are any
        //resolveNativeCodeLibraries(resolved);

        // For every resolved host bundle create the {@link ModuleSpec}
        //addModules(resolved);

        // For every resolved host bundle load the module. This creates the {@link ModuleClassLoader}
        //loadModules(resolved);

        // Change the bundle state to RESOLVED
        //setBundleToResolved(resolved);

        // Apply the resource wiring map
        delegate.applyResourceWiringMap(result);

        return result;
    }


    private void attachFragmentsToHost(Map<Resource, List<Wire>> wiremap) {
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            Resource res = entry.getKey();
            if (res instanceof FragmentBundleRevision) {
                FragmentBundleRevision fragRev = (FragmentBundleRevision) res;
                for (Wire wire : entry.getValue()) {
                    Capability cap = wire.getCapability();
                    if (WIRING_HOST_NAMESPACE.equals(cap.getNamespace())) {
                        HostBundleRevision hostRev = (HostBundleRevision) cap.getResource();
                        fragRev.attachToHost(hostRev);
                    }
                }
            }
        }
    }
}