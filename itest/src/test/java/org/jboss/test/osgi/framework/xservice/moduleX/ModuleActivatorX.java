/*
 * #%L
 * JBossOSGi Framework iTest
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
package org.jboss.test.osgi.framework.xservice.moduleX;

import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.modules.ModuleActivator;
import org.jboss.osgi.modules.ModuleContext;

/**
 * A Service Activator
 * 
 * @author thomas.diesler@jboss.com
 * @since 24-Apr-2009
 */
public class ModuleActivatorX implements ModuleActivator {

    private ServiceName serviceName;

    @Override
    public void start(final ModuleContext context) throws ModuleLoadException {
        ServiceTarget serviceTarget = context.getServiceContainer().subTarget();
        serviceName = context.getServiceName(ModuleServiceX.class);

        Service<ModuleServiceX> service = new Service<ModuleServiceX>() {

            ModuleServiceX value = new ModuleServiceX(context.getBundle());

            @Override
            public ModuleServiceX getValue() throws IllegalStateException {
                return value;
            }

            @Override
            public void start(StartContext context) throws StartException {
            }

            @Override
            public void stop(StopContext context) {
            }
        };

        ServiceBuilder<ModuleServiceX> serviceBuilder = serviceTarget.addService(serviceName, service);
        serviceBuilder.setInitialMode(Mode.PASSIVE).install();
    }

    @Override
    public void stop(ModuleContext context) {
        if (serviceName != null) {
            ServiceController<?> service = context.getServiceContainer().getService(serviceName);
            service.setMode(Mode.REMOVE);
        }
    }
}