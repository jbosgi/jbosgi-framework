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
package org.jboss.test.osgi.msc;

import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
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

        // serviceB depends on serviceA
        // serviceA is ON_DEMAND
        // verify that serviceA comes UP

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

        // serviceB depends on serviceA
        // serviceA is ON_DEMAND
        // remove serviceB
        // verify that serviceA goes DOWN

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
    public void testDependencyRemoved() throws Exception {

        // serviceB depends on serviceA
        // serviceA is ON_DEMAND
        // remove serviceA
        // verify that serviceB goes DOWN
        // re-install serviceA
        // verify that serviceB comes UP

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

        controllerA.setMode(Mode.REMOVE);

        new FutureServiceValue<String>(controllerA, State.REMOVED).get();
        Assert.assertNull("value is null", controllerA.getValue());
        Assert.assertEquals(Mode.REMOVE, controllerA.getMode());
        Assert.assertEquals(State.REMOVED, controllerA.getState());

        new FutureServiceValue<String>(controllerB, State.DOWN).get();
        Assert.assertNull("value is null", controllerB.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerB.getMode());
        Assert.assertEquals(State.DOWN, controllerB.getState());

        builderA = serviceTarget.addService(snameA, new ServiceA());
        builderA.setInitialMode(Mode.ON_DEMAND);
        controllerA = builderA.install();

        new FutureServiceValue<String>(controllerB).get();
        Assert.assertEquals(State.UP, controllerA.getState());
    }

    @Test
    public void testDependencySetActive() throws Exception {

        // serviceB depends on serviceA
        // serviceA is ON_DEMAND
        // set serviceA to ACTIVE
        // remove serviceB
        // verify that serviceA stays UP

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

        Assert.assertEquals("serviceA", controllerA.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerA.getMode());
        Assert.assertEquals(State.UP, controllerA.getState());
    }

    @Test
    public void testDependencyTransitions() throws Exception {

        // serviceB depends on serviceA
        // serviceA is ON_DEMAND
        // add a listaner to serviceA
        // set serviceA to ACTIVE
        // remove serviceB
        // verify that the listener is not called

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

        Assert.assertEquals("serviceA", controllerA.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerA.getMode());
        Assert.assertEquals(State.UP, controllerA.getState());

        Assert.assertFalse("Listener not called", listenerCalled.get());
    }

    @Test
    public void testOptionalDependency() throws Exception {

        // serviceB depends on serviceA (optionally)
        // serviceA is ON_DEMAND
        // verify that serviceA comes UP
        // remove serviceA

        ServiceName snameA = ServiceName.of("serviceA");
        ServiceBuilder<String> builderA = serviceTarget.addService(snameA, new ServiceA());
        builderA.setInitialMode(Mode.ON_DEMAND);
        ServiceController<String> controllerA = builderA.install();

        ServiceName snameB = ServiceName.of("serviceB");
        ServiceBuilder<String> builderB = serviceTarget.addService(snameB, new ServiceB());
        builderB.addDependency(DependencyType.OPTIONAL, snameA);
        ServiceController<String> controllerB = builderB.install();

        new FutureServiceValue<String>(controllerB).get();
        Assert.assertEquals("serviceB", controllerB.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerB.getMode());
        Assert.assertEquals(State.UP, controllerB.getState());

        Assert.assertEquals("serviceA", controllerA.getValue());
        Assert.assertEquals(Mode.ON_DEMAND, controllerA.getMode());
        Assert.assertEquals(State.UP, controllerA.getState());

        controllerA.setMode(Mode.REMOVE);

        new FutureServiceValue<String>(controllerA, State.REMOVED).get();
        Assert.assertNull("value is null", controllerA.getValue());
        Assert.assertEquals(Mode.REMOVE, controllerA.getMode());
        Assert.assertEquals(State.REMOVED, controllerA.getState());

        new FutureServiceValue<String>(controllerB).get();
        Assert.assertEquals("serviceB", controllerB.getValue());
        Assert.assertEquals(Mode.ACTIVE, controllerB.getMode());
        Assert.assertEquals(State.UP, controllerB.getState());
    }

}
