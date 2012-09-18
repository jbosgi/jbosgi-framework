package org.jboss.osgi.framework.internal;
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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.util.ServiceTracker.SynchronousListenerServiceWrapper;

/**
 * Represents the ACTIVE state of a host bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-May-2011
 */
final class HostBundleActiveService extends UserBundleActiveService<HostBundleState> {

    private final InjectedValue<HostBundleState> injectedBundleState = new InjectedValue<HostBundleState>();

    static void addService(ServiceTarget serviceTarget, FrameworkState frameworkState, ServiceName serviceName) {
        HostBundleActiveService service = new HostBundleActiveService(frameworkState);
        ServiceBuilder<HostBundleState> builder = serviceTarget.addService(serviceName.append("ACTIVE"), new SynchronousListenerServiceWrapper<HostBundleState>(service));
        builder.addDependency(serviceName.append("INSTALLED"), HostBundleState.class, service.injectedBundleState);
        builder.setInitialMode(Mode.NEVER);
        builder.install();
    }

    private HostBundleActiveService(FrameworkState frameworkState) {
        super(frameworkState);
    }

    @Override
    HostBundleState getBundleState() {
        return injectedBundleState.getValue();
    }
}
