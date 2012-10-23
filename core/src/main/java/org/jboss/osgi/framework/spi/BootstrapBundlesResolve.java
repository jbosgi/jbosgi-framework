package org.jboss.osgi.framework.spi;

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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.ServiceTracker.SynchronousListenerServiceWrapper;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.Bundle;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.resolver.ResolutionException;

public class BootstrapBundlesResolve<T> extends BootstrapBundlesService<T> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<PackageAdmin> injectedPackageAdmin = new InjectedValue<PackageAdmin>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<XResolver> injectedResolver = new InjectedValue<XResolver>();
    private final Set<ServiceName> installedServices;

    public BootstrapBundlesResolve(ServiceName baseName, Set<ServiceName> installedServices) {
        super(baseName, IntegrationService.BootstrapPhase.RESOLVE);
        this.installedServices = installedServices;
    }

    @Override
    public ServiceController<T> install(ServiceTarget serviceTarget) {
        // The bootstrap resolve service cannot have a direct dependency on
        // the bundle INSTALLED services because it must be possible to uninstall
        // a bundle without taking this service down
        ServiceBuilder<T> builder = serviceTarget.addService(getServiceName(), new SynchronousListenerServiceWrapper<T>(this));
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, injectedPackageAdmin);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(Services.RESOLVER, XResolver.class, injectedResolver);
        builder.addDependencies(getPreviousService());
        addServiceDependencies(builder);
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<T> builder) {
    }

    @Override
    public void start(StartContext context) throws StartException {

        ServiceContainer serviceRegistry = context.getController().getServiceContainer();
        int targetLevel = getBeginningStartLevel();

        // Collect the set of resolvable bundles
        Map<ServiceName, XBundle> resolvableServices = new HashMap<ServiceName, XBundle>();
        for (ServiceName serviceName : installedServices) {
            ServiceController<?> controller = serviceRegistry.getRequiredService(serviceName);
            XBundle bundle = (XBundle) controller.getValue();
            Deployment dep = bundle.adapt(Deployment.class);
            int bundleLevel = dep.getStartLevel() != null ? dep.getStartLevel() : 1;
            if (dep.isAutoStart() && !bundle.isFragment() && bundleLevel <= targetLevel) {
                resolvableServices.put(serviceName, bundle);
            }
        }

        // Strictly resolve the bootstrap bundles
        if (IntegrationService.BOOTSTRAP_BUNDLES.isParentOf(getServiceName())) {
            XEnvironment env = injectedEnvironment.getValue();
            List<XBundleRevision> mandatory = new ArrayList<XBundleRevision>();
            for (XBundle bundle : resolvableServices.values()) {
                mandatory.add(bundle.getBundleRevision());
            }
            XResolver resolver = injectedResolver.getValue();
            XResolveContext ctx = resolver.createResolveContext(env, mandatory, null);
            try {
                resolver.resolveAndApply(ctx);
            } catch (ResolutionException ex) {
                throw new StartException(ex);
            }
        }

        if (IntegrationService.PERSISTENT_BUNDLES.isParentOf(getServiceName())) {
            Bundle[] bundles = resolvableServices.values().toArray(new Bundle[resolvableServices.size()]);
            PackageAdmin packageAdmin = injectedPackageAdmin.getValue();
            packageAdmin.resolveBundles(bundles);
        }

        // Collect the resolved service
        final Set<ServiceName> resolvedServices = new HashSet<ServiceName>();
        for (Entry<ServiceName, XBundle> entry : resolvableServices.entrySet()) {
            if (entry.getValue().isResolved()) {
                resolvedServices.add(entry.getKey());
            }
        }

        // Track the resolved services
        final ServiceTarget serviceTarget = context.getChildTarget();
        ServiceTracker<XBundle> resolvedTracker = new ServiceTracker<XBundle>() {

            @Override
            protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
                return resolvedServices.size() == trackedServices.size();
            }

            @Override
            protected void complete() {
                installActivateService(serviceTarget, resolvedServices);
            }
        };

        // Add the tracker to the Bundle RESOLVED services
        BundleManager bundleManager = injectedBundleManager.getValue();
        for (ServiceName serviceName : resolvedServices) {
            XBundle bundle = resolvableServices.get(serviceName);
            serviceName = bundleManager.getServiceName(bundle, Bundle.RESOLVED);
            @SuppressWarnings("unchecked")
            ServiceController<XBundle> resolved = (ServiceController<XBundle>) serviceRegistry.getRequiredService(serviceName);
            resolved.addListener(resolvedTracker);
        }

        // Check the tracker for completeness
        resolvedTracker.checkAndComplete();
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

    protected ServiceController<T> installActivateService(ServiceTarget serviceTarget, Set<ServiceName> resolvedServices) {
        return new BootstrapBundlesActivate<T>(getServiceName().getParent(), resolvedServices).install(serviceTarget);
    }

}