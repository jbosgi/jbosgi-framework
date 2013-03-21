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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.resolver.XBundleRevision;

/**
 * Service associated with an {@link XBundleRevision}
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Mar-2013
 */
abstract class AbstractBundleRevisionService<T extends XBundleRevision> implements Service<T> {

    private final FrameworkState frameworkState;

    AbstractBundleRevisionService(FrameworkState frameworkState) {
        this.frameworkState = frameworkState;
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    BundleManagerPlugin getBundleManager() {
        return frameworkState.getBundleManager();
    }

    @Override
    public void start(StartContext context) throws StartException {
        ServiceController<?> controller = context.getController();
        LOGGER.tracef("Starting: %s in mode %s", controller.getName(), controller.getMode());
    }

    @Override
    public void stop(StopContext context) {
        ServiceController<?> controller = context.getController();
        LOGGER.tracef("Stopping: %s in mode %s", controller.getName(), controller.getMode());
    }

    @Override
    public T getValue() {
        return getBundleRevision();
    }

    abstract T getBundleRevision();
}