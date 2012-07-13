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
package org.jboss.osgi.framework;

import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * Default implementation for the COMPLETE step of the {@link AutoInstallPlugin}.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
public abstract class AutoInstallComplete extends AbstractInstallComplete {

    protected ServiceName getServiceName() {
        return IntegrationServices.AUTOINSTALL_COMPLETE;
    }

    @Override
    protected void configureDependencies(ServiceBuilder<Void> builder) {
        builder.addDependency(IntegrationServices.AUTOINSTALL_PLUGIN);
    }

    @Override
    protected abstract boolean allServicesAdded(Set<ServiceName> trackedServices);
}
