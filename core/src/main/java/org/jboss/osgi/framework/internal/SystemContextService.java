/*
 * #%L
 * JBossOSGi Framework Core
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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * Represents the the system {@link BundleContext}.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class SystemContextService implements Service<BundleContext> {

    final InjectedValue<Bundle> injectedSystemBundle = new InjectedValue<Bundle>();

    static void addService(ServiceTarget serviceTarget) {
        SystemContextService service = new SystemContextService();
        ServiceBuilder<BundleContext> builder = serviceTarget.addService(Services.SYSTEM_CONTEXT, service);
        builder.addDependency(Services.SYSTEM_BUNDLE, Bundle.class, service.injectedSystemBundle);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private SystemContextService() {
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public BundleContext getValue()  {
        return injectedSystemBundle.getValue().getBundleContext();
    }
}