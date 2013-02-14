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

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Iterator;

import org.jboss.osgi.framework.internal.WeavingContext.ContextClass;
import org.jboss.osgi.framework.internal.WeavingContext.HookRegistration;
import org.jboss.osgi.framework.spi.BundleReferenceClassLoader;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.hooks.weaving.WeavingException;
import org.osgi.framework.hooks.weaving.WeavingHook;

/**
 * A {@link ClassFileTransformer} that delegates to the registered {@link WeavingHook}.
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Feb-2013
 */
final class WeavingHookProcessor implements ClassFileTransformer {

    private final FrameworkEvents frameworkEvents;

    WeavingHookProcessor(FrameworkEvents frameworkEvents) {
        this.frameworkEvents = frameworkEvents;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        WeavingContext context = WeavingContext.getCurrentContext();
        if (context == null || context.getWeavingHooks().isEmpty()) {
            return classfileBuffer;
        }

        ContextClass wovenClass = context.createContextClass(className, classBeingRedefined, protectionDomain, classfileBuffer);
        for (Iterator<HookRegistration> iterator = context.getWeavingHooks().iterator(); iterator.hasNext();) {
            HookRegistration hookreg = iterator.next();
            WeavingHook hook = hookreg.hook;
            try {
                hook.weave(wovenClass);
            } catch (RuntimeException rte) {
                // This method can throw a WeavingException to deliberately fail the class load
                // without being blacklisted by the framework.
                if (!(rte instanceof WeavingException)) {
                    context.blacklist(hookreg.sref);
                    iterator.remove();
                }
                wovenClass.markComplete();
                BundleReferenceClassLoader<?> bref = (BundleReferenceClassLoader<?>) hook.getClass().getClassLoader();
                frameworkEvents.fireFrameworkEvent(bref.getBundleState(), FrameworkEvent.ERROR, rte, (FrameworkListener[]) null);
                throw rte;
            }
        }

        return wovenClass.getBytes();
    }
}
