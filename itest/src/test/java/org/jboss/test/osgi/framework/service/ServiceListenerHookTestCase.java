/*
 * #%L
 * JBossOSGi Framework iTest
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
package org.jboss.test.osgi.framework.service;

import org.jboss.osgi.spi.ConstantsHelper;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test {@link ListenerHook} functionality.
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Mar-2010
 */
public class ServiceListenerHookTestCase extends OSGiFrameworkTest {

    @Test
    public void testListenerHook() throws Exception {
        final Collection<ListenerInfo> added = new ArrayList<ListenerInfo>();
        final Collection<ListenerInfo> removed = new ArrayList<ListenerInfo>();
        ListenerHook hook = new ListenerHook() {

            @SuppressWarnings({ "rawtypes", "unchecked" })
            public void added(Collection infos) {
                added.addAll(infos);
            }

            @SuppressWarnings({ "rawtypes", "unchecked" })
            public void removed(Collection infos) {
                added.removeAll(infos);
                removed.addAll(infos);
            }
        };

        Runnable service = new Runnable() {

            public void run() {
            }
        };

        final List<String> events = new ArrayList<String>();
        ServiceListener listener = new ServiceListener() {

            public void serviceChanged(ServiceEvent event) {
                int eventType = event.getType();
                events.add(ConstantsHelper.serviceEvent(eventType));
            }
        };

        BundleContext context = getFramework().getBundleContext();
        context.addServiceListener(listener, "(foo=bar)");

        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put("foo", "bar");
        context.registerService(Runnable.class.getName(), service, props);
        assertEquals(1, events.size());
        assertEquals("REGISTERED", events.get(0));

        context.registerService(ListenerHook.class.getName(), hook, null);
        assertTrue("Hook added called", added.size() > 0);
        assertTrue("Hook removed not called", removed.isEmpty());

        int size = added.size();

        // Register the same listener with a different filter
        context.addServiceListener(listener, "(bar=foo)");
        assertTrue("Hook removed called", removed.size() > 0);
        assertEquals("Hook added called", size, added.size());
    }
}
