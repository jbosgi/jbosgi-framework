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
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.EnvironmentPlugin;
import org.jboss.osgi.framework.ResolverPlugin;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.v2.FelixResolver;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Resource;
import org.osgi.framework.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.Resolver;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.osgi.framework.resource.ResourceConstants.WIRING_HOST_NAMESPACE;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class DefaultResolverPlugin extends AbstractPluginService<ResolverPlugin> implements ResolverPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(DefaultResolverPlugin.class);

    private final InjectedValue<EnvironmentPlugin> injectedEnvironmentPlugin = new InjectedValue<EnvironmentPlugin>();
    private Resolver resolver;

    static void addService(ServiceTarget serviceTarget) {
        DefaultResolverPlugin service = new DefaultResolverPlugin();
        ServiceBuilder<ResolverPlugin> builder = serviceTarget.addService(Services.RESOLVER_PLUGIN, service);
        builder.addDependency(Services.ENVIRONMENT_PLUGIN, EnvironmentPlugin.class, service.injectedEnvironmentPlugin);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultResolverPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        resolver = new FelixResolver();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        resolver = null;
    }

    @Override
    public ResolverPlugin getValue() {
        return this;
    }

    @Override
    public Map<Resource, List<Wire>> resolve(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) throws ResolutionException {
        EnvironmentPlugin envPlugin = injectedEnvironmentPlugin.getValue();
        Collection<Capability> hostcaps = getHostCapabilities(mandatory);
        if (hostcaps.isEmpty() == false) {
            Collection<Resource> allOptional = new HashSet<Resource>();
            allOptional.addAll(optional != null ? optional : Collections.EMPTY_SET);
            allOptional.addAll(envPlugin.findAttachableFragments(hostcaps));
            optional = allOptional;
        }
        return resolver.resolve(envPlugin.getEnvironment(), mandatory, optional);
    }

    private Collection<Capability> getHostCapabilities(Collection<? extends Resource> resources) {
        Collection<Capability> result = new HashSet<Capability>();
        for (Resource res : resources) {
            List<Capability> caps = res.getCapabilities(WIRING_HOST_NAMESPACE);
            if (caps.size() == 1)
                result.add(caps.get(0));
        }
        return result;
    }

    @Override
    public boolean resolveAndApply(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) throws ResolutionException {
        Map<Resource, List<Wire>> result = resolve(mandatory, optional);
        EnvironmentPlugin envPlugin = injectedEnvironmentPlugin.getValue();
        envPlugin.getEnvironment().applyResolverResults(result);
        return result.size() > 0;
    }
}