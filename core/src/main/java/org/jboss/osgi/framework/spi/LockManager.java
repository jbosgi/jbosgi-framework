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

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.osgi.framework.internal.LockManagerImpl;


/**
 * A manager for framework wide locks.
 *
 * @author thomas.diesler@jboss.com
 * @since 22-Nov-2012
 */
public interface LockManager {

    enum Method {
        INSTALL, START, STOP, RESOLVE, REFRESH, UPDATE, UNINSTALL
    }

    /**
     * A lockable item that supports a {@link ReentrantLock}
     */
    interface LockableItem {
        LockSupport getLockSupport();
    }

    interface LockSupport {
    }

    interface LockContext {
        Method getMethod();
        List<LockableItem> getItems();
    }

    <T extends LockableItem> T getItemForType(Class<T> type);

    LockContext getCurrentLockContext();

    LockContext lockItems(Method method, LockableItem... items);

    LockContext lockItems(Method method, long timeout, TimeUnit unit, LockableItem... items);

    void unlockItems(LockContext context);

    class Factory {
        public static LockSupport addLockSupport(LockableItem item) {
            return new LockManagerImpl.LockSupportImpl(item);
        }
    }
}
