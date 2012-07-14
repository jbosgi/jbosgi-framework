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

import java.util.HashSet;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Default implementation for the RESOLVED step of the {@link PersistentBundlesPlugin}.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
public abstract class PersistentBundlesResolved extends AbstractBootstrapInstallTracker {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<PackageAdmin> injectedPackageAdmin = new InjectedValue<PackageAdmin>();

    protected ServiceName getServiceName() {
        return IntegrationServices.PERSISTENT_BUNDLES_RESOLVED;
    }

    @Override
    protected void configureDependencies(ServiceBuilder<Void> builder) {
        builder.addDependency(IntegrationServices.PERSISTENT_BUNDLES_INSTALLED);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, injectedPackageAdmin);
    }

    @Override
    protected void startInternal(StartContext context, Set<Bundle> installedBundles) throws StartException {
        
        int targetLevel = getBeginningStartLevel();
        
        // Collect the set of resolvable bundles
        Set<Bundle> resolvableBundles = new HashSet<Bundle>();
        for (Bundle bundle : installedBundles) {
            TypeAdaptor typeAdaptor = (TypeAdaptor)bundle;
            Deployment dep = typeAdaptor.adapt(Deployment.class);
            int bundleLevel = dep.getStartLevel() != null ? dep.getStartLevel() : 1;
            if (dep.isAutoStart() && bundleLevel <= targetLevel) {
                resolvableBundles.add(bundle);
            }
        }
        
        // Leniently resolve the bundles
        Bundle[] bundles = new Bundle[resolvableBundles.size()];
        PackageAdmin packageAdmin = injectedPackageAdmin.getValue();
        packageAdmin.resolveBundles(resolvableBundles.toArray(bundles));
        
        // Collect the set of resolved bundles
        Set<Bundle> resolvedBundles = new HashSet<Bundle>();
        for (Bundle bundle : resolvableBundles) {
            if (bundle.getState() == Bundle.RESOLVED)
                resolvedBundles.add(bundle);
        }
        
        // Create the bootstrap ACTIVE service
        PersistentBundlesActive.addService(context.getChildTarget(), resolvedBundles);
    }

    private int getBeginningStartLevel() {
        BundleManager bundleManager = injectedBundleManager.getValue();
        String levelSpec = (String) bundleManager.getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
        if (levelSpec != null) {
            try {
                return Integer.parseInt(levelSpec);
            } catch (NumberFormatException nfe) {
                LOGGER.errorInvalidBeginningStartLevel(levelSpec);
            }
        }
        return 1;
    }
}