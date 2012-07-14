/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.osgi.framework.Bundle;

/**
 * Represents the RESOLVED state of a host bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Apr-2011
 */
final class HostBundleResolvedService extends UserBundleResolvedService<HostBundleState> {

    private final HostBundleState hostBundle;

    static void addService(ServiceTarget serviceTarget, HostBundleState hostBundle, ServiceName moduleServiceName) {
        ServiceName serviceName = hostBundle.getServiceName(Bundle.RESOLVED);
        HostBundleResolvedService service = new HostBundleResolvedService(hostBundle);
        ServiceBuilder<HostBundleState> builder = serviceTarget.addService(serviceName, service);
        builder.addDependency(moduleServiceName);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private HostBundleResolvedService(HostBundleState hostBundle) {
        super(hostBundle.getFrameworkState());
        this.hostBundle = hostBundle;
    }

    @Override
    HostBundleState getBundleState() {
        return hostBundle;
    }
}
