package org.jboss.osgi.framework.internal;
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

import static org.jboss.osgi.framework.IntegrationServices.BUNDLE_INSTALL_PLUGIN;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BundleInstallPlugin;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.BundleException;

/**
 * A plugin that manages bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
final class DefaultBundleInstallPlugin extends AbstractPluginService<BundleInstallPlugin> implements BundleInstallPlugin {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(BUNDLE_INSTALL_PLUGIN) == null) {
            DefaultBundleInstallPlugin service = new DefaultBundleInstallPlugin();
            ServiceBuilder<BundleInstallPlugin> builder = serviceTarget.addService(BUNDLE_INSTALL_PLUGIN, service);
            builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
            builder.addDependency(Services.FRAMEWORK_CREATE);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultBundleInstallPlugin() {
    }

    @Override
    public BundleInstallPlugin getValue() {
        return this;
    }

    @Override
    public void installBundle(Deployment dep) throws BundleException {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        bundleManager.installBundle(dep, null);
    }

    @Override
    public void uninstallBundle(Deployment dep) {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        bundleManager.uninstallBundle(dep);
    }
}