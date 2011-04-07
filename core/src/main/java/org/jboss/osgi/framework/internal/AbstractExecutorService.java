/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;


/**
 * Plugin that provides an ExecutorService.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Mar-2011
 */
abstract class AbstractExecutorService<T> extends AbstractPluginService<T> {

    private ExecutorService executorService;
    private AtomicInteger threadCount = new AtomicInteger();
    private String typeName;

    AbstractExecutorService(String executorType) {
        this.typeName = executorType;
    }

    
    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        executorService = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable run) {
                Thread thread = new Thread(run);
                thread.setName(String.format("OSGi " + typeName + " Thread-%d", threadCount.incrementAndGet()));
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        executorService.shutdown();
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
}