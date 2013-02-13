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
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;

/**
 * A plugin that manages {@link FrameworkListener}, {@link BundleListener}, {@link ServiceListener} and their associated
 * {@link FrameworkEvent}, {@link BundleEvent}, {@link ServiceEvent}.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class FrameworkEventsPlugin extends ExecutorServicePlugin<FrameworkEvents> {

    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();

    FrameworkEventsPlugin() {
        super(IntegrationServices.FRAMEWORK_EVENTS, "Framework Events Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkEvents> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(IntegrationServices.LOCK_MANAGER, LockManager.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected FrameworkEvents createServiceValue(StartContext startContext) throws StartException {
        LockManager lockManager = injectedLockManager.getValue();
        return new FrameworkEventsImpl(getBundleManager(), getExecutorService(), lockManager);
    }

    @Override
    public void stop(StopContext context) {
        FrameworkEvents events = getValue();
        events.removeAllBundleListeners();
        events.removeAllFrameworkListeners();
        events.removeAllServiceListeners();
    }
}