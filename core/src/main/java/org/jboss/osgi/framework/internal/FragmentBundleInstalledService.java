/*
 * #%L
 * JBossOSGi Framework Core
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
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.Bundle;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.BundleException;

/**
 * Represents the INSTALLED state of a fragment bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
final class FragmentBundleInstalledService extends UserBundleInstalledService<FragmentBundleState,FragmentBundleRevision> {

    static ServiceName addService(ServiceTarget serviceTarget, FrameworkState frameworkState, Deployment dep, ServiceListener<Bundle> listener) throws BundleException {
        ServiceName serviceName = frameworkState.getBundleManager().getServiceName(dep).append("INSTALLED");
        FragmentBundleInstalledService service = new FragmentBundleInstalledService(frameworkState, dep);
        ServiceBuilder<FragmentBundleState> builder = serviceTarget.addService(serviceName, service);
        builder.addDependency(InternalServices.FRAMEWORK_CORE_SERVICES);
        if (listener != null) {
            builder.addListener(listener);
        }
        builder.install();
        return serviceName;
    }

    private FragmentBundleInstalledService(FrameworkState frameworkState, Deployment deployment) throws BundleException {
        super(frameworkState, deployment);
    }

    @Override
    FragmentBundleRevision createBundleRevision(Deployment deployment, OSGiMetaData metadata, StorageState storageState) throws BundleException {
        return new FragmentBundleRevision(getFrameworkState(), deployment, metadata, storageState);
    }

    @Override
    FragmentBundleState createBundleState(FragmentBundleRevision revision, StorageState storageState) {
        return new FragmentBundleState(getFrameworkState(), revision, storageState);
    }
}
