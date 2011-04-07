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

import static org.jboss.osgi.framework.Constants.JBOSGI_INTERNAL_NAME;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.AutoInstallProvider;
import org.jboss.osgi.framework.SystemServicesProvider;
import org.jboss.osgi.spi.util.BundleInfo;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * A plugin that installs/starts bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class AutoInstallProcessor extends AbstractPluginService<AutoInstallProcessor> {

    static final ServiceName SERVICE_NAME = JBOSGI_INTERNAL_NAME.append("autoinstall");

    // Provide logging
    final Logger log = Logger.getLogger(AutoInstallProcessor.class);

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    private final InjectedValue<AutoInstallProvider> injectedProvider = new InjectedValue<AutoInstallProvider>();
    private final InjectedValue<SystemServicesProvider> injectedSystemServices = new InjectedValue<SystemServicesProvider>();

    static void addService(ServiceTarget serviceTarget) {
        AutoInstallProcessor service = new AutoInstallProcessor();
        ServiceBuilder<AutoInstallProcessor> builder = serviceTarget.addService(SERVICE_NAME, service);
        builder.addDependency(AutoInstallProvider.SERVICE_NAME, AutoInstallProvider.class, service.injectedProvider);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(Services.SYSTEM_BUNDLE, SystemBundleState.class, service.injectedSystemBundle);
        builder.addDependency(SystemServicesProvider.SERVICE_NAME, SystemServicesProvider.class, service.injectedSystemServices);
        builder.addDependency(Services.FRAMEWORK_INIT);
        builder.install();
    }

    static void awaitStartup(ServiceContainer serviceContainer, long timeout, TimeUnit unit) throws BundleException {
        @SuppressWarnings("unchecked")
        ServiceController<AutoInstallProcessor> controller = (ServiceController<AutoInstallProcessor>) serviceContainer.getRequiredService(SERVICE_NAME);
        FutureServiceValue<AutoInstallProcessor> future = new FutureServiceValue<AutoInstallProcessor>(controller);
        try {
            future.get(timeout, unit);
        } catch (Exception ex) {
            throw new BundleException("Cannot start " + SERVICE_NAME, ex);
        }
    }

    private AutoInstallProcessor() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);

        try {
            // Register additional system services
            BundleContext systemContext = injectedSystemBundle.getValue().getBundleContext();
            SystemServicesProvider servicesProvider = injectedSystemServices.getValue();
            servicesProvider.registerSystemServices(systemContext);

            AutoInstallProvider provider = injectedProvider.getValue();
            List<URL> autoInstall = new ArrayList<URL>(provider.getAutoInstallList(systemContext));
            List<URL> autoStart = new ArrayList<URL>(provider.getAutoStartList(systemContext));
            installBundles(autoInstall, autoStart);
        } catch (BundleException ex) {
            throw new IllegalStateException("Cannot start auto install bundles", ex);
        }
    }

    @Override
    public AutoInstallProcessor getValue() {
        return this;
    }

    private void installBundles(List<URL> autoInstall, List<URL> autoStart) throws BundleException {

        // Add the autoStart bundles to autoInstall
        for (URL bundleURL : autoStart) {
            autoInstall.add(bundleURL);
        }

        HashMap<URL, Bundle> autoBundles = new HashMap<URL, Bundle>();

        // Install autoInstall bundles
        BundleManager bundleManager = injectedBundleManager.getValue();
        for (URL url : autoInstall) {
            BundleInfo info = BundleInfo.createBundleInfo(url);
            Deployment dep = DeploymentFactory.createDeployment(info);
            dep.setAutoStart(autoStart.contains(url));
            Bundle bundle = bundleManager.installBundle(dep);
            autoBundles.put(url, bundle);
        }

        // Start autoStart bundles
        if (autoStart != null) {
            for (URL uri : autoStart) {
                Bundle bundle = autoBundles.get(uri);
                bundle.start();
            }
        }
    }
}