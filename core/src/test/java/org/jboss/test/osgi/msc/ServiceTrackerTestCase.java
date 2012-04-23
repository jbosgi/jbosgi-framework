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
package org.jboss.test.osgi.msc;


import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.util.ServiceTracker;
import org.junit.Test;

/**
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 19-Apr-2012
 */
public class ServiceTrackerTestCase extends AbstractServiceTestCase {

    @Test
    public void testImmediateCallToListenerAdded() throws Exception {

        final AtomicBoolean listenerAdded = new AtomicBoolean();
        ServiceListener<Object> listener = new AbstractServiceListener<Object>() {

            @Override
            public void listenerAdded(ServiceController<? extends Object> controller) {
                listenerAdded.set(true);
            }
        };
        ServiceBuilder<String> builder = serviceTarget.addService(ServiceName.of("serviceA"), new ServiceA());
        builder.addListener(listener);
        builder.install();

        Assert.assertTrue("Listener added", listenerAdded.get());
    }

    @Test
    public void testSimpleTracker() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        ServiceListener<Object> listener = new ServiceTracker<Object>() {

            @Override
            public void complete() {
                latch.countDown();
            }
        };
        ServiceBuilder<String> builder = serviceTarget.addService(ServiceName.of("serviceA"), new ServiceA());
        builder.addListener(listener);
        builder.install();

        boolean outcome = latch.await(500, TimeUnit.MILLISECONDS);
        Assert.assertTrue("All complete", outcome);
    }

    @Test
    public void testServiceSetTracker() throws Exception {

        final Set<ServiceName> expected = new HashSet<ServiceName>();
        for (int i = 0; i < 10; i++) {
            expected.add(ServiceName.of("service" + i));
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final Set<ServiceName> names = new HashSet<ServiceName>();
        ServiceListener<Object> listener = new ServiceTracker<Object>() {

            @Override
            protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
                return expected.equals(trackedServices);
            }

            @Override
            public void serviceStarted(ServiceController<? extends Object> controller) {
                names.add(controller.getName());
            }

            @Override
            public void complete() {
                latch.countDown();
            }
        };

        for (ServiceName serviceName : expected) {
            Thread.sleep(10);
            ServiceBuilder<String> builder = serviceTarget.addService(serviceName, new TestService());
            builder.addListener(listener);
            builder.install();
        }

        boolean outcome = latch.await(500, TimeUnit.MILLISECONDS);
        Assert.assertTrue("All complete", outcome);
        Assert.assertEquals("All services started", 10, names.size());

    }
}
