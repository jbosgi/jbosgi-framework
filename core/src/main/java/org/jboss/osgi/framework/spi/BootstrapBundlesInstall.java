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
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * A plugin that installs the auto install bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class BootstrapBundlesInstall<T> extends BootstrapBundlesService<T> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

    public BootstrapBundlesInstall(ServiceName baseName) {
        super(baseName, IntegrationServices.BootstrapPhase.INSTALL);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<T> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, injectedBundleContext);
        builder.addDependency(IntegrationServices.FRAMEWORK_CORE_SERVICES);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    protected BundleManager getBundleManager() {
        return injectedBundleManager.getValue();
    }

    protected BundleContext getBundleContext() {
        return injectedBundleContext.getValue();
    }

    protected void installBootstrapBundles(final ServiceTarget serviceTarget, final List<Deployment> deployments) {

        Set<XBundleRevision> installedRevisions = new HashSet<XBundleRevision>();

        // Install the auto install bundles
        for (Deployment dep : deployments) {
            try {
                XBundleRevision brev = getBundleManager().createBundleRevision(getBundleContext(), dep, serviceTarget);
                installedRevisions.add(brev);
            } catch (BundleException ex) {
                LOGGER.errorStateCannotInstallInitialBundle(ex, dep.getLocation());
            }
        }

        installResolveService(serviceTarget, installedRevisions);
    }

    protected ServiceController<T> installResolveService(ServiceTarget serviceTarget, Set<XBundleRevision> installedRevisions) {
        return new BootstrapBundlesResolve<T>(getServiceName().getParent(), installedRevisions).install(serviceTarget, getServiceListener());
    }
}