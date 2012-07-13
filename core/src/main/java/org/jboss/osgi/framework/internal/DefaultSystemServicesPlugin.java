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

import static org.jboss.osgi.framework.IntegrationServices.SYSTEM_SERVICES_PLUGIN;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.SystemServicesPlugin;
import org.osgi.framework.BundleContext;

/**
 * A noop placeholder for additional system services
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Feb-2011
 */
final class DefaultSystemServicesPlugin extends AbstractPluginService<SystemServicesPlugin> implements SystemServicesPlugin {

    static void addIntegrationService(ServiceRegistry registry, ServiceTarget serviceTarget) {
        if (registry.getService(SYSTEM_SERVICES_PLUGIN) == null) {
            DefaultSystemServicesPlugin service = new DefaultSystemServicesPlugin();
            ServiceBuilder<SystemServicesPlugin> builder = serviceTarget.addService(SYSTEM_SERVICES_PLUGIN, service);
            builder.setInitialMode(Mode.ON_DEMAND);
            builder.install();
        }
    }

    private DefaultSystemServicesPlugin() {
    }


    @Override
    public void registerSystemServices(BundleContext context) {
        // do nothing
    }

    @Override
    public DefaultSystemServicesPlugin getValue() {
        return this;
    }
}