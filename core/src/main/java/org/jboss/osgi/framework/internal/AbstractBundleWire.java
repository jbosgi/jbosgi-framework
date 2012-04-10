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

import org.jboss.osgi.resolver.spi.AbstractWire;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

/**
 * The {@link BundleWire} implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Feb-2012
 */
class AbstractBundleWire extends AbstractWire implements BundleWire {

    AbstractBundleWire(BundleCapability cap, BundleRequirement req, BundleRevision provider, BundleRevision requirer) {
        super(cap, req, provider, requirer);
    }

    @Override
    public BundleWiring getProviderWiring() {
        return getProvider().getWiring();
    }

    @Override
    public BundleWiring getRequirerWiring() {
        return getRequirer().getWiring();
    }

    public BundleRevision getProvider() {
        return (BundleRevision) super.getProvider();
    }

    public BundleRevision getRequirer() {
        return (BundleRevision) super.getRequirer();
    }

    @Override
    public BundleCapability getCapability() {
        return (BundleCapability) super.getCapability();
    }

    @Override
    public BundleRequirement getRequirement() {
        return (BundleRequirement) super.getRequirement();
    }
}