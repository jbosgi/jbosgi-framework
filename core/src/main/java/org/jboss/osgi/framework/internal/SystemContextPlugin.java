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
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.osgi.framework.BundleContext;

/**
 * Represents the the system {@link BundleContext}.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class SystemContextPlugin extends AbstractIntegrationService<BundleContext> {

    final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();

    SystemContextPlugin() {
        super(IntegrationServices.SYSTEM_CONTEXT_INTERNAL);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleContext> builder) {
        builder.addDependency(IntegrationServices.SYSTEM_BUNDLE_INTERNAL, SystemBundleState.class, injectedSystemBundle);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected BundleContext createServiceValue(StartContext startContext) throws StartException {
        SystemBundleState systemBundle = injectedSystemBundle.getValue();
        return systemBundle.getBundleContext();
    }
}