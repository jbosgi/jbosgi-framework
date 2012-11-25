package org.jboss.test.osgi.framework;

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.Assert;

import org.jboss.osgi.framework.spi.LockException;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.LockSupport;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test locks
 *
 * @author thomas.diesler@jboss.com
 * @since 22-Nov-2012
 */
public class LockManagerTestCase {

    int noitems = 5;
    TestItem[] items = new TestItem[noitems];

    int notasks = 3;
    Task[] tasks = new Task[notasks];

    LockManager lockManager;
    ExecutorService executor;

    @Before
    public void setUp() {
        lockManager = LockManager.Factory.create();
        executor = Executors.newFixedThreadPool(notasks);
        for (int i = 0; i < noitems; i++) {
            items[i] = new TestItem("item" + i);
        }
        tasks[0] = new Task(Method.START, items[0], items[1], items[2]);
        tasks[1] = new Task(Method.START, items[2], items[3], items[4]);
        tasks[2] = new Task(Method.STOP, items[3], items[4]);
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void testLockManagerConcurrency() throws Exception {
        for (final Task task : tasks) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    LockContext lockContext = null;
                    try {
                        lockContext = lockManager.lockItems(task.method, task.items);
                        for (TestItem item : task.items) {
                            if (task.method == Method.START) {
                                item.start();
                            } else if (task.method == Method.STOP) {
                                item.stop();
                            }
                        }
                    } finally {
                        lockManager.unlockItems(lockContext);
                    }
                }
            };
            executor.execute(runner);
        }
    }

    @Test
    public void testLockManagerTimeout() throws Exception {

        final Thread[] threadHolder = new Thread[1];
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable taskA = new Runnable() {
            @Override
            public void run() {
                threadHolder[0] = Thread.currentThread();
                LockContext lockContext = null;
                try { 
                    lockContext = lockManager.lockItems(Method.START, items[0]);
                    latch.countDown();
                    // Do some heavy crunching
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                } finally {
                    lockManager.unlockItems(lockContext);
                }
            }
        };
        executor.execute(taskA);
        
        // Wait for taskA to start
        Assert.assertTrue("TaskA started", latch.await(500, TimeUnit.MILLISECONDS));
        
        try {
            // Try to obtain a lock on the same item with a short timeout
            lockManager.lockItems(Method.STOP, 500, TimeUnit.MILLISECONDS, items[0]);
            Assert.fail("LockException expected");
        } catch (LockException ex) {
            Throwable cause = ex.getCause();
            Assert.assertSame(TimeoutException.class, cause.getClass());
        }
        
        // Interrupt the heavy crunching
        threadHolder[0].interrupt();
    }

    static class Task {
        final TestItem[] items;
        final Method method;

        Task(Method method, TestItem... items) {
            this.items = items;
            this.method = method;
        }
    }

    class TestItem implements LockableItem {
        final LockSupport itemLock = LockManager.Factory.addLockSupport(this);
        final String name;

        TestItem(String name) {
            this.name = name;
        }

        void start() {
            LockContext lockContext = null;
            try {
                lockContext = lockManager.lockItems(Method.START, this);
                // do stuff
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // ignore
            } finally {
                lockManager.unlockItems(lockContext);
            }
        }

        void stop() {
            LockContext lockContext = null;
            try {
                lockContext = lockManager.lockItems(Method.START, this);
                // do stuff
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // ignore
            } finally {
                lockManager.unlockItems(lockContext);
            }
        }

        @Override
        public LockManager.LockSupport getLockSupport() {
            return itemLock;
        }

        public String toString() {
            return "[" + name + "]";
        }
    }
}