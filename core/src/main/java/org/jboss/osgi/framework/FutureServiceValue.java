/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.osgi.framework;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.StartException;

/**
 * A Future that waits for the given service to come up and returns it's value.
 *
 * Use cautiously and only if there is no way to use direct service dependencies instead.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Mar-2011
 */
public final class FutureServiceValue<T> implements Future<T> {

    private ServiceController<T> controller;

    public FutureServiceValue(ServiceController<T> controller) {
        if (controller == null)
            throw MESSAGES.illegalArgumentNull("controller");
       this.controller = controller;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return controller.getState() == State.UP;
    }

    @Override
    public T get() throws ExecutionException {
        try {
            return get(5, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            throw new ExecutionException(ex);
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {
        return getValue(timeout, unit);
    }

    private T getValue(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException {

        if (controller.getState() == State.UP)
            return controller.getValue();

        final CountDownLatch latch = new CountDownLatch(1);
        final FutureServiceValue<T> futureServiceValue = this;
        final String serviceName = controller.getName().getCanonicalName();
        ServiceListener<T> listener = new AbstractServiceListener<T>() {

            @Override
            public void listenerAdded(ServiceController<? extends T> controller) {
                State state = controller.getState();
                if (state == State.UP || state == State.START_FAILED)
                    listenerDone(controller);
            }

            public void transition(final ServiceController<? extends T> controller, final ServiceController.Transition transition) {
                LOGGER.tracef("transition %s %s => %s", futureServiceValue, serviceName, transition);
                switch (transition) {
                    case STARTING_to_UP:
                    case STARTING_to_START_FAILED:
                        listenerDone(controller);
                        break;
                }
            }
            
            private void listenerDone(ServiceController<? extends T> controller) {
                controller.removeListener(this);
                latch.countDown();
            }
        };
        controller.addListener(listener);

        try {
            if (latch.await(timeout, unit) == false) {
                throw MESSAGES.timeoutGettingService(serviceName);
            }
        } catch (InterruptedException e) {
            // ignore;
        }

        if (controller.getState() == State.UP)
            return controller.getValue();

        StartException startException = controller.getStartException();
        Throwable cause = startException != null ? startException.getCause() : startException;
        if (cause instanceof RuntimeException) { 
            throw (RuntimeException)cause;
        }
        throw MESSAGES.executionCannotGetServiceValue(cause, serviceName);
    }
}