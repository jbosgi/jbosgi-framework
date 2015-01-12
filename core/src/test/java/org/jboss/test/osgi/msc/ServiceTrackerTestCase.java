package org.jboss.test.osgi.msc;
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


import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.framework.spi.ServiceTracker;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test the {@link ServiceTracker}
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

        ServiceTracker<Object> tracker = new ServiceTracker<Object>();
        ServiceBuilder<String> builder = serviceTarget.addService(ServiceName.of("serviceA"), new ServiceA());
        builder.addListener(tracker);
        builder.install();

        boolean outcome = tracker.awaitCompletion(500, TimeUnit.MILLISECONDS);
        Assert.assertTrue("All complete", outcome);
    }

    @Test
    public void testServiceSetTracker() throws Exception {

        final Set<ServiceName> expected = new HashSet<ServiceName>();
        for (int i = 0; i < 10; i++) {
            expected.add(ServiceName.of("service" + i));
        }

        final Set<ServiceName> names = new HashSet<ServiceName>();
        ServiceTracker<Object> tracker = new ServiceTracker<Object>() {

            @Override
            protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
                return expected.equals(trackedServices);
            }

            @Override
            public void serviceStarted(ServiceController<? extends Object> controller) {
                names.add(controller.getName());
            }
        };

        for (ServiceName serviceName : expected) {
            Thread.sleep(10);
            ServiceBuilder<String> builder = serviceTarget.addService(serviceName, new TestService());
            builder.addListener(tracker);
            builder.install();
        }

        boolean outcome = tracker.awaitCompletion(500, TimeUnit.MILLISECONDS);
        Assert.assertTrue("All complete", outcome);
        Assert.assertEquals("All services started", 10, names.size());

    }

    @Test
    public void testDependencyFailed() throws Exception {

        ServiceTracker<Object> tracker = new ServiceTracker<Object>();
        ServiceBuilder<String> builder = serviceTarget.addService(ServiceName.of("serviceA"), new ServiceA());
        builder.addDependency(ServiceName.of("serviceB"));
        builder.addListener(tracker);
        builder.install();

        builder = serviceTarget.addService(ServiceName.of("serviceB"), new ServiceB() {
            @Override
            public void start(StartContext context) throws StartException {
                throw new StartException("serviceB failed");
            }
        });
        builder.addListener(tracker);
        builder.install();

        boolean outcome = tracker.awaitCompletion(500, TimeUnit.MILLISECONDS);
        Assert.assertFalse("All complete", outcome);
    }


    @Test
    @Ignore
    public void testUntrackedDependencyFailed() throws Exception {

        ServiceTracker<Object> tracker = new ServiceTracker<Object>();
        ServiceBuilder<String> builder = serviceTarget.addService(ServiceName.of("serviceA"), new ServiceA());
        builder.addDependency(ServiceName.of("serviceB"));
        builder.addListener(tracker);
        builder.install();

        builder = serviceTarget.addService(ServiceName.of("serviceB"), new ServiceB() {
            @Override
            public void start(StartContext context) throws StartException {
                throw new StartException("serviceB failed");
            }
        });
        // don't add the tracker to this service
        builder.install();

        boolean outcome = tracker.awaitCompletion(500, TimeUnit.MILLISECONDS);
        Assert.assertTrue("All complete", outcome);
    }

    @Test
    public void testTransitionToNever() throws Exception {

        ServiceTracker<Object> tracker = new ServiceTracker<Object>();
        ServiceBuilder<String> builder = serviceTarget.addService(ServiceName.of("serviceA"), new ServiceA());
        builder.addListener(tracker);
        ServiceController<String> controller = builder.install();

        controller.setMode(Mode.NEVER);

        boolean outcome = tracker.awaitCompletion(500, TimeUnit.MILLISECONDS);
        Assert.assertTrue("All complete", outcome);
    }
}
