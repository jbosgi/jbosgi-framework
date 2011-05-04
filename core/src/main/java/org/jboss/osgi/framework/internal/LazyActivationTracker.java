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
package org.jboss.osgi.framework.internal;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jboss.logging.Logger;
import org.osgi.framework.BundleException;

/**
 * A {@link ThreadLocal} of bundles that need to get started caused by lazy activation.
 * 
 * @author thomas.diesler@jboss.com
 * @since 02-May-2011
 */
final class LazyActivationTracker {

    // Provide logging
    static final Logger log = Logger.getLogger(LazyActivationTracker.class);
    
    private final static ThreadLocal<Deque<HostBundleState>> stackAssociation = new ThreadLocal<Deque<HostBundleState>>();
    private final static ThreadLocal<HostBundleState> initiatorAssociation = new ThreadLocal<HostBundleState>();

    static void startTracking(HostBundleState hostBundle, String className) {
        initiatorAssociation.set(hostBundle);
    }

    static void processLoadedClass(Class<?> loadedClass) {
        if (initiatorAssociation.get() == null)
            throw new IllegalStateException("No activation initiator");

        processActivationStack();
    }

    static void processDefinedClass(HostBundleState hostBundle, Class<?> definedClass) {
        addDefinedClass(hostBundle, definedClass);
        if (initiatorAssociation.get() == null) {
            processActivationStack();
        }
    }

    static void stopTracking() {
        initiatorAssociation.remove();
    }

    private static void addDefinedClass(HostBundleState hostBundle, Class<?> definedClass) {
        Deque<HostBundleState> stack = stackAssociation.get();
        if (stack == null) {
            stack = new ArrayDeque<HostBundleState>();
            stackAssociation.set(stack);
        }
        stack.add(hostBundle);
    }

    private static void processActivationStack() {
        Deque<HostBundleState> stack = stackAssociation.get();
        if (stack != null) {
            try {
                while (stack.isEmpty() == false) {
                    HostBundleState hostBundle = stack.pop();
                    if (hostBundle.awaitLazyActivation()) {
                        try {
                            hostBundle.activateLazily();
                        } catch (BundleException ex) {
                            log.errorf(ex, "Cannot activate lazily: %s", hostBundle);
                        }
                    }
                }
            } finally {
                stack.clear();
            }
        }
    }
}
