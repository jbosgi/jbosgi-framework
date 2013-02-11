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

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.weaving.WeavingException;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.framework.wiring.BundleWiring;

/**
 * A {@link ClassFileTransformer} that delegates to the registered {@link WeavingHook}.
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Feb-2013
 */
final class WeavingHookTransformer implements ClassFileTransformer {

    private final HostBundleState hostState;
    private final BundleManagerPlugin bundleManager;
    private ThreadLocal<List<WeavingHook>> reentrantHooks;

    WeavingHookTransformer(HostBundleState hostState) {
        this.hostState = hostState;
        this.bundleManager = hostState.getBundleManager();
        this.reentrantHooks = new ThreadLocal<List<WeavingHook>>();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        List<WeavingHook> hooks = reentrantHooks.get();
        if (hooks != null) {
            WovenClassImpl wovenClass = new WovenClassImpl(hostState, className, classBeingRedefined, protectionDomain, classfileBuffer);
            return callWeavingHooks(wovenClass, hooks);
        }

        // Find the registered {@link WeavingHook}
        Collection<ServiceReference<WeavingHook>> srefs = null;
        BundleContext syscontext = bundleManager.getSystemContext();
        try {
            srefs = syscontext.getServiceReferences(WeavingHook.class, null);
        } catch (InvalidSyntaxException e) {
            // ignore
        }

        // Weaving Hook services that are lower in ranking will weave any of the changes of higher ranking Weaving Hook services.
        List<ServiceReference<WeavingHook>> sorted = new ArrayList<ServiceReference<WeavingHook>>(srefs);
        Collections.sort(sorted);

        // Get the hook instances and associate them with the current thread
        hooks = new ArrayList<WeavingHook>();
        for (ServiceReference<WeavingHook> sref : sorted) {
            WeavingHook hook = syscontext.getService(sref);
            hooks.add(hook);
        }

        reentrantHooks.set(hooks);
        try {
            WovenClassImpl wovenClass = new WovenClassImpl(hostState, className, classBeingRedefined, protectionDomain, classfileBuffer);
            return callWeavingHooks(wovenClass, hooks);
        } finally {
            reentrantHooks.remove();
        }
    }

    private byte[] callWeavingHooks(WovenClassImpl wovenClass, List<WeavingHook> hooks) {
        for(Iterator<WeavingHook> iterator = hooks.iterator(); iterator.hasNext();) {
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
        wovenClass.setWeavingComplete();
        return wovenClass.getBytes();
    }

    static class WovenClassImpl implements WovenClass {

        private final HostBundleState hostState;
        private final String className;
        private final Class<?> redefinedClass;
        private final ProtectionDomain protectionDomain;
        private List<String> dynamicImports;
        private boolean weavingComplete;
        private byte[] classfileBuffer;

        WovenClassImpl(HostBundleState hostState, String className, Class<?> redefinedClass, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            this.hostState = hostState;
            this.className = className.replace('/', '.');
            this.redefinedClass = redefinedClass;
            this.protectionDomain = protectionDomain;
            this.classfileBuffer = classfileBuffer;
            this.dynamicImports = new ArrayList<String>();
        }

        @Override
        public byte[] getBytes() {
            return classfileBuffer;
        }

        @Override
        public void setBytes(byte[] newBytes) {
            assertWeavingNotComplete();
            this.classfileBuffer = newBytes;
        }

        @Override
        public List<String> getDynamicImports() {
            return dynamicImports;
        }

        @Override
        public boolean isWeavingComplete() {
            return weavingComplete;
        }

        void setWeavingComplete() {
            assertWeavingNotComplete();
            dynamicImports = Collections.unmodifiableList(dynamicImports);
            classfileBuffer = Arrays.copyOf(classfileBuffer, classfileBuffer.length);
            weavingComplete = true;
        }

        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public ProtectionDomain getProtectionDomain() {
            return protectionDomain;
        }

        @Override
        public Class<?> getDefinedClass() {
            return redefinedClass;
        }

        @Override
        public BundleWiring getBundleWiring() {
            return hostState.adapt(BundleWiring.class);
        }

        private void assertWeavingNotComplete() {
            if (weavingComplete)
                throw MESSAGES.illegalStateWeavingAlreadyComplete(className);
        }
    }
}
