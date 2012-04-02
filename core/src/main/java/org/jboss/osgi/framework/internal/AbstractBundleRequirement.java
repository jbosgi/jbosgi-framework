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
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.spi.AbstractRequirement;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;

import java.util.Map;

/**
 * The {@link BundleRequirement} implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-Feb-2012
 */
class AbstractBundleRequirement extends AbstractRequirement implements BundleRequirement {

    // Provide logging
    static final Logger log = Logger.getLogger(AbstractBundleRequirement.class);

    AbstractBundleRequirement(BundleRevision brev, String namespace, Map<String, Object> atts, Map<String, String> dirs) {
        super(brev, namespace, atts, dirs);
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
    public boolean matches(BundleCapability capability) {
        return super.matches((XCapability) capability);
    }
}