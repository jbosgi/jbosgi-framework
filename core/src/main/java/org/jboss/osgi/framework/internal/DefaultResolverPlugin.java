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
import org.osgi.framework.resource.Resource;
import org.osgi.framework.resource.Wire;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.Resolver;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 06-Jul-2009
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
    public Map<Resource, List<Wire>> resolve(Collection<? extends Resource> mandatoryResources, Collection<? extends Resource> optionalResources) throws ResolutionException {
        EnvironmentPlugin environment = injectedEnvironmentPlugin.getValue();
        return resolver.resolve(environment, mandatoryResources, optionalResources);
    }

    @Override
    public void resolveAndApply(Collection<? extends Resource> mandatoryResources, Collection<? extends Resource> optionalResources) throws ResolutionException {
        EnvironmentPlugin environment = injectedEnvironmentPlugin.getValue();
        Map<Resource, List<Wire>> result = resolver.resolve(environment, mandatoryResources, optionalResources);
        environment.applyResolverResults(result);
    }
}