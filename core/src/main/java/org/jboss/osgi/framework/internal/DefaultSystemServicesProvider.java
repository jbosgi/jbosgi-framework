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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.SystemServicesProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * An integration point for system services.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Mar-2011
 */
final class DefaultSystemServicesProvider extends AbstractPluginService<SystemServicesProvider> implements SystemServicesProvider {

    static void addService(ServiceTarget serviceTarget) {
        DefaultSystemServicesProvider service = new DefaultSystemServicesProvider();
        ServiceBuilder<SystemServicesProvider> builder = serviceTarget.addService(SERVICE_NAME, service);
        builder.install();
    }

    private DefaultSystemServicesProvider() {
    }


    @Override
    public SystemServicesProvider getValue() throws IllegalStateException {
        return this;
    }

    @Override
    public void registerSystemServices(BundleContext systemContext) throws BundleException {
        // nothing to do
    }
}