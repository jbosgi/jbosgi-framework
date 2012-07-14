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
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * Default implementation for the ACTIVE step of the {@link BootstrapBundlesPlugin}.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
public class BootstrapBundlesActive extends AbstractService<Void> {

    private final Set<Bundle> resolvedBundles;
    
    static void addService(ServiceTarget serviceTarget, Set<Bundle> resolvedBundles) {
        BootstrapBundlesActive service = new BootstrapBundlesActive(resolvedBundles);
        ServiceBuilder<Void> builder = serviceTarget.addService(IntegrationServices.BOOTSTRAP_BUNDLES_ACTIVE, service);
        builder.addDependency(IntegrationServices.BOOTSTRAP_BUNDLES_RESOLVED);
        for (Bundle bundle : resolvedBundles) {
            TypeAdaptor typeAdaptor = (TypeAdaptor)bundle;
            ServiceName serviceName = typeAdaptor.adapt(ServiceName.class);
            builder.addDependency(serviceName.getParent().append("RESOLVED"));
        }
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }
    
    private BootstrapBundlesActive(Set<Bundle> resolvedBundles) {
        this.resolvedBundles = resolvedBundles;
    }

    @Override
    public void start(StartContext context) {
        List<Bundle> bundles = new ArrayList<Bundle>(resolvedBundles);
        Collections.sort(bundles, new BundleComparator());
        for (Bundle bundle : bundles) {
            TypeAdaptor adaptor = (TypeAdaptor) bundle;
            Deployment dep = adaptor.adapt(Deployment.class);
            OSGiMetaData metadata = adaptor.adapt(OSGiMetaData.class);
            if (dep.isAutoStart() && metadata.getFragmentHost() == null) {
                try {
                    bundle.start(Bundle.START_ACTIVATION_POLICY);
                } catch (BundleException ex) {
                    LOGGER.errorCannotStartBundle(ex, bundle);
                }
            }
        }
    }
    
    static class BundleComparator implements Comparator<Bundle> {
        @Override
        public int compare(Bundle b1, Bundle b2) {
            return (int) (b1.getBundleId() - b2.getBundleId());
        }
    }
}
