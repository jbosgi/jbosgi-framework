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
import org.jboss.osgi.framework.internal.BundleLifecycleImpl;

/**
 * An integration point for the bundle lifecycle.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public class BundleLifecyclePlugin extends AbstractIntegrationService<BundleLifecycle> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();

    public BundleLifecyclePlugin() {
        super(IntegrationServices.BUNDLE_LIFECYCLE_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleLifecycle> builder) {
        builder.addDependency(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected BundleLifecycle createServiceValue(StartContext startContext) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        return new BundleLifecycleImpl(bundleManager);
    }
}