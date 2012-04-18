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
 * Represents the INSTALLED state of a fragment bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
final class FragmentBundleInstalledService extends UserBundleInstalledService<FragmentBundleState> {

    static ServiceName addService(ServiceTarget serviceTarget, FrameworkState frameworkState, Deployment dep) throws BundleException {
        ServiceName serviceName = BundleManagerPlugin.getServiceName(dep).append("INSTALLED");
        FragmentBundleInstalledService service = new FragmentBundleInstalledService(frameworkState, dep);
        ServiceBuilder<FragmentBundleState> builder = serviceTarget.addService(serviceName, service);
        builder.addDependency(InternalServices.FRAMEWORK_CORE_SERVICES);
        builder.install();
        return serviceName;
    }

    private FragmentBundleInstalledService(FrameworkState frameworkState, Deployment dep) throws BundleException {
        super(frameworkState, dep);
    }

    @Override
    FragmentBundleState createBundleState(Deployment dep) {
        long bundleId = dep.getAttachment(BundleId.class).longValue();
        return new FragmentBundleState(getFrameworkState(), bundleId, dep);
    }
}
