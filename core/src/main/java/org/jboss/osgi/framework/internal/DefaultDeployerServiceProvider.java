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

import java.util.Properties;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.DeployerService;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.SystemDeployerService;
import org.jboss.osgi.framework.DeployerServiceProvider;
import org.jboss.osgi.framework.ServiceNames;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * A plugin that manages bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
final class DefaultDeployerServiceProvider extends AbstractPluginService<DeployerService> implements DeployerServiceProvider {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    private final InjectedValue<ServiceManagerPlugin> injectedServiceManager = new InjectedValue<ServiceManagerPlugin>();
    private DeployerService delegate;

    static void addService(ServiceTarget serviceTarget) {
        DefaultDeployerServiceProvider service = new DefaultDeployerServiceProvider();
        ServiceBuilder<DeployerService> builder = serviceTarget.addService(ServiceNames.DEPLOYERSERVICE_PROVIDER, service);
        builder.addDependency(ServiceNames.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(ServiceNames.SYSTEM_BUNDLE, SystemBundleState.class, service.injectedSystemBundle);
        builder.addDependency(InternalServices.SERVICE_MANAGER_PLUGIN, ServiceManagerPlugin.class, service.injectedServiceManager);
        builder.addDependency(ServiceNames.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultDeployerServiceProvider() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        final BundleManager bundleManager = injectedBundleManager.getValue();
        final SystemBundleState systemBundle = injectedSystemBundle.getValue();
        delegate = new SystemDeployerService(systemBundle.getBundleContext()) {

            @Override
            protected Bundle installBundle(Deployment dep) throws BundleException {
                UserBundleState userBundle = bundleManager.installBundle(dep);
                return userBundle.getBundleProxy();
            }

            @Override
            protected void uninstallBundle(Deployment dep, Bundle bundle) throws BundleException {
                bundleManager.uninstallBundle(dep);
            }
        };

        Properties props = new Properties();
        props.put("provider", "system");
        ServiceManagerPlugin serviceManager = injectedServiceManager.getValue();
        serviceManager.registerService(systemBundle, new String[] { DeployerService.class.getName() }, delegate, props);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        delegate = null;
    }

    @Override
    public DeployerService getValue() {
        return delegate;
    }
}