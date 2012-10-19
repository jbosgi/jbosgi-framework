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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.osgi.resolver.XBundle;

/**
 * The bundle lock.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
@SuppressWarnings("serial")
public class BundleLock extends ReentrantLock {

    public enum LockMethod {
        RESOLVE, START, STOP, UNINSTALL
    }

    public boolean tryLock(XBundle bundle, BundleLock.LockMethod method) {
        try {
            LOGGER.tracef("Aquire %s lock on: %s", method, bundle);
            if (tryLock(30, TimeUnit.SECONDS)) {
                return true;
            } else {
                LOGGER.errorCannotAquireBundleLock(method.toString(), bundle);
                return false;
            }
        } catch (InterruptedException ex) {
            LOGGER.debugf("Interupted while trying to aquire %s lock on: %s", method, bundle);
            return false;
        }
    }

    public void unlock(XBundle bundle, BundleLock.LockMethod method) {
        LOGGER.tracef("Release %s lock on: %s", method, bundle);
        unlock();
    }
}