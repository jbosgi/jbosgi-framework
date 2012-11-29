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

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;

public abstract class AbstractIntegrationService<T> extends AbstractService<T> implements IntegrationService<T> {

    private final ServiceName serviceName;
    private T serviceValue;

    public AbstractIntegrationService(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public ServiceName getServiceName() {
        return serviceName;
    }

    @Override
    public ServiceController<T> install(ServiceTarget serviceTarget, ServiceListener<Object> listener) {
        ServiceBuilder<T> builder = serviceTarget.addService(getServiceName(), this);
        addServiceDependencies(builder);
        builder.addListener(listener);
        return builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<T> builder) {
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        serviceValue = createServiceValue(startContext);
    }

    protected abstract T createServiceValue(StartContext startContext) throws StartException;

    @Override
    public T getValue() throws IllegalStateException {
        return serviceValue;
    }
}