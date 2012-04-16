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

import java.util.Map;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Default implementation for the COMPLETE step of the {@link PersistentBundlesProvider}.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
public class PersistentBundlesProviderComplete extends AbstractService<Void> {

    private final InjectedValue<PackageAdmin> injectedPackageAdmin = new InjectedValue<PackageAdmin>();
    private final Map<ServiceName, Deployment> installedBundles;

    public PersistentBundlesProviderComplete(Map<ServiceName, Deployment> installedBundles) {
        this.installedBundles = installedBundles;
    }

    public void install(ServiceTarget serviceTarget) {
        ServiceBuilder<Void> builder = serviceTarget.addService(IntegrationServices.PERSISTENT_BUNDLES_PROVIDER_COMPLETE, this);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, injectedPackageAdmin);
        builder.addDependency(IntegrationServices.PERSISTENT_BUNDLES_PROVIDER);
        builder.addDependencies(installedBundles.keySet());
        addAdditionalDependencies(builder);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    protected void addAdditionalDependencies(ServiceBuilder<Void> builder) {
    }

    public void start(StartContext context) throws StartException {
        LOGGER.debugf("Persistent bundles installed");
        PackageAdmin packageAdmin = injectedPackageAdmin.getValue();
        for (Deployment dep : installedBundles.values()) {
            StorageState storageState = dep.getAttachment(StorageState.class);
            if (storageState.isPersistentlyStarted()) {
                Bundle bundle = dep.getAttachment(Bundle.class);
                if (packageAdmin.getBundleType(bundle) != PackageAdmin.BUNDLE_TYPE_FRAGMENT) {
                    LOGGER.debugf("Auto start persistent bundle: %s", bundle);
                    try {
                        bundle.start(Bundle.START_TRANSIENT & Bundle.START_ACTIVATION_POLICY);
                    } catch (BundleException ex) {
                        LOGGER.errorCannotStartPersistentBundle(ex, bundle);
                    }
                }
            }
        }
        LOGGER.debugf("Persistent bundles started");
    }
}