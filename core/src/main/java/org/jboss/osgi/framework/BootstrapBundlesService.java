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

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.IntegrationServices.BootstrapPhase;

public class BootstrapBundlesService<T> extends AbstractService<T> {

    private final ServiceName baseName;
    private final BootstrapPhase phase;

    public BootstrapBundlesService(ServiceName baseName, BootstrapPhase phase) {
        this.baseName = baseName;
        this.phase = phase;
    }

    public ServiceName getServiceName() {
        return BootstrapPhase.serviceName(baseName, phase);
    }

    public ServiceName getPreviousService() {
        return BootstrapPhase.serviceName(baseName, phase.previous());
    }

    public ServiceName getNextService() {
        return BootstrapPhase.serviceName(baseName, phase.next());
    }
}