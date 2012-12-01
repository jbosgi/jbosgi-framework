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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.Services;
import org.osgi.service.startlevel.StartLevel;

/**
 * An integration service.
 *
 * @author thomas.diesler@jboss.com
 * @since 01-Aug-2012
 */
public interface IntegrationService<T> extends Service<T> {

    /** The service name for the {@link StartLevel} service */
    ServiceName START_LEVEL_SUPPORT = Services.JBOSGI_BASE_NAME.append("StartLevelSupport");

    ServiceName getServiceName();

    ServiceController<T> install(ServiceTarget serviceTarget, ServiceListener<Object> listener);

}
