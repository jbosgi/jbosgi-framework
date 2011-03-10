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
package org.jboss.osgi.framework.plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.osgi.framework.bundle.BundleManager;

/**
 * Plugin that provides an ExecutorService.
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Mar-2011
 */
public abstract class AbstractExecutorServicePlugin extends AbstractPlugin implements ExecutorServicePlugin {

    private ExecutorService executorService;
    private AtomicInteger threadCount = new AtomicInteger();
    private String typeName;

    public AbstractExecutorServicePlugin(BundleManager bundleManager, String executorType) {
        super(bundleManager);
        this.typeName = executorType;
    }

    @Override
    public void startPlugin() {
        if (executorService == null) {
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
    }

    @Override
    public void stopPlugin() {
        try {
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
            executorService = null;
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }
}