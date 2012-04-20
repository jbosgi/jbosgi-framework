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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.IntegrationServices.BUNDLE_INSTALL_HANDLER;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BundleInstallHandler;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.BundleException;

/**
 * A plugin that manages bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
final class DefaultBundleInstallHandler extends AbstractPluginService<BundleInstallHandler> implements BundleInstallHandler {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(BUNDLE_INSTALL_HANDLER) == null) {
            DefaultBundleInstallHandler service = new DefaultBundleInstallHandler();
            ServiceBuilder<BundleInstallHandler> builder = serviceTarget.addService(BUNDLE_INSTALL_HANDLER, service);
            builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
            builder.addDependency(Services.FRAMEWORK_CREATE);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultBundleInstallHandler() {
    }

    @Override
    public BundleInstallHandler getValue() {
        return this;
    }

    @Override
    public void installBundle(Deployment dep) throws BundleException {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        bundleManager.installBundle(dep);
    }

    @Override
    public void uninstallBundle(Deployment dep) {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        bundleManager.uninstallBundle(dep);
    }
}