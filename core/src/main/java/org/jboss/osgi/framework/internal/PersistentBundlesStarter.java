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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.Map;
import java.util.Map.Entry;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.IntegrationServices;
import org.jboss.osgi.framework.PersistentBundleInstaller;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * A service that starts persistent bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Apr-2012
 */
public final class PersistentBundlesStarter extends AbstractPluginService<Void> {

    private final InjectedValue<PersistentBundleInstaller> injectedPersistentBundles = new InjectedValue<PersistentBundleInstaller>();

    static void addService(ServiceTarget serviceTarget) {
        PersistentBundlesStarter service = new PersistentBundlesStarter();
        ServiceBuilder<Void> builder = serviceTarget.addService(InternalServices.PERSISTENT_BUNDLE_STARTER, service);
        builder.addDependency(IntegrationServices.PERSISTENT_BUNDLE_INSTALLER, PersistentBundleInstaller.class, service.injectedPersistentBundles);
        builder.addDependency(IntegrationServices.PERSISTENT_BUNDLE_INSTALLER_COMPLETE);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private PersistentBundlesStarter() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        Map<ServiceName, Deployment> pendingServices = injectedPersistentBundles.getValue().getInstalledServices();
        for (Entry<ServiceName, Deployment> entry : pendingServices.entrySet()) {
            ServiceName key = entry.getKey();
            Deployment dep = entry.getValue();
            if (dep.isAutoStart()) {
                LOGGER.debugf("Autostart persistent bundle: %s", key);
                Bundle bundle = dep.getAttachment(Bundle.class);
                try {
                    bundle.start();
                } catch (BundleException ex) {
                    LOGGER.errorCannotStartPersistentBundle(ex, bundle);
                }
            }
        }
        LOGGER.debugf("Persistent bundles started");
    }
}