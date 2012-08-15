package org.jboss.osgi.framework.internal;
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
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.ServiceController.Mode;

/**
 * The plugin for framework locks.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Aug-2012
 */
final class LockManagerPlugin extends AbstractPluginService<LockManagerPlugin> {

    private final ReentrantLock frameworkLock = new ReentrantLock();

    static void addService(ServiceTarget serviceTarget) {
        LockManagerPlugin service = new LockManagerPlugin();
        ServiceBuilder<LockManagerPlugin> builder = serviceTarget.addService(InternalServices.LOCK_MANAGER_PLUGIN, service);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private LockManagerPlugin() {
    }

    @Override
    public LockManagerPlugin getValue() throws IllegalStateException {
        return this;
    }

    void aquireFrameworkLock() throws TimeoutException {
        aquireFrameworkLock(30, TimeUnit.SECONDS);
    }

    void aquireFrameworkLock(long timeout, TimeUnit unit) throws TimeoutException {
        try {
            LOGGER.tracef("Aquire framework lock");
            if (!frameworkLock.tryLock(timeout, unit)) {
                throw MESSAGES.cannotAquireFrameworkLock();
            }
        } catch (InterruptedException ex) {
            throw MESSAGES.cannotAquireFrameworkLock();
        }
    }

    void releaseFrameworkLock() {
        LOGGER.tracef("Release framework lock");
        frameworkLock.unlock();
    }
}
