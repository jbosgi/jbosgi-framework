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
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.BundleException;

/**
 * This is the internal implementation of a fragment Bundle.
 *
 * Fragment specific functionality is handled here.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
final class FragmentBundleService extends UserBundleService<FragmentBundleState> {

    static ServiceName addService(ServiceTarget serviceTarget, FrameworkState frameworkState, long bundleId, Deployment dep) throws BundleException {
        FragmentBundleState bundleState = new FragmentBundleState(frameworkState, bundleId, dep.getSymbolicName());
        FragmentBundleService service = new FragmentBundleService(bundleState, dep);
        ServiceName serviceName = bundleState.getServiceName();
        ServiceBuilder<FragmentBundleState> builder = serviceTarget.addService(serviceName, service);
        builder.addDependency(InternalServices.CORE_SERVICES);
        builder.install();
        return serviceName;
    }

    private FragmentBundleService(FragmentBundleState bundleState, Deployment dep) throws BundleException {
        super(bundleState, dep);
    }
}
