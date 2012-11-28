package org.jboss.osgi.framework.spi;
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

import java.util.concurrent.ExecutorService;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;


/**
 * Plugin that provides an ExecutorService.
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Mar-2011
 */
public abstract class ExecutorServicePlugin<T> extends AbstractIntegrationService<T> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final String threadName;
    private ExecutorService executorService;

    protected ExecutorServicePlugin(ServiceName serviceName, String threadName) {
        super(serviceName);
        this.threadName = threadName;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<T> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
    }

    @Override
    public void start(StartContext context) throws StartException {
        executorService = getBundleManager().createExecutorService(threadName);
    }

    protected BundleManager getBundleManager() {
        return injectedBundleManager.getValue();
    }

    @Override
    public void stop(StopContext context) {
        executorService.shutdown();
    }

    protected ExecutorService getExecutorService() {
        return executorService;
    }
}