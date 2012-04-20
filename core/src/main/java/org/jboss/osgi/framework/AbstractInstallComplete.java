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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceController.Transition;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceListener.Inheritance;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * Default implementation for the COMPLETE step of a bundles install plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
abstract class AbstractInstallComplete extends AbstractService<Void> {

    private final Map<ServiceName, Deployment> installedBundles = new HashMap<ServiceName, Deployment>();
    private final AtomicBoolean installComplete = new AtomicBoolean();
    private final ServiceContainer serviceContainer;
    private final ServiceController<?> listenerTarget;
    private ServiceListener<Object> listener;

    AbstractInstallComplete(BundleManager bundleManager) {
        this.serviceContainer = bundleManager.getServiceContainer();
        this.listenerTarget = serviceContainer.getRequiredService(Services.BUNDLE_MANAGER);
    }

    public void registerBundleInstallService(ServiceName serviceName, Deployment dep) {
        synchronized (installedBundles) {
            LOGGER.debugf("Initial bundle register: %s", serviceName.getCanonicalName());
            installedBundles.put(serviceName, dep);
        }
    }
    
    public void installComplete(ServiceBuilder<Void> builder) {
        synchronized (installedBundles) {
            int remainingServiceCount = installedBundles.size();
            LOGGER.debugf("Initial bundle install complete (remaining=%d)", remainingServiceCount);
            if (remainingServiceCount == 0) {
                installService(builder);
            }
            installComplete.set(true);
        }
    }

    private void installService(ServiceBuilder<Void> builder) {
        LOGGER.debugf("Install initial bundle service: %s", getServiceName());
        listenerTarget.removeListener(listener);
        builder.install();
    }
    
    public ServiceBuilder<Void> install(ServiceTarget serviceTarget) {
        final ServiceBuilder<Void> builder = serviceTarget.addService(getServiceName(), this);
        configureInstallCompleteDependencies(builder);
        listener = new AbstractServiceListener<Object>() {

            @Override
            public void listenerAdded(ServiceController<? extends Object> controller) {
                LOGGER.debugf("Initial bundle listener added to: %s", controller.getName());
                synchronized (installedBundles) {
                    Iterator<ServiceName> iterator = installedBundles.keySet().iterator();
                    while (iterator.hasNext()) {
                        ServiceName serviceName = iterator.next();
                        ServiceController<?> service = serviceContainer.getService(serviceName);
                        LOGGER.debugf("Initial bundle checking state: %s == %s", serviceName, service.getState());
                        if (service.getState() == State.UP) {
                            bundleInstallServiceComplete(service);
                            iterator.remove();
                        }
                    }
                }
            }

            @Override
            public void transition(ServiceController<? extends Object> controller, Transition transition) {
                synchronized (installedBundles) {
                    if (installedBundles.get(controller.getName()) != null) {
                        LOGGER.debugf("Initial bundle transition: %s => %s", transition, controller.getName());
                        switch (transition) {
                            case STARTING_to_UP:
                            case STARTING_to_START_FAILED:
                                installedBundles.remove(controller.getName());
                                bundleInstallServiceComplete(controller);
                        }
                    }
                }
            }

            private void bundleInstallServiceComplete(ServiceController<?> controller) {
                int remainingServiceCount = installedBundles.size();
                LOGGER.debugf("Initial bundle install complete: %s (remaining=%d)", controller.getValue(), remainingServiceCount);
                if (installComplete.get() && remainingServiceCount == 0) {
                    installService(builder);
                }
            }
        };
        listenerTarget.addListener(Inheritance.ALL, listener);
        return builder;
    }

    protected abstract ServiceName getServiceName();

    protected abstract void configureInstallCompleteDependencies(ServiceBuilder<Void> builder);

    public void start(final StartContext context) throws StartException {
        ServiceController<?> controller = context.getController();
        LOGGER.debugf("Starting: %s", controller.getName());
        List<Deployment> deployments = new ArrayList<Deployment>(installedBundles.values());
        Collections.sort(deployments, new DeploymentComparator());
        for (Deployment dep : deployments) {
            if (dep.isAutoStart()) {
                Bundle bundle = dep.getAttachment(Bundle.class);
                OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
                if (metadata.getFragmentHost() == null) {
                    LOGGER.debugf("Starting bundle: %s", bundle);
                    try {
                        bundle.start(Bundle.START_ACTIVATION_POLICY);
                    } catch (BundleException ex) {
                        LOGGER.errorCannotStartBundle(ex, bundle);
                    }
                }
            }
        }
        LOGGER.debugf("Started: %s", controller.getName());
    }

    static class DeploymentComparator implements Comparator<Deployment> {
        @Override
        public int compare(Deployment dep1, Deployment dep2) {
            Bundle b1 = dep1.getAttachment(Bundle.class);
            Bundle b2 = dep2.getAttachment(Bundle.class);
            return (int) (b1.getBundleId() - b2.getBundleId());
        }
    }
}
