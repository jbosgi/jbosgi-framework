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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jboss.osgi.framework.internal.WeavingContext.ContextClass;
import org.jboss.osgi.framework.internal.WeavingContext.HookRegistration;
import org.jboss.osgi.framework.spi.BundleReferenceClassLoader;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XBundleRevisionBuilderFactory;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.XResourceBuilder;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.hooks.weaving.WeavingException;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.resource.Requirement;

/**
 * A {@link ClassFileTransformer} that delegates to the registered {@link WeavingHook}.
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Feb-2013
 */
final class WeavingHookProcessor implements ClassFileTransformer {

    private final FrameworkEvents frameworkEvents;
    private final HostBundleRevision hostRev;

    private List<String> processedImports = new ArrayList<String>();

    WeavingHookProcessor(HostBundleRevision hostRev, FrameworkEvents frameworkEvents) {
        this.frameworkEvents = frameworkEvents;
        this.hostRev = hostRev;
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

        addDynamicWeavingImports(wovenClass);

        return wovenClass.getBytes();
    }

    private void addDynamicWeavingImports(ContextClass wovenClass) {

        // Get the list of unprocessed imports
        List<String> unprocessedImports = new ArrayList<String>();
        for (String importSpec : wovenClass.getDynamicImports()) {
            if (!processedImports.contains(importSpec)) {
                unprocessedImports.add(importSpec);
                processedImports.add(importSpec);
            }
        }

        // Build the dynamic weaving package requirements
        for (String importSpec : unprocessedImports) {
            try {
                OSGiMetaDataBuilder mdbuilder = OSGiMetaDataBuilder.createBuilder(hostRev.getSymbolicName(), hostRev.getVersion());
                mdbuilder.addDynamicImportPackages(importSpec);
                OSGiMetaData metadata = mdbuilder.getOSGiMetaData();

                XResourceBuilder resbuilder = XBundleRevisionBuilderFactory.create();
                XResource res = resbuilder.loadFrom(metadata).getResource();

                // Extract the dynamic package requirements
                for (Requirement req : res.getRequirements(PackageNamespace.PACKAGE_NAMESPACE)) {
                    FallbackLoader fallbackLoader = hostRev.getFallbackLoader();
                    fallbackLoader.addDynamicWeavingImport((XPackageRequirement) req);
                }
            } catch(RuntimeException rte) {
                // The dynamic imports must have a valid syntax,
                // otherwise an Illegal Argument Exception must be thrown.
                throw MESSAGES.illegalArgumentDynamicWeavingImport(rte, importSpec);
            }
        }
    }
}
