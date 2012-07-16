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
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.IntegrationServices.BootstrapPhase;
import org.jboss.osgi.framework.util.ServiceTracker;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.BundleException;

/**
 * A plugin that installs the auto install bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public abstract class BootstrapBundlesInstall<T> extends BootstrapBundlesService<T> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();

    public BootstrapBundlesInstall(ServiceName baseName) {
        super(baseName, BootstrapPhase.INSTALL);
    }

    public ServiceController<T> install(ServiceTarget serviceTarget) {
        ServiceBuilder<T> builder = serviceTarget.addService(getServiceName(), this);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        addServiceDependencies(builder);
        builder.setInitialMode(Mode.ON_DEMAND);
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<T> builder) {
    }

    protected void installBootstrapBundles(final ServiceTarget serviceTarget, final List<Deployment> deployments) {

        // No bootstrap bundles - we are done
        if (deployments.isEmpty()) {
            installCompleteService(serviceTarget, false);
            return;
        }

        final Set<ServiceName> installedServices = new HashSet<ServiceName>();
        final Set<ServiceName> resolvedServices = new HashSet<ServiceName>();

        // Install the bootstrap phase services
        installResolveService(serviceTarget, installedServices, resolvedServices);
        installActivateService(serviceTarget, resolvedServices);
        installCompleteService(serviceTarget, true);

        // Track the Bundle INSTALLED services
        ServiceTracker<XBundle> installTracker = new ServiceTracker<XBundle>() {

            @Override
            protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
                return deployments.size() == trackedServices.size();
            }

            @Override
            protected void serviceStarted(ServiceController<? extends XBundle> controller) {
                installedServices.add(controller.getName());
            }

            @Override
            protected void complete() {
                activateNextService();
            }
        };

        // Install the auto install bundles
        BundleManager bundleManager = injectedBundleManager.getValue();
        for (Deployment dep : deployments) {
            try {
                bundleManager.installBundle(dep, installTracker);
            } catch (BundleException ex) {
                LOGGER.errorStateCannotInstallInitialBundle(ex, dep.getLocation());
            }
        }
    }

    protected abstract ServiceController<T> installResolveService(ServiceTarget serviceTarget, Set<ServiceName> installedServices, Set<ServiceName> resolvedServices);

    protected abstract ServiceController<T> installActivateService(ServiceTarget serviceTarget, Set<ServiceName> resolvedServices);

    protected abstract ServiceController<T> installCompleteService(ServiceTarget serviceTarget, boolean withDependency);
}