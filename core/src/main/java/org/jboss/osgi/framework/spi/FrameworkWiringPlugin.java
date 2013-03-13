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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.FrameworkWiringImpl;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.wiring.FrameworkWiring;

/**
 * An implementation of the {@link FrameworkWiringSupport} service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 08-Nov-2012
 */
public class FrameworkWiringPlugin extends ExecutorServicePlugin<FrameworkWiring>  {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<FrameworkEvents> injectedFrameworkEvents = new InjectedValue<FrameworkEvents>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<XResolver> injectedResolver = new InjectedValue<XResolver>();

    public FrameworkWiringPlugin() {
        super(IntegrationServices.FRAMEWORK_WIRING_PLUGIN, "Framework Refresh Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkWiring> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(IntegrationServices.FRAMEWORK_EVENTS_PLUGIN, FrameworkEvents.class, injectedFrameworkEvents);
        builder.addDependency(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
        builder.addDependency(Services.RESOLVER, XResolver.class, injectedResolver);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected FrameworkWiring createServiceValue(StartContext startContext) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        FrameworkEvents events = injectedFrameworkEvents.getValue();
        XEnvironment env = injectedEnvironment.getValue();
        XResolver resolver = injectedResolver.getValue();
        LockManager lockManager = injectedLockManager.getValue();
        return new FrameworkWiringImpl(bundleManager, events, env, resolver, lockManager, getExecutorService());
    }
}
