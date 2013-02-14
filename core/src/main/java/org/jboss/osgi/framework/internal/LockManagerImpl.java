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
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.LockException;
import org.jboss.osgi.framework.spi.LockManager;



/**
 * The plugin for framework locks.
 *
 * @author thomas.diesler@jboss.com
 * @since 22-Nov-2012
 */
public final class LockManagerImpl implements LockManager {

    private final FrameworkWiringLock wiringLock = new FrameworkWiringLock();
    private final Map<Class<? extends LockableItem>, LockableItem> otherLocks = new HashMap<Class<? extends LockableItem>, LockableItem>();
    private static ThreadLocal<Stack<LockContext>> lockContextAssociation = new ThreadLocal<Stack<LockContext>>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends LockableItem> T getItemForType(Class<T> type) {
        synchronized (otherLocks) {
            T lock;
            if (type == FrameworkWiringLock.class) {
                return (T) wiringLock;
            } else {
                lock = (T) otherLocks.get(type);
                if (lock == null) {
                    try {
                        lock = type.newInstance();
                        otherLocks.put(type, lock);
                    } catch (Exception ex) {
                        throw new LockException(ex);
                    }
                }
            }
            return lock;
        }
    }

    @Override
    public LockContext getCurrentContext() {
        Stack<LockContext> stack = lockContextAssociation.get();
        return stack != null ? stack.peek() : null;
    }

    @Override
    public synchronized LockContext lockItems(Method method, LockableItem... items) {
        return lockItems(method, 30, TimeUnit.SECONDS, items);
    }

    @Override
    public synchronized LockContext lockItems(Method method, long timeout, TimeUnit unit, LockableItem... items) {

        LockContextImpl context = new LockContextImpl(method, items);

        // Try to lock all items
        long start = System.currentTimeMillis();
        while (!context.lockItems()) {

            // Timeout if we have waited long enough
            long now = System.currentTimeMillis();
            if (now >= start + unit.toMillis(timeout))
                throw MESSAGES.cannotObtainLockTimely(new TimeoutException(), context);

            LOGGER.debugf("LockManager lock: %s waiting ...", context);

            try {
                wait(unit.toMillis(timeout));
                LOGGER.debugf("LockManager continue ...");
            } catch (InterruptedException ex) {
                throw MESSAGES.cannotObtainLockTimely(ex, context);
            }
        }

        LOGGER.debugf("LockManager locked: %s", context);

        // Push the current lock context to the stack
        Stack<LockContext> contextStack = lockContextAssociation.get();
        if (contextStack == null) {
            contextStack = new Stack<LockContext>();
            lockContextAssociation.set(contextStack);
        }
        contextStack.push(context);

        return context;
    }

    @Override
    public synchronized void unlockItems(LockContext context) {

        if (context != null) {

            // Unlock all items
            for (LockableItem item : context.getItems()) {
                LockSupportImpl support = (LockSupportImpl) item.getLockSupport();
                support.unlock();
            }

            LOGGER.debugf("LockManager unlocked: %s", context);

            // Pop the current context stack
            Stack<LockContext> contextStack = lockContextAssociation.get();
            contextStack.pop();
            if (contextStack.isEmpty()) {
                lockContextAssociation.remove();
            }
        }

        // Notify all waiting threads
        notifyAll();
    }

    static class LockContextImpl implements LockContext {

        final List<LockableItem> items;
        final Method method;

        LockContextImpl(Method method, LockableItem... items) {
            this.items = Arrays.asList(items);
            this.method = method;
        }

        @Override
        public List<LockableItem> getItems() {
            return items;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        boolean lockItems() {

            // Try to lock all items
            int index = -1;
            for (LockableItem item : items) {
                LockSupportImpl support = (LockSupportImpl) item.getLockSupport();
                if (!support.tryLock(method)) {
                    break;
                }
                index++;
            }

            // All items locked
            if (index + 1 == items.size()) {
                return true;
            }

            // Unlock the locked items
            for (; index >= 0; index--) {
                LockableItem item = items.get(index);
                LockSupportImpl support = (LockSupportImpl) item.getLockSupport();
                support.unlock();
            }

            return false;
        }

        @Override
        public String toString() {
            return "(" + method + ") " + items;
        }
    }

    public static class LockSupportImpl implements LockSupport {

        private final ReentrantLock lock = new ReentrantLock();

        public LockSupportImpl(LockableItem item) {
        }

        boolean tryLock(Method method) {
            return lock.tryLock();
        }

        void unlock() {
            lock.unlock();
        }
    }
}
