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

import java.util.concurrent.ExecutionException;

import junit.framework.Assert;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.framework.FutureServiceValue;
import org.junit.Test;

/**
 * Test service activation use cases.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 27-Jun-2012
 */
public class ServiceActivationTestCase extends AbstractServiceTestCase {

    @Test
    public void testSimpleServiceActivation() throws Exception {

        ServiceName snameA = ServiceName.of("serviceA");
        ServiceBuilder<String> builderA = serviceTarget.addService(snameA, new ServiceA());
        ServiceController<String> controllerA = builderA.install();
        ServiceActivator activatorA = new ServiceActivator(controllerA);

        // Test that a service can be activated/deactivated synchronously through API
        activatorA.start();
        Assert.assertEquals(State.UP, controllerA.getState());

        activatorA.stop();
        Assert.assertEquals(State.DOWN, controllerA.getState());

        activatorA.start();
        Assert.assertEquals(State.UP, controllerA.getState());

        activatorA.stop();
        Assert.assertEquals(State.DOWN, controllerA.getState());
    }

    @Test
    public void testNestedServiceActivation() throws Exception {

        ServiceName snameA = ServiceName.of("serviceA");
        ServiceBuilder<String> builderA = serviceTarget.addService(snameA, new ServiceA());
        final ServiceController<String> controllerA = builderA.install();
        final ServiceActivator activatorA = new ServiceActivator(controllerA);

        ServiceName snameB = ServiceName.of("serviceB");
        ServiceBuilder<String> builderB = serviceTarget.addService(snameB, new ServiceB() {

            @Override
            public void start(StartContext context) throws StartException {
                super.start(context);
                try {
                    activatorA.start();
                } catch (ExecutionException e) {
                    throw new StartException(e);
                }
            }

            @Override
            public void stop(StopContext context) {
                activatorA.stop();
                super.stop(context);
            }
        });
        ServiceController<String> controllerB = builderB.install();
        ServiceActivator activatorB = new ServiceActivator(controllerB);

        // Test that a service can be activated/deactivated through another service
        activatorB.start();
        Assert.assertEquals(State.UP, controllerA.getState());
        Assert.assertEquals(State.UP, controllerB.getState());

        activatorB.stop();
        Assert.assertEquals(State.DOWN, controllerA.getState());
        Assert.assertEquals(State.DOWN, controllerB.getState());

        activatorB.start();
        Assert.assertEquals(State.UP, controllerA.getState());
        Assert.assertEquals(State.UP, controllerB.getState());

        activatorB.stop();
        Assert.assertEquals(State.DOWN, controllerA.getState());
        Assert.assertEquals(State.DOWN, controllerB.getState());
    }

    static class ServiceActivator {

        final ServiceController<String> controller;
        public ServiceActivator(ServiceController<String> controller) {
            this.controller = controller;
        }

        void start() throws ExecutionException {
            FutureServiceValue<String> future = new FutureServiceValue<String>(controller, State.UP);
            controller.setMode(Mode.ACTIVE);
            future.get();
        }

        void stop() {
            FutureServiceValue<String> future = new FutureServiceValue<String>(controller, State.DOWN);
            controller.setMode(Mode.NEVER);
            try {
                future.get();
            } catch (ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
