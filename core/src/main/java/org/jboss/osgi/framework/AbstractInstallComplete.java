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
package org.jboss.osgi.framework;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.util.ServiceTracker;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * Default implementation for the COMPLETE step of a bundles install plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
abstract class AbstractInstallComplete extends AbstractService<Void> {

    private Set<Bundle> installedBundles = new HashSet<Bundle>();
    private ServiceTracker<Bundle> tracker;

    protected abstract ServiceName getServiceName();

    abstract boolean allServicesAdded(Set<ServiceName> trackedServices);

    protected abstract void configureDependencies(ServiceBuilder<Void> builder);

    public ServiceListener<Bundle> getListener() {
        return tracker;
    }

    public ServiceBuilder<Void> install(ServiceTarget serviceTarget) {
        final AbstractInstallComplete installComplete = this;
        final ServiceBuilder<Void> builder = serviceTarget.addService(getServiceName(), this);
        tracker = new ServiceTracker<Bundle>() {

            @Override
            protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
                return installComplete.allServicesAdded(trackedServices);
            }

            @Override
            protected void serviceStarted(ServiceController<? extends Bundle> controller) {
                Bundle bundle = controller.getValue();
                installedBundles.add(bundle);
            }

            @Override
            protected void complete() {
                builder.install();
            }
        };
        configureDependencies(builder);
        return builder;
    }

    public void checkAndComplete() {
        tracker.checkAndComplete();
    }

    public void start(final StartContext context) throws StartException {
        ServiceController<?> controller = context.getController();
        LOGGER.debugf("Starting: %s", controller.getName());
        List<Bundle> bundles = new ArrayList<Bundle>(installedBundles);
        Collections.sort(bundles, new BundleComparator());
        for (Bundle bundle : bundles) {
            TypeAdaptor adaptor = (TypeAdaptor) bundle;
            Deployment dep = adaptor.adapt(Deployment.class);
            OSGiMetaData metadata = adaptor.adapt(OSGiMetaData.class);
            if (dep.isAutoStart() && metadata.getFragmentHost() == null) {
                LOGGER.debugf("Starting bundle: %s", bundle);
                try {
                    bundle.start(Bundle.START_ACTIVATION_POLICY);
                } catch (BundleException ex) {
                    LOGGER.errorCannotStartBundle(ex, bundle);
                }
            }
        }
        LOGGER.debugf("Started: %s", controller.getName());
        installedBundles = null;
        tracker = null;
    }

    static class BundleComparator implements Comparator<Bundle> {
        @Override
        public int compare(Bundle b1, Bundle b2) {
            return (int) (b1.getBundleId() - b2.getBundleId());
        }
    }
}
