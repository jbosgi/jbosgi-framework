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

import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.spi.AbstractEnvironment;
import org.jboss.osgi.resolver.spi.AbstractWiring;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;

/**
 * The default {@link XEnvironment} plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class DefaultEnvironmentPlugin extends AbstractEnvironment implements Service<XEnvironment> {

    // Provide logging
    final Logger log = Logger.getLogger(DefaultEnvironmentPlugin.class);

    static void addService(ServiceTarget serviceTarget) {
        DefaultEnvironmentPlugin service = new DefaultEnvironmentPlugin();
        ServiceBuilder<XEnvironment> builder = serviceTarget.addService(Services.ENVIRONMENT, service);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultEnvironmentPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public XEnvironment getValue() {
        return this;
    }

    @Override
    public Wiring createWiring(Resource res, List<Wire> wires) {
        Wiring result;
        if (res instanceof AbstractBundleRevision) {
            AbstractBundleRevision brev = (AbstractBundleRevision) res;
            result = new AbstractBundleWiring(brev, wires);
        } else {
            result = new AbstractWiring(res, wires);
        }
        return result;
    }

}