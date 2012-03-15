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

import java.io.InputStream;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Requirement;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;

/**
 * A BundleRevision that delegates to 
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class FabricatedBundleRevision implements BundleRevision, XResource {

    // Provide logging
    final Logger log = Logger.getLogger(FabricatedBundleRevision.class);

    private final XResource resource;
    private final XIdentityCapability identity;
    
    FabricatedBundleRevision(XResource resource) {
        if (resource == null || resource instanceof BundleRevision)
            throw new IllegalArgumentException("Invalid resource");
        this.resource = resource;
        this.identity = resource.getIdentityCapability();
    }
    
    @Override
    public Bundle getBundle() {
        throw new NotImplementedException();
    }

    @Override
    public String getSymbolicName() {
        return identity.getSymbolicName();
    }

    @Override
    public Version getVersion() {
        return identity.getVersion();
    }

    @Override
    public int getTypes() {
        return isFragment() ? TYPE_FRAGMENT : 0;
    }

    @Override
    public List<BundleCapability> getDeclaredCapabilities(String namespace) {
        throw new NotImplementedException();
    }

    @Override
    public List<BundleRequirement> getDeclaredRequirements(String namespace) {
        throw new NotImplementedException();
    }

    @Override
    public BundleWiring getWiring() {
        return resource.getAttachment(BundleWiring.class);
    }

    @Override
    public List<Capability> getCapabilities(String namespace) {
        return resource.getCapabilities(namespace);
    }

    @Override
    public List<Requirement> getRequirements(String namespace) {
        return resource.getRequirements(namespace);
    }

    @Override
    public <T> T addAttachment(Class<T> clazz, T value) {
        return resource.addAttachment(clazz, value);
    }

    @Override
    public <T> T getAttachment(Class<T> clazz) {
        return resource.getAttachment(clazz);
    }

    @Override
    public <T> T removeAttachment(Class<T> clazz) {
        return resource.removeAttachment(clazz);
    }

    @Override
    public XIdentityCapability getIdentityCapability() {
        return resource.getIdentityCapability();
    }

    @Override
    public boolean isFragment() {
        return resource.isFragment();
    }

    @Override
    public InputStream getContent() {
        return resource.getContent();
    }

}