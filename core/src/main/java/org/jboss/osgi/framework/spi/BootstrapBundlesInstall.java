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
import static org.jboss.osgi.framework.internal.InternalServices.BUNDLE_BASE_NAME;

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
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.Services;
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
        super(baseName, IntegrationServices.BootstrapPhase.INSTALL);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<T> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    protected BundleManager getBundleManager() {
        return injectedBundleManager.getValue();
    }

    protected void installBootstrapBundles(final ServiceTarget serviceTarget, final List<Deployment> deployments) {

        // Track the Bundle INSTALLED services
        ServiceTracker<XBundle> installTracker = new ServiceTracker<XBundle>(getServiceName().getCanonicalName()) {

            Set<ServiceName> installedServices = new HashSet<ServiceName>();

            @Override
            protected boolean trackService(ServiceController<? extends XBundle> controller) {
                ServiceName serviceName = controller.getName();
                return BUNDLE_BASE_NAME.isParentOf(serviceName) && serviceName.getSimpleName().equals("INSTALLED");
            }

            @Override
            protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
                return deployments.size() == trackedServices.size();
            }

            @Override
            protected void serviceStarted(ServiceController<? extends XBundle> controller) {
                synchronized (installedServices) {
                    installedServices.add(controller.getName());
                }
            }

            @Override
            protected void complete() {
                installResolveService(serviceTarget, installedServices);
            }
        };

        // Install the auto install bundles
        BundleManager bundleManager = getBundleManager();
        for (Deployment dep : deployments) {
            try {
                bundleManager.installBundle(dep, serviceTarget, installTracker);
            } catch (BundleException ex) {
                LOGGER.errorStateCannotInstallInitialBundle(ex, dep.getLocation());
            }
        }

        // Check the tracker for completeness
        installTracker.checkAndComplete();
    }

    protected ServiceController<T> installResolveService(ServiceTarget serviceTarget, Set<ServiceName> installedServices) {
        return new BootstrapBundlesResolve<T>(getServiceName().getParent(), installedServices).install(serviceTarget, getServiceListener());
    }
}