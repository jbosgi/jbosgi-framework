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
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.ServiceTracker.SynchronousListenerServiceWrapper;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * Represents the INSTALLED state of a fragment bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
final class FragmentBundleInstalledService extends UserBundleInstalledService<FragmentBundleState, FragmentBundleRevision> {

    static ServiceName addService(ServiceTarget serviceTarget, FrameworkState frameworkState, Deployment dep, ServiceListener<XBundle> listener) throws BundleException {
        ServiceName serviceName = frameworkState.getBundleManager().getServiceName(dep, Bundle.INSTALLED);
        FragmentBundleInstalledService service = new FragmentBundleInstalledService(frameworkState, dep);
        ServiceBuilder<FragmentBundleState> builder = serviceTarget.addService(serviceName, new SynchronousListenerServiceWrapper<FragmentBundleState>(service));
        builder.addDependency(InternalServices.FRAMEWORK_CORE_SERVICES);
        if (listener != null) {
            builder.addListener(listener);
        }
        builder.install();
        return serviceName;
    }

    private FragmentBundleInstalledService(FrameworkState frameworkState, Deployment dep) throws BundleException {
        super(frameworkState, dep);
    }

    @Override
    FragmentBundleRevision createBundleRevision(Deployment dep, OSGiMetaData metadata, InternalStorageState storageState) throws BundleException {
        return new FragmentBundleRevision(getFrameworkState(), dep, metadata, storageState);
    }

    @Override
    FragmentBundleState createBundleState(FragmentBundleRevision revision, ServiceName serviceName) {
        return new FragmentBundleState(getFrameworkState(), revision, serviceName);
    }

    @Override
    void createResolvedService(ServiceTarget serviceTarget, FragmentBundleRevision userRev) {
        // Fragments don't have a RESOLVED service
    }

    @Override
    void createActiveService(ServiceTarget serviceTarget, FragmentBundleRevision brev) {
        // Fragments don't have a ACTIVE service
    }
}
