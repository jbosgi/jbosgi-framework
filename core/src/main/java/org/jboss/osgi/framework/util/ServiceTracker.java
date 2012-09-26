package org.jboss.osgi.framework.util;

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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceController.Transition;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 *
 * @author Thomas.Diesler@jboss.com
 *
 * @since 19-Apr-2012
 */
public class ServiceTracker<S> extends AbstractServiceListener<S> {

    private final Set<ServiceName> addedNames = new HashSet<ServiceName>();
    private final Set<ServiceController<? extends S>> trackedControllers = new HashSet<ServiceController<? extends S>>();
    private final AtomicBoolean allComplete = new AtomicBoolean();
    private final boolean completeOnFirstFailure;

    public ServiceTracker() {
        this(true);
    }

    public ServiceTracker(boolean completeOnFail) {
        this.completeOnFirstFailure = completeOnFail;
    }

    @Override
    public void listenerAdded(ServiceController<? extends S> controller) {
        synchronized (trackedControllers) {
            if (trackService(controller)) {
                LOGGER.tracef("ServiceTracker controller added: %s", controller);
                addedNames.add(controller.getName());
                serviceListerAddedInternal(controller);
                State state = controller.getState();
                switch (state) {
                    case UP:
                        serviceStarted(controller);
                        break;
                    case START_FAILED:
                        serviceStartFailed(controller, controller.getStartException());
                        break;
                    default:
                        trackedControllers.add(controller);
                }
            } else {
                controller.removeListener(this);
            }
        }
    }

    @Override
    public void transition(ServiceController<? extends S> controller, Transition transition) {
        synchronized (trackedControllers) {
            Service<? extends S> service = controller.getService();
            switch (transition) {
                case STARTING_to_UP:
                    if (!(service instanceof SynchronousListenerService)) {
                        LOGGER.tracef("ServiceTracker transition to UP: " + controller.getName());
                        serviceStarted(controller);
                        serviceCompleteInternal(controller);
                    }
                    break;
                case STARTING_to_START_FAILED:
                    if (!(service instanceof SynchronousListenerService)) {
                        LOGGER.tracef("ServiceTracker transition to START_FAILED: " + controller.getName());
                        serviceStartFailed(controller, controller.getStartException());
                        serviceCompleteInternal(controller);
                    }
                    break;
            }
        }
    }

    public void synchronousListenerServiceStarted(ServiceController<? extends S> controller) {
        synchronized (trackedControllers) {
            LOGGER.tracef("Synchronous listener service started: " + controller.getName());
            serviceStarted(controller);
            serviceCompleteInternal(controller);
        }
    }

    public void synchronousListenerServiceFailed(ServiceController<? extends S> controller, Throwable th) {
        synchronized (trackedControllers) {
            LOGGER.tracef("Synchronous listener service failed: " + controller.getName());
            serviceStartFailed(controller, th);
            serviceCompleteInternal(controller);
        }
    }

    public void checkAndComplete() {
        synchronized (trackedControllers) {
            if (trackedControllers.size() == 0 && allServicesAdded(Collections.unmodifiableSet(addedNames))) {
                completeInternal();
            }
        }
    }

    protected boolean trackService(ServiceController<? extends S> controller) {
        return true;
    }

    protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
        return true;
    }

    protected void serviceListenerAdded(ServiceController<? extends S> controller) {
    }

    protected void serviceStartFailed(ServiceController<? extends S> controller, Throwable th) {
    }

    protected void serviceStarted(ServiceController<? extends S> controller) {
    }

    protected void complete() {
    }

    @SuppressWarnings("unchecked")
    private void serviceListerAddedInternal(ServiceController<? extends S> controller) {
        serviceListenerAdded(controller);
        Service<? extends S> service = controller.getService();
        if (service instanceof SynchronousListenerService) {
            SynchronousListenerService<S> sync = (SynchronousListenerService<S>) service;
            sync.addListener(this);
        }
    }

    @SuppressWarnings("unchecked")
    private void serviceListerRemoveInternal(ServiceController<? extends S> controller) {
        Service<? extends S> service = controller.getService();
        if (service instanceof SynchronousListenerService) {
            SynchronousListenerService<S> sync = (SynchronousListenerService<S>) service;
            sync.removeListener(this);
        }
    }

    private void serviceCompleteInternal(ServiceController<? extends S> controller) {
        trackedControllers.remove(controller);
        serviceListerRemoveInternal(controller);
        controller.removeListener(this);

        if (!completeOnFirstFailure || controller.getStartException() == null) {
            checkAndComplete();
            return;
        }

        // Remove all tracked services and complete the tracker
        Iterator<ServiceController<? extends S>> iterator = trackedControllers.iterator();
        while (iterator.hasNext()) {
            ServiceController<? extends S> aux = iterator.next();
            serviceListerRemoveInternal(aux);
            aux.removeListener(this);
            iterator.remove();
        }

        completeInternal();
    }

    private void completeInternal() {
        if (allComplete.compareAndSet(false, true)) {
            LOGGER.tracef("ServiceTracker complete: " + getClass().getName());
            complete();
        }
    }

    public interface SynchronousListenerService<T> extends Service<T> {
        void addListener(ServiceTracker<T> listener);

        void removeListener(ServiceTracker<T> listener);

        void startCompleted(final StartContext context);

        void startFailed(StartContext context, Throwable th);
    }

    /**
     * Service wrapper implementation that can be used with {@link ServiceTracker}. This service wrapper invokes the listener
     * methods after the delegate's start method has been invoked.
     *
     * @author Stuart Douglas
     * @author Thomas.Diesler@jboss.com
     * @since
     */
    public static class SynchronousListenerServiceWrapper<T> implements SynchronousListenerService<T> {

        private final Set<ServiceTracker<T>> listeners = new HashSet<ServiceTracker<T>>();
        private final Service<T> delegate;

        public SynchronousListenerServiceWrapper(final Service<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void addListener(ServiceTracker<T> listener) {
            listeners.add(listener);
        }

        @Override
        public synchronized void removeListener(ServiceTracker<T> listener) {
            listeners.remove(listener);
        }

        @Override
        public void start(final StartContext context) throws StartException {
            try {
                delegate.start(context);
                startCompleted(context);
            } catch (StartException ex) {
                startFailed(context, ex);
                throw ex;
            } catch (RuntimeException ex) {
                startFailed(context, ex);
                throw ex;
            }
        }

        @Override
        public void stop(final StopContext context) {
            delegate.stop(context);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void startCompleted(final StartContext context) {
            for (ServiceTracker<T> listener : new ArrayList<ServiceTracker<T>>(listeners)) {
                listener.synchronousListenerServiceStarted((ServiceController<? extends T>) context.getController());
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void startFailed(StartContext context, Throwable th) {
            for (ServiceTracker<T> listener : new ArrayList<ServiceTracker<T>>(listeners)) {
                listener.synchronousListenerServiceFailed((ServiceController<? extends T>) context.getController(), th);
            }
        }

        @Override
        public T getValue() throws IllegalStateException, IllegalArgumentException {
            return delegate.getValue();
        }
    }
}