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
package org.jboss.osgi.framework.util;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.ServiceController.Transition;

/**
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 19-Apr-2012
 */
public class ServiceTracker<S> extends AbstractServiceListener<S> {

    private final Set<ServiceName> addedNames = new HashSet<ServiceName>();
    private final Set<ServiceController<? extends S>> trackedController = new HashSet<ServiceController<? extends S>>();

    @Override
    public void listenerAdded(ServiceController<? extends S> controller) {
        synchronized (trackedController) {
            LOGGER.infof("ServiceTracker controller added: %s", controller);
            addedNames.add(controller.getName());
            trackedController.add(controller);
        }
    }

    @Override
    public void transition(ServiceController<? extends S> controller, Transition transition) {
        synchronized (trackedController) {
            switch (transition) {
                case STARTING_to_UP:
                    LOGGER.infof("ServiceTracker transition to UP: " + controller.getName());
                    serviceStarted(controller);
                    serviceComplete(controller);
                    break;
                case STARTING_to_START_FAILED:
                    LOGGER.infof("ServiceTracker transition to START_FAILED: " + controller.getName());
                    StartException ex = controller.getStartException();
                    serviceStartFailed(controller, ex);
                    serviceComplete(controller);
                    break;
            }
        }
    }

    private void serviceComplete(ServiceController<? extends S> controller) {
        trackedController.remove(controller);
        controller.removeListener(this);
        if (trackedController.size() == 0 && allServicesAdded(Collections.unmodifiableSet(addedNames))) {
            LOGGER.infof("ServiceTracker complete: " + getClass().getName());
            allComplete();
        }
    }

    protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
        return true;
    }

    protected void serviceStartFailed(ServiceController<? extends S> controller, StartException ex) {
    }

    protected void serviceStarted(ServiceController<? extends S> controller) {
    }

    protected void allComplete() {
    }
}