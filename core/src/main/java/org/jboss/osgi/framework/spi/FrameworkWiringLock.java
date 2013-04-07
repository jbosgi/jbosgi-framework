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
package org.jboss.osgi.framework.spi;

import java.util.concurrent.locks.ReentrantLock;


/**
 * A lock for the framework wiring.
 *
 * There is a gurantee that the framework wiring is not changed
 * by another thread while the owner thread holds this lock.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Nov-2012
 */
public final class FrameworkWiringLock implements LockManager.LockableItem {

    private final ReentrantLock wiringLock = new ReentrantLock();

    @Override
    public ReentrantLock getReentrantLock() {
        return wiringLock;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
