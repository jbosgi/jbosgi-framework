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

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.List;

import org.jboss.osgi.framework.internal.WeavingContext.ContextClass;
import org.osgi.framework.hooks.weaving.WeavingException;
import org.osgi.framework.hooks.weaving.WeavingHook;

/**
 * A {@link ClassFileTransformer} that delegates to the registered {@link WeavingHook}.
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Feb-2013
 */
final class WeavingHookProcessor implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        WeavingContext context = WeavingContext.getCurrentWeavingContext();
        List<WeavingHook> weavingHooks = context.getWeavingHooks();
        if (weavingHooks.isEmpty()) {
            return classfileBuffer;
        }

        ContextClass wovenClass = context.createContextClass(className, classBeingRedefined, protectionDomain, classfileBuffer);
        for (Iterator<WeavingHook> iterator = weavingHooks.iterator(); iterator.hasNext();) {
            WeavingHook hook = iterator.next();
            try {
                hook.weave(wovenClass);
            } catch (WeavingException ex) {
                LOGGER.warnErrorWhileCallingWeavingHook(ex, hook);
                // This method can throw a WeavingException to deliberately fail the class load
                // without being blacklisted by the framework.
                throw ex;
            } catch (RuntimeException rte) {
                LOGGER.warnErrorWhileCallingWeavingHook(rte, hook);
                iterator.remove();
                throw rte;
            }
        }

        return wovenClass.getBytes();
    }
}
