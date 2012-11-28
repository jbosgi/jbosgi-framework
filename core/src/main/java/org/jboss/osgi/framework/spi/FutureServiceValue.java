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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceController.Substate;
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

    private final ServiceController<T> controller;
    private final State expectedState;

    public FutureServiceValue(ServiceController<T> controller) {
        this(controller, State.UP);
    }

    public FutureServiceValue(ServiceController<T> controller, State state) {
        if (controller == null)
            throw MESSAGES.illegalArgumentNull("controller");
        if (state == null)
            throw MESSAGES.illegalArgumentNull("state");
        this.controller = controller;
        this.expectedState = state;
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
        return controller.getState() == expectedState;
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

        if (controller.getState() == expectedState)
            return controller.getValue();

        final CountDownLatch latch = new CountDownLatch(1);
        final FutureServiceValue<T> futureServiceValue = this;
        final String serviceName = controller.getName().getCanonicalName();
        ServiceListener<T> listener = new AbstractServiceListener<T>() {

            @Override
            public void listenerAdded(ServiceController<? extends T> controller) {
                State state = controller.getState();
                if (state == expectedState || state == State.START_FAILED)
                    listenerDone(controller);
            }

            @Override
            public void transition(final ServiceController<? extends T> controller, final ServiceController.Transition transition) {
                LOGGER.tracef("transition %s %s => %s", futureServiceValue, serviceName, transition);
                Substate targetState = transition.getAfter();
                switch (expectedState) {
                    case UP:
                        if (targetState == Substate.UP || targetState == Substate.START_FAILED) {
                            listenerDone(controller);
                        }
                        break;
                    case DOWN:
                        if (targetState == Substate.DOWN) {
                            listenerDone(controller);
                        }
                        break;
                    case REMOVED:
                        if (targetState == Substate.REMOVED) {
                            listenerDone(controller);
                        }
                        break;
                }
            }

            private void listenerDone(ServiceController<? extends T> controller) {
                latch.countDown();
            }
        };

        controller.addListener(listener);
        try {
            if (latch.await(timeout, unit) == false) {
                TimeoutException ex = MESSAGES.timeoutGettingService(serviceName);
                throw ex;
            }
        } catch (InterruptedException e) {
            // ignore
        } finally {
            controller.removeListener(listener);
        }

        if (controller.getState() == expectedState)
            return expectedState == State.UP ? controller.getValue() : null;

        Throwable cause = controller.getStartException();
        while (cause instanceof StartException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        throw MESSAGES.cannotGetServiceValue(cause, serviceName);
    }
}
