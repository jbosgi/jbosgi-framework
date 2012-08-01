package org.jboss.osgi.framework;
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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;

public abstract class BootstrapBundlesService<T> extends AbstractService<T> implements IntegrationService<T> {

    private final ServiceName baseName;
    private final IntegrationService.BootstrapPhase phase;

    public BootstrapBundlesService(ServiceName baseName, IntegrationService.BootstrapPhase phase) {
        this.baseName = baseName;
        this.phase = phase;
    }

    @Override
    public ServiceName getServiceName() {
        return IntegrationService.BootstrapPhase.serviceName(baseName, phase);
    }

    public ServiceName getPreviousService() {
        return IntegrationService.BootstrapPhase.serviceName(baseName, phase.previous());
    }

    public ServiceName getNextService() {
        return IntegrationService.BootstrapPhase.serviceName(baseName, phase.next());
    }

    @Override
    public void start(StartContext context) throws StartException {
        ServiceController<?> serviceController = context.getController();
        LOGGER.tracef("Starting: %s in mode %s", serviceController.getName(), serviceController.getMode());
    }
}