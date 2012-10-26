/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.spi.ServiceTracker.SynchronousListenerServiceWrapper;
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
        ServiceBuilder<HostBundleState> builder = serviceTarget.addService(serviceName, new SynchronousListenerServiceWrapper<HostBundleState>(service));
        builder.addDependency(hostBundle.getServiceName(Bundle.INSTALLED));
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
