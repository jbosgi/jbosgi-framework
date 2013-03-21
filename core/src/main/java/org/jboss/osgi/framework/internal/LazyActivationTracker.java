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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;

import java.util.Stack;

/**
 * A {@link ThreadLocal} of bundles that need to get started caused by lazy activation.
 *
 * @author thomas.diesler@jboss.com
 * @since 02-May-2011
 */
final class LazyActivationTracker {

    private final static ThreadLocal<Stack<UserBundleState>> stackAssociation = new ThreadLocal<Stack<UserBundleState>>();
    private final static ThreadLocal<UserBundleState> initiatorAssociation = new ThreadLocal<UserBundleState>();

    static void startTracking(UserBundleState userBundle, String className) {
        LOGGER.tracef("startTracking %s from: %s", className, userBundle);
        initiatorAssociation.set(userBundle);
    }

    static void processLoadedClass(Class<?> loadedClass) {
        assert initiatorAssociation.get() != null : "No activation initiator";
        LOGGER.tracef("processLoadedClass: %s", loadedClass.getName());
        processActivationStack();
    }

    static void preDefineClass(UserBundleState userBundle, String className) {
        LOGGER.tracef("preDefineClass %s from: %s", className, userBundle);
        addDefinedClass(userBundle, className);
    }

    static void postDefineClass(UserBundleState userBundle, Class<?> definedClass) {
        LOGGER.tracef("postDefineClass %s from: %s", definedClass.getName(), userBundle);
        if (initiatorAssociation.get() == null) {
            processActivationStack();
        }
    }

    static void stopTracking(UserBundleState userBundle, String className) {
        LOGGER.tracef("stopTracking %s from: %s", className, userBundle);
        initiatorAssociation.remove();
        stackAssociation.remove();
    }

    private static void addDefinedClass(UserBundleState userBundle, String className) {
        if (userBundle.awaitLazyActivation() && userBundle.isAlreadyStarting() == false) {
            Stack<UserBundleState> stack = stackAssociation.get();
            if (stack == null) {
                stack = new Stack<UserBundleState>();
                stackAssociation.set(stack);
            }
            if (stack.contains(userBundle) == false) {
                LOGGER.tracef("addDefinedClass %s from: %s", className, userBundle);
                stack.push(userBundle);
            }
        }
    }

    private static void processActivationStack() {
        Stack<UserBundleState> stack = stackAssociation.get();
        if (stack != null) {
            LOGGER.tracef("processActivationStack: %s", stack);
            while (stack.isEmpty() == false) {
                UserBundleState userBundle = stack.pop();
                if (userBundle.awaitLazyActivation()) {
                    try {
                        userBundle.activateLazily();
                    } catch (Throwable th) {
                        LOGGER.errorCannotActivateBundleLazily(th, userBundle);
                    }
                }
            }
        }
    }
}
