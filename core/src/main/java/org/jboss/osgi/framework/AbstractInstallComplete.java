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
import java.util.Map;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Default implementation for the COMPLETE step of a bundles install plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
abstract class AbstractInstallComplete extends AbstractService<Void> {

    final InjectedValue<PackageAdmin> injectedPackageAdmin = new InjectedValue<PackageAdmin>();
    final Map<ServiceName, Deployment> installedBundles;

    AbstractInstallComplete(Map<ServiceName, Deployment> installedBundles) {
        this.installedBundles = installedBundles;
    }

    protected void addAdditionalDependencies(ServiceBuilder<Void> builder) {
    }

    public void start(StartContext context) throws StartException {
        String serviceName = context.getController().getName().getCanonicalName();
        LOGGER.debugf("Bundles installed: %s", serviceName);
        PackageAdmin packageAdmin = injectedPackageAdmin.getValue();
        List<Deployment> deployments = new ArrayList<Deployment>(installedBundles.values());
        Collections.sort(deployments, new DeploymentComparator());
        for (Deployment dep : deployments) {
            if (dep.isAutoStart()) {
                Bundle bundle = dep.getAttachment(Bundle.class);
                if (packageAdmin.getBundleType(bundle) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
                    LOGGER.debugf("Starting bundle: %s", bundle);
                    try {
                        bundle.start(Bundle.START_ACTIVATION_POLICY);
                    } catch (BundleException ex) {
                        LOGGER.errorCannotStartBundle(ex, bundle);
                    }
                }
            }
        }
        LOGGER.debugf("Bundles started: %s", serviceName);
    }

    static class DeploymentComparator implements Comparator<Deployment> {
        @Override
        public int compare(Deployment dep1, Deployment dep2) {
            Bundle b1 = dep1.getAttachment(Bundle.class);
            Bundle b2 = dep2.getAttachment(Bundle.class);
            return (int) (b1.getBundleId() - b2.getBundleId());
        }
    }
}
