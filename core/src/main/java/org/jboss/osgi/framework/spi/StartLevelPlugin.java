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
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.StartLevelImpl;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.startlevel.StartLevel;

/**
 * A plugin for the deprecated {@link StartLevel} service.
 *
 * @author thomas.diesler@jboss.com
 * @since 13-Mar-2013
 */
@SuppressWarnings("deprecation")
public class StartLevelPlugin extends AbstractIntegrationService<StartLevel> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private ServiceRegistration<StartLevel> registration;

    public StartLevelPlugin() {
        super(IntegrationServices.DEPRECATED_START_LEVEL_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<StartLevel> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, injectedSystemContext);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        super.start(startContext);
        BundleContext systemContext = injectedSystemContext.getValue();
        registration = systemContext.registerService(StartLevel.class, getValue(), null);
    }

    @Override
    protected StartLevel createServiceValue(StartContext startContext) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        return new StartLevelImpl(bundleManager);
    }

    @Override
    public void stop(StopContext context) {
        registration.unregister();
    }
}