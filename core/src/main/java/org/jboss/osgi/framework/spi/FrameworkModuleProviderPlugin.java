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
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.FrameworkModuleProviderImpl;

/**
 * The system module provider plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
public class FrameworkModuleProviderPlugin extends AbstractIntegrationService<FrameworkModuleProvider> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<SystemPaths> injectedSystemPaths = new InjectedValue<SystemPaths>();

    public FrameworkModuleProviderPlugin() {
        super(IntegrationServices.FRAMEWORK_MODULE_PLUGIN);
    }


    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkModuleProvider> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(IntegrationServices.SYSTEM_PATHS_PLUGIN, SystemPaths.class, injectedSystemPaths);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected FrameworkModuleProvider createServiceValue(StartContext startContext) {
        SystemPaths systemPaths = injectedSystemPaths.getValue();
        BundleManager bundleManager = injectedBundleManager.getValue();
        return new FrameworkModuleProviderImpl(bundleManager, systemPaths);
    }
}