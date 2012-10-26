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
package org.jboss.osgi.framework.spi;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        super(baseName, IntegrationServices.BootstrapPhase.RESOLVE);
        this.installedServices = installedServices;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<T> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, injectedPackageAdmin);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(Services.RESOLVER, XResolver.class, injectedResolver);
        builder.addDependencies(getPreviousService());
    }

    protected BundleManager getBundleManager() {
        return injectedBundleManager.getValue();
    }

    @Override
    public void start(final StartContext context) throws StartException {

        ServiceContainer serviceRegistry = context.getController().getServiceContainer();
        int targetLevel = getBeginningStartLevel();

        // Track the resolved services
        final Map<ServiceName, XBundle> resolvableServices = new HashMap<ServiceName, XBundle>();
        ServiceTracker<XBundle> resolvedTracker = new ServiceTracker<XBundle>(getServiceName().getCanonicalName()) {

            @Override
            protected boolean trackService(ServiceController<? extends XBundle> controller) {
                return resolvableServices.keySet().contains(controller.getName());
            }

            @Override
            protected void complete() {
                installActivateService(context.getChildTarget(), resolvableServices.keySet());
            }
        };

        // Collect the set of resolvable bundles
        for (ServiceName installedName : installedServices) {
            XBundle bundle = getServiceController(serviceRegistry, installedName).getValue();
            Deployment dep = bundle.adapt(Deployment.class);
            int bundleLevel = dep.getStartLevel() != null ? dep.getStartLevel() : 1;
            if (dep.isAutoStart() && !bundle.isFragment() && bundleLevel <= targetLevel) {
                ServiceName resolvedName = getBundleManager().getServiceName(bundle, Bundle.RESOLVED);
                getServiceController(serviceRegistry, resolvedName).addListener(resolvedTracker);
                resolvableServices.put(resolvedName, bundle);
            }
        }

        // Strictly resolve the bootstrap bundles
        if (IntegrationServices.BOOTSTRAP_BUNDLES.isParentOf(getServiceName())) {
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

        // Leniently resolve the persistent bundles
        if (IntegrationServices.PERSISTENT_BUNDLES.isParentOf(getServiceName())) {
            Bundle[] bundles = resolvableServices.values().toArray(new Bundle[resolvableServices.size()]);
            PackageAdmin packageAdmin = injectedPackageAdmin.getValue();
            packageAdmin.resolveBundles(bundles);
        }

        // Remove the unresolved service from the tracker
        for (ServiceName serviceName : new HashSet<ServiceName>(resolvableServices.keySet())) {
            if (!resolvableServices.get(serviceName).isResolved()) {
                resolvableServices.remove(serviceName);
                resolvedTracker.untrackService(getServiceController(serviceRegistry, serviceName));
            }
        }

        // Check the tracker for completeness
        resolvedTracker.checkAndComplete();
    }

    @SuppressWarnings("unchecked")
    private ServiceController<XBundle> getServiceController(ServiceContainer serviceRegistry, ServiceName serviceName) {
        return (ServiceController<XBundle>) serviceRegistry.getRequiredService(serviceName);
    }

    private int getBeginningStartLevel() {
        String levelSpec = (String) getBundleManager().getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
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
        return new BootstrapBundlesActivate<T>(getServiceName().getParent(), resolvedServices).install(serviceTarget, getServiceListener());
    }

}