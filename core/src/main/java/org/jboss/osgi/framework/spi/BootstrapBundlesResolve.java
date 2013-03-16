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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.resolver.ResolutionException;

public class BootstrapBundlesResolve<T> extends BootstrapBundlesService<T> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<FrameworkWiring> injectedFrameworkWiring = new InjectedValue<FrameworkWiring>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<XBundle> injectedSystemBundle = new InjectedValue<XBundle>();
    private final InjectedValue<XResolver> injectedResolver = new InjectedValue<XResolver>();
    private final Set<ServiceName> installedServices;

    public BootstrapBundlesResolve(ServiceName baseName, Set<ServiceName> installedServices) {
        super(baseName, IntegrationServices.BootstrapPhase.RESOLVE);
        this.installedServices = installedServices;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<T> builder) {
        builder.addDependency(IntegrationServices.SYSTEM_BUNDLE_INTERNAL, XBundle.class, injectedSystemBundle);
        builder.addDependency(IntegrationServices.FRAMEWORK_WIRING_PLUGIN, FrameworkWiring.class, injectedFrameworkWiring);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
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
        final Set<XBundle> resolvableBundles = new HashSet<XBundle>();

        // Collect the set of resolvable bundles
        for (ServiceName installedName : installedServices) {
            XBundle bundle = getServiceController(serviceRegistry, installedName).getValue();
            Deployment dep = bundle.adapt(Deployment.class);
            int bundleLevel = dep.getStartLevel() != null ? dep.getStartLevel() : 1;
            if (dep.isAutoStart() && !bundle.isFragment() && bundleLevel <= targetLevel) {
                resolvableBundles.add(bundle);
            }
        }

        // Strictly resolve the bootstrap bundles
        if (IntegrationServices.BOOTSTRAP_BUNDLES.isParentOf(getServiceName())) {
            XEnvironment env = injectedEnvironment.getValue();
            List<XBundleRevision> mandatory = new ArrayList<XBundleRevision>();
            mandatory.add(injectedSystemBundle.getValue().getBundleRevision());
            for (XBundle bundle : resolvableBundles) {
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

        if (!resolvableBundles.isEmpty()) {

            // Leniently resolve the persistent bundles
            if (IntegrationServices.PERSISTENT_BUNDLES.isParentOf(getServiceName())) {
                List<Bundle> bundles = new ArrayList<Bundle>();
                for (XBundle bundle : resolvableBundles) {
                    bundles.add(bundle);
                }
                FrameworkWiring frameworkWiring = injectedFrameworkWiring.getValue();
                frameworkWiring.resolveBundles(bundles);
            }

            // Remove the unresolved service from the tracker
            Iterator<XBundle> iterator = resolvableBundles.iterator();
            while(iterator.hasNext()) {
                XBundle bundle = iterator.next();
                if (!bundle.isResolved()) {
                    iterator.remove();
                }
            }
        }

        installActivateService(context.getChildTarget(), resolvableBundles);
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

    protected ServiceController<T> installActivateService(ServiceTarget serviceTarget, Set<XBundle> resolvedBundles) {
        return new BootstrapBundlesActivate<T>(getServiceName().getParent(), resolvedBundles).install(serviceTarget, getServiceListener());
    }

}