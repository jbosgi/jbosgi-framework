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

import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.FutureServiceValue;
import org.junit.Test;

/**
 * Test service dependency use cases.
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 19-Apr-2012
 */
public class ServiceDependenciesTestCase extends AbstractServiceTestCase {

    @Test
    public void testSimpleOnDemandDependency() throws Exception {

        ServiceName snameA = ServiceName.of("serviceA");
        ServiceBuilder<String> builderA = serviceTarget.addService(snameA, new ServiceA());
        builderA.setInitialMode(Mode.ON_DEMAND);
        ServiceController<String> controllerA = builderA.install();

        ServiceName snameB = ServiceName.of("serviceB");
        ServiceBuilder<String> builderB = serviceTarget.addService(snameB, new ServiceB());
        builderB.addDependency(snameA);
        ServiceController<String> controllerB = builderB.install();

        new FutureServiceValue<String>(controllerB).get();
        Assert.assertEquals("serviceB", controllerB.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerB.getMode());
        Assert.assertEquals(State.UP, controllerB.getState());

        Assert.assertEquals("serviceA", controllerA.getValue());
        Assert.assertEquals(Mode.ON_DEMAND, controllerA.getMode());
        Assert.assertEquals(State.UP, controllerA.getState());
    }

    @Test
    public void testDependeeRemoved() throws Exception {

        ServiceName snameA = ServiceName.of("serviceA");
        ServiceBuilder<String> builderA = serviceTarget.addService(snameA, new ServiceA());
        builderA.setInitialMode(Mode.ON_DEMAND);
        ServiceController<String> controllerA = builderA.install();

        ServiceName snameB = ServiceName.of("serviceB");
        ServiceBuilder<String> builderB = serviceTarget.addService(snameB, new ServiceB());
        builderB.addDependency(snameA);
        ServiceController<String> controllerB = builderB.install();

        new FutureServiceValue<String>(controllerB).get();
        Assert.assertEquals(State.UP, controllerA.getState());

        controllerB.setMode(Mode.REMOVE);

        new FutureServiceValue<String>(controllerB, State.REMOVED).get();
        Assert.assertNull("value is null", controllerB.getValue());
        Assert.assertEquals(Mode.REMOVE, controllerB.getMode());
        Assert.assertEquals(State.REMOVED, controllerB.getState());

        new FutureServiceValue<String>(controllerA, State.DOWN).get();
        Assert.assertNull("value is null", controllerA.getValue());
        Assert.assertEquals(Mode.ON_DEMAND, controllerA.getMode());
        Assert.assertEquals(State.DOWN, controllerA.getState());
    }

    @Test
    public void testDependencySetActive() throws Exception {

        ServiceName snameA = ServiceName.of("serviceA");
        ServiceBuilder<String> builderA = serviceTarget.addService(snameA, new ServiceA());
        builderA.setInitialMode(Mode.ON_DEMAND);
        ServiceController<String> controllerA = builderA.install();

        ServiceName snameB = ServiceName.of("serviceB");
        ServiceBuilder<String> builderB = serviceTarget.addService(snameB, new ServiceB());
        builderB.addDependency(snameA);
        ServiceController<String> controllerB = builderB.install();

        new FutureServiceValue<String>(controllerB).get();
        Assert.assertEquals(State.UP, controllerA.getState());

        controllerA.setMode(Mode.ACTIVE);
        controllerB.setMode(Mode.REMOVE);

        new FutureServiceValue<String>(controllerB, State.REMOVED).get();
        Assert.assertNull("value is null", controllerB.getValue());
        Assert.assertEquals(Mode.REMOVE, controllerB.getMode());
        Assert.assertEquals(State.REMOVED, controllerB.getState());

        Thread.sleep(200); // not expecting transitions
        Assert.assertEquals("serviceA", controllerA.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerA.getMode());
        Assert.assertEquals(State.UP, controllerA.getState());
    }

    @Test
    public void testDependencyTransitions() throws Exception {

        ServiceName snameA = ServiceName.of("serviceA");
        ServiceBuilder<String> builderA = serviceTarget.addService(snameA, new ServiceA());
        builderA.setInitialMode(Mode.ON_DEMAND);
        ServiceController<String> controllerA = builderA.install();

        final AtomicBoolean listenerCalled = new AtomicBoolean();
        controllerA.addListener(new AbstractServiceListener<String>() {
            public void transition(final ServiceController<? extends String> controller, final ServiceController.Transition transition) {
                switch (transition) {
                    case STARTING_to_START_FAILED:
                    case STOPPING_to_DOWN:
                        controller.removeListener(this);
                        listenerCalled.set(true);
                        break;
                }
            }
        });

        ServiceName snameB = ServiceName.of("serviceB");
        ServiceBuilder<String> builderB = serviceTarget.addService(snameB, new ServiceB());
        builderB.addDependency(snameA);
        ServiceController<String> controllerB = builderB.install();

        new FutureServiceValue<String>(controllerB).get();
        Assert.assertEquals(State.UP, controllerA.getState());

        controllerA.setMode(Mode.ACTIVE);
        controllerB.setMode(Mode.REMOVE);

        new FutureServiceValue<String>(controllerB, State.REMOVED).get();
        Assert.assertNull("value is null", controllerB.getValue());
        Assert.assertEquals(Mode.REMOVE, controllerB.getMode());
        Assert.assertEquals(State.REMOVED, controllerB.getState());

        Thread.sleep(200); // not expecting transitions
        Assert.assertEquals("serviceA", controllerA.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerA.getMode());
        Assert.assertEquals(State.UP, controllerA.getState());

        Assert.assertFalse("Listener not called", listenerCalled.get());
    }
}
