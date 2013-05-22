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

import static org.jboss.osgi.framework.internal.InternalConstants.NATIVE_LIBRARY_METADATA_KEY;
import static org.jboss.osgi.resolver.ResolverMessages.MESSAGES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.FrameworkModuleLoader;
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.NativeCode;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.XResourceCapability;
import org.jboss.osgi.resolver.XWiring;
import org.jboss.osgi.resolver.spi.AbstractBundleWire;
import org.jboss.osgi.resolver.spi.ResolverHookProcessor;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.ResolveContext;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class FrameworkResolverImpl implements XResolver {

    private final BundleManagerPlugin bundleManager;
    private final NativeCode nativeCode;
    private final ModuleManager moduleManager;
    private final FrameworkModuleLoader moduleLoader;
    private final LockManager lockManager;
    private final XResolver resolver;

    FrameworkResolverImpl(BundleManager bundleManager, NativeCode nativeCode, ModuleManager moduleManager, FrameworkModuleLoader moduleLoader, XResolver resolver, LockManager lockManager) {
        this.bundleManager = (BundleManagerPlugin) bundleManager;
        this.nativeCode = nativeCode;
        this.moduleManager = moduleManager;
        this.moduleLoader = moduleLoader;
        this.lockManager = lockManager;
        this.resolver = resolver;
    }

    @Override
    public XResolveContext createResolveContext(XEnvironment environment, Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) {
        return resolver.createResolveContext(environment, mandatory, optional);
    }

    @Override
    public synchronized Map<Resource, List<Wire>> resolve(ResolveContext resolveContext) throws ResolutionException {
        return resolveInternal((XResolveContext) resolveContext, false);
    }

    @Override
    public synchronized Map<Resource, List<Wire>> resolveAndApply(XResolveContext resolveContext) throws ResolutionException {
        return resolveInternal(resolveContext, true);
    }

    private Map<Resource, List<Wire>> resolveInternal(XResolveContext resolveContext, boolean applyResults) throws ResolutionException {

        // Resolver Hooks also must not be allowed to start another resolve operation, for example by starting a bundle or resolving bundles.
        // The framework must detect this and throw an Illegal State Exception.
        if (ResolverHookProcessor.getCurrentProcessor() != null)
            throw MESSAGES.illegalStateResolverHookCannotTriggerResolveOperation();

        XEnvironment env = resolveContext.getEnvironment();
        BundleContext syscontext = bundleManager.getSystemContext();
        Collection<Resource> manres = new HashSet<Resource>(resolveContext.getMandatoryResources());
        Collection<Resource> optres = new HashSet<Resource>(resolveContext.getOptionalResources());
        ResolverHookProcessor hookregs = new ResolverHookProcessor(syscontext, bundleManager.getBundles(Bundle.INSTALLED));
        try {
            // Recreate the {@link ResolveContext} with filtered resources
            if (hookregs.hasResolverHooks()) {
                hookregs.begin(manres, optres);
                hookregs.filterResolvable();
                hookregs.filterSingletonCollisions(new ResolverHookProcessor.SingletonLocator() {
                    @Override
                    public Collection<BundleCapability> findCollisionCandidates(BundleCapability viewpoint) {
                        Collection<BundleCapability> result = new HashSet<BundleCapability>();
                        if (viewpoint instanceof XResourceCapability) {
                            String symbolicName = ((XResourceCapability) viewpoint).getName();
                            for (XBundle bundle : bundleManager.getBundles(symbolicName, null)) {
                                XBundleRevision xres = bundle.getBundleRevision();
                                List<BundleCapability> bcaps = xres.getDeclaredCapabilities(viewpoint.getNamespace());
                                if (bcaps.size() == 1) {
                                    BundleCapability bcap = bcaps.get(0);
                                    String spec = bcap.getDirectives().get(Constants.SINGLETON_DIRECTIVE);
                                    if (bcap != viewpoint && Boolean.parseBoolean(spec)) {
                                        result.add(bcap);
                                    }
                                }
                            }
                        }
                        return result;
                    }
                });
                resolveContext = resolver.createResolveContext(env, getFilteredResources(hookregs, manres), getFilteredResources(hookregs, optres));
            } else {
                filterSingletons(manres, optres);
                resolveContext = resolver.createResolveContext(env, manres, optres);
            }

            Map<Resource, List<Wire>> wiremap;

            LockContext lockContext = null;
            try {
                FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
                lockContext = lockManager.lockItems(Method.RESOLVE, wireLock);
                wiremap = resolver.resolve(resolveContext);
                if (applyResults) {
                    applyResolverResults(env, wiremap);
                }
            } finally {
                lockManager.unlockItems(lockContext);
            }

            // Send the {@link BundleEvent.RESOLVED} event outside the lock
            if (applyResults) {
                sendBundleResolvedEvents(wiremap);
            }

            return wiremap;
        } finally {
            hookregs.end();
        }
    }

    private Collection<? extends Resource> getFilteredResources(ResolverHookProcessor hookregs, Collection<? extends Resource> resources) {
        Collection<Resource> filtered = null;
        if (resources != null) {
            filtered = new ArrayList<Resource>(resources);
            Iterator<Resource> iterator = filtered.iterator();
            while (iterator.hasNext()) {
                Resource res = iterator.next();
                if (!hookregs.hasResource(res)) {
                    iterator.remove();
                }
            }
        }
        return filtered;
    }

    private HashSet<Resource> getCombinedResources(Collection<? extends Resource> manres, Collection<Resource> optres) {
        HashSet<Resource> combined = new HashSet<Resource>(manres);
        combined.addAll(optres);
        return combined;
    }

    private void filterSingletons(Collection<? extends Resource> manres, Collection<Resource> optres) {
        Map<String, Resource> singletons = new HashMap<String, Resource>();
        for (Resource res : getCombinedResources(manres, optres)) {
            XResource xres = (XResource) res;
            XIdentityCapability icap = xres.getIdentityCapability();
            if (icap.isSingleton()) {
                if (singletons.get(icap.getName()) != null) {
                    manres.remove(res);
                    optres.remove(res);
                } else {
                    singletons.put(icap.getName(), xres);
                }
            }
        }
    }

    private Map<Resource, Wiring> applyResolverResults(XEnvironment environment, Map<Resource, List<Wire>> wiremap) throws ResolutionException {

        // [TODO] Revisit how we apply the resolution results
        // An exception in one of the steps may leave the framework partially modified

        // Transform the wiremap to {@link BundleRevision} and {@link BundleWire}
        Map<BundleRevision, List<BundleWire>> brevmap = new LinkedHashMap<BundleRevision, List<BundleWire>>();
        for (Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            List<BundleWire> bwires = new ArrayList<BundleWire>();
            List<Wire> wires = new ArrayList<Wire>();
            for (Wire wire : entry.getValue()) {
                AbstractBundleWire bwire = new AbstractBundleWire(wire);
                bwires.add(bwire);
                wires.add(bwire);
            }
            Resource res = entry.getKey();
            brevmap.put((BundleRevision) res, bwires);
            wiremap.put(res, wires);
        }

        // Attach the fragments to host
        attachFragmentsToHost(brevmap);

        // Resolve native code libraries if there are any
        try {
            resolveNativeCodeLibraries(brevmap);
        } catch (BundleException ex) {
            throw new ResolutionException(ex);
        }

        // For every resolved host bundle create the {@link ModuleSpec}
        addModules(brevmap);

        // For every resolved host bundle create a {@link Module} service
        createModuleServices(brevmap);

        // Construct and apply the resource wiring map
        Map<Resource, Wiring> wirings = environment.updateWiring(wiremap);
        for (Entry<Resource, Wiring> entry : wirings.entrySet()) {
            XBundleRevision res = (XBundleRevision) entry.getKey();
            res.getWiringSupport().setWiring((XWiring) entry.getValue());
        }

        // Change the bundle state to RESOLVED
        setBundleStatesToResolved(brevmap);

        return wirings;
    }

    private void attachFragmentsToHost(Map<BundleRevision, List<BundleWire>> wiremap) {
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            XBundleRevision brev = (XBundleRevision) entry.getKey();
            if (brev.isFragment()) {
                FragmentBundleRevision fragRev = (FragmentBundleRevision) brev;
                for (BundleWire wire : entry.getValue()) {
                    BundleCapability cap = wire.getCapability();
                    if (HostNamespace.HOST_NAMESPACE.equals(cap.getNamespace())) {
                        HostBundleRevision hostRev = (HostBundleRevision) cap.getResource();
                        fragRev.attachToHost(hostRev);
                    }
                }
            }
        }
    }

    private void resolveNativeCodeLibraries(Map<BundleRevision, List<BundleWire>> wiremap) throws BundleException {
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            XBundleRevision brev = (XBundleRevision) entry.getKey();
            if (brev instanceof UserBundleRevision) {
                UserBundleRevision userRev = (UserBundleRevision) brev;
                Deployment deployment = userRev.getDeployment();

                // Resolve the native code libraries, if there are any
                NativeLibraryMetaData libMetaData = deployment.getAttachment(NATIVE_LIBRARY_METADATA_KEY);
                if (libMetaData != null) {
                    nativeCode.resolveNativeCode(userRev);
                }
            }
        }
    }

    private void addModules(Map<BundleRevision, List<BundleWire>> wiremap) {
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            XBundleRevision brev = (XBundleRevision) entry.getKey();
            if (brev.isFragment() == false) {
                List<BundleWire> wires = wiremap.get(brev);
                moduleManager.addModule(brev, wires);
            }
        }
    }

    private void createModuleServices(Map<BundleRevision, List<BundleWire>> wiremap) {
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            XBundleRevision brev = (XBundleRevision) entry.getKey();
            List<BundleWire> wires = entry.getValue();
            XBundle bundle = brev.getBundle();
            if (bundle != null && bundle.getBundleId() != 0 && !brev.isFragment()) {
                moduleLoader.createModuleService(brev, wires);
            }
        }
    }

    private void setBundleStatesToResolved(Map<BundleRevision, List<BundleWire>> wiremap) {
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            Bundle bundle = entry.getKey().getBundle();
            if (bundle instanceof AbstractBundleState) {
                AbstractBundleState<?> bundleState = (AbstractBundleState<?>) bundle;
                bundleState.changeState(Bundle.RESOLVED, 0);
            }
        }
    }

    private void sendBundleResolvedEvents(Map<Resource, List<Wire>> wiremap) {
        for (Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            Resource res = entry.getKey();
            if (res instanceof XBundleRevision) {
                XBundleRevision brev = (XBundleRevision) res;
                Bundle bundle = brev.getBundle();
                if (bundle instanceof AbstractBundleState) {
                    AbstractBundleState<?> bundleState = (AbstractBundleState<?>) bundle;
                    if (bundleManager.isFrameworkCreated()) {
                        bundleState.fireBundleEvent(BundleEvent.RESOLVED);
                    }
                }
            }
        }
    }
}
