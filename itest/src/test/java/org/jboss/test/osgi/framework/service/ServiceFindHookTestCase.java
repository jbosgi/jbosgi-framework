/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.test.osgi.framework.service;

// Id: $

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.FindHook;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test {@link FindHook} functionality.
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Mar-2010
 */
public class ServiceFindHookTestCase extends OSGiFrameworkTest {

    @Test
    public void testFindHook() throws Exception {
        final BundleContext context = getFramework().getBundleContext();

        final boolean[] allServices = new boolean[4];
        final boolean[] allGood = new boolean[4];
        final AtomicInteger callIndex = new AtomicInteger();
        FindHook hook = new FindHook() {

            @Override
            @SuppressWarnings("rawtypes")
            public void find(BundleContext context, String name, String filter, boolean all, Collection references) {
                assertNotNull("BundleContext not null", context);
                assertNotNull("Service name not null", name);
                assertNull("Filter null", filter);
                allServices[callIndex.get()] = all;
                assertEquals(2, references.size());
                if (callIndex.get() == 2) {
                    Iterator it = references.iterator();
                    it.next();
                    it.remove();
                }
                allGood[callIndex.get()] = true;
            }
        };

        Runnable service = new Runnable() {

            public void run() {
            }
        };

        ServiceRegistration registration = context.registerService(FindHook.class.getName(), hook, null);
        try {
            ServiceReference sref1 = context.registerService(Runnable.class.getName(), service, null).getReference();
            ServiceReference sref2 = context.registerService(Runnable.class.getName(), service, null).getReference();

            ServiceReference sref = context.getServiceReference(Runnable.class.getName());
            assertNotNull("Reference not null", sref);
            assertFalse("All services false", allServices[callIndex.get()]);
            assertEquals(sref1, sref);
            assertTrue("All good", allGood[callIndex.get()]);

            callIndex.incrementAndGet();
            ServiceReference[] srefs = context.getServiceReferences(Runnable.class.getName(), null);
            assertNotNull("References not null", srefs);
            assertEquals(2, srefs.length);
            assertEquals(sref2, srefs[0]);
            assertEquals(sref1, srefs[1]);
            assertFalse("All services false", allServices[callIndex.get()]);
            assertTrue("All good", allGood[callIndex.get()]);

            callIndex.incrementAndGet();
            srefs = context.getServiceReferences(Runnable.class.getName(), null);
            assertNotNull("References not null", srefs);
            assertEquals(1, srefs.length);
            assertEquals(sref1, srefs[0]);
            assertFalse("All services false", allServices[callIndex.get()]);
            assertTrue("All good", allGood[callIndex.get()]);

            callIndex.incrementAndGet();
            srefs = context.getAllServiceReferences(Runnable.class.getName(), null);
            assertEquals(2, srefs.length);
            assertEquals(sref2, srefs[0]);
            assertEquals(sref1, srefs[1]);
            assertTrue("All services true", allServices[callIndex.get()]);
            assertTrue("All good", allGood[callIndex.get()]);
        } finally {
            registration.unregister();
        }
    }
}
