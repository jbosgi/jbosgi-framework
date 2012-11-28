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
package org.jboss.osgi.framework.internal;

import java.util.Collection;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.spi.ExecutorServicePlugin;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.ServiceState;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

/**
 * A plugin that manages {@link FrameworkListener}, {@link BundleListener}, {@link ServiceListener} and their associated
 * {@link FrameworkEvent}, {@link BundleEvent}, {@link ServiceEvent}.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class FrameworkEventsPlugin extends ExecutorServicePlugin<FrameworkEvents> implements FrameworkEvents {

    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    private FrameworkEvents frameworkEvents;

    FrameworkEventsPlugin() {
        super(IntegrationServices.FRAMEWORK_EVENTS, "Framework Events Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkEvents> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(IntegrationServices.SYSTEM_CONTEXT_INTERNAL, BundleContext.class, injectedSystemContext);
        builder.addDependency(IntegrationServices.LOCK_MANAGER, LockManager.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        LockManager lockManager = injectedLockManager.getValue();
        frameworkEvents = new FrameworkEventsImpl(getBundleManager(), getExecutorService(), lockManager);
    }

    @Override
    public void stop(StopContext context) {
        frameworkEvents.removeAllBundleListeners();
        frameworkEvents.removeAllFrameworkListeners();
        frameworkEvents.removeAllServiceListeners();
    }

    @Override
    public FrameworkEvents getValue() {
        return this;
    }

    public void addBundleListener(XBundle bundle, BundleListener listener) {
        frameworkEvents.addBundleListener(bundle, listener);
    }

    public void removeBundleListener(XBundle bundle, BundleListener listener) {
        frameworkEvents.removeBundleListener(bundle, listener);
    }

    public void removeBundleListeners(XBundle bundle) {
        frameworkEvents.removeBundleListeners(bundle);
    }

    public void removeAllBundleListeners() {
        frameworkEvents.removeAllBundleListeners();
    }

    public void addFrameworkListener(XBundle bundle, FrameworkListener listener) {
        frameworkEvents.addFrameworkListener(bundle, listener);
    }

    public void removeFrameworkListener(XBundle bundle, FrameworkListener listener) {
        frameworkEvents.removeFrameworkListener(bundle, listener);
    }

    public void removeFrameworkListeners(XBundle bundle) {
        frameworkEvents.removeFrameworkListeners(bundle);
    }

    public void removeAllFrameworkListeners() {
        frameworkEvents.removeAllFrameworkListeners();
    }

    public void addServiceListener(XBundle bundle, ServiceListener listener, String filterstr) throws InvalidSyntaxException {
        frameworkEvents.addServiceListener(bundle, listener, filterstr);
    }

    public void removeServiceListener(XBundle bundle, ServiceListener listener) {
        frameworkEvents.removeServiceListener(bundle, listener);
    }

    public void removeServiceListeners(XBundle bundle) {
        frameworkEvents.removeServiceListeners(bundle);
    }

    public void removeAllServiceListeners() {
        frameworkEvents.removeAllServiceListeners();
    }

    public void fireBundleEvent(XBundle bundle, int type) {
        frameworkEvents.fireBundleEvent(bundle, type);
    }

    public void fireFrameworkEvent(Bundle bundle, int type, Throwable th) {
        frameworkEvents.fireFrameworkEvent(bundle, type, th);
    }

    public void fireServiceEvent(XBundle bundle, int type, ServiceState serviceState) {
        frameworkEvents.fireServiceEvent(bundle, type, serviceState);
    }

    public Collection<ListenerInfo> getServiceListenerInfos(XBundle bundle) {
        return frameworkEvents.getServiceListenerInfos(bundle);
    }

}