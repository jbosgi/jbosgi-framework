package org.jboss.test.osgi.framework.service;
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

import org.jboss.osgi.spi.ConstantsHelper;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.hooks.service.EventHook;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test {@link EventHook} functionality.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Mar-2010
 */
public class ServiceEventHookTestCase extends OSGiFrameworkTest {

    @Test
    public void testEventHook() throws Exception {
        final BundleContext context = getFramework().getBundleContext();

        final List<String> events = new ArrayList<String>();
        final boolean[] allGood = new boolean[1];
        EventHook hook = new EventHook() {

            @Override
            @SuppressWarnings({ "rawtypes", "unchecked" })
            public void event(ServiceEvent event, Collection contexts) {
                assertNotNull("ServiceEvent not null", event);
                events.add(ConstantsHelper.serviceEvent(event.getType()));
                assertNotNull("Contexts not null", contexts);
                assertEquals(1, contexts.size());
                Iterator it = contexts.iterator();
                assertEquals(context, it.next());
                // Can remove a context
                it.remove();
                try {
                    contexts.add(context);
                    fail("Cannot add a context");
                } catch (UnsupportedOperationException ex) {
                    // expected
                }
                allGood[0] = true;
            }
        };

        Runnable service = new Runnable() {

            public void run() {
            }
        };

        context.registerService(EventHook.class.getName(), hook, null);
        context.registerService(Runnable.class.getName(), service, null);
        assertTrue("Events called", events.size() > 0);
        assertTrue("Events all good", allGood[0]);
    }
}
