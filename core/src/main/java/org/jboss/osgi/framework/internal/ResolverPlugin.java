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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.IntegrationService;
import org.jboss.osgi.framework.spi.ModuleLoaderPlugin;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.felix.StatelessResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
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
final class ResolverPlugin extends AbstractService<ResolverPlugin> implements XResolver {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<ModuleLoaderPlugin> injectedModuleLoader = new InjectedValue<ModuleLoaderPlugin>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<LockManagerPlugin> injectedLockManager = new InjectedValue<LockManagerPlugin>();
    private XResolver resolver;

    static void addService(ServiceTarget serviceTarget) {
        ResolverPlugin service = new ResolverPlugin();
        ServiceBuilder<ResolverPlugin> builder = serviceTarget.addService(Services.RESOLVER, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, service.injectedEnvironment);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, service.injectedNativeCode);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, service.injectedModuleManager);
        builder.addDependency(IntegrationService.MODULE_LOADER_PLUGIN, ModuleLoaderPlugin.class, service.injectedModuleLoader);
        builder.addDependency(InternalServices.LOCK_MANAGER_PLUGIN, LockManagerPlugin.class, service.injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private ResolverPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        resolver = new StatelessResolver();
    }

    @Override
    public void stop(StopContext context) {
        resolver = null;
    }

    @Override
    public ResolverPlugin getValue() {
        return this;
    }

    @Override
    public XResolveContext createResolveContext(XEnvironment environment, Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) {
        XEnvironment env = injectedEnvironment.getValue();
        Collection<Resource> manres = filterSingletons(mandatory);
        Collection<Resource> optres = new HashSet<Resource>(optional != null ? optional : Collections.<Resource> emptySet());
        appendOptionalFragments(mandatory, optres);
        appendOptionalHostBundles(mandatory, optres);
        return resolver.createResolveContext(env, manres, optres);
    }

    @Override
    public Map<Resource, List<Wire>> resolve(ResolveContext context) throws ResolutionException {
        aquireFrameworkLock();
        try {
            return resolver.resolve(context);
        } finally {
            releaseFrameworkLock();
        }
    }

    @Override
    public Map<Resource, Wiring> resolveAndApply(XResolveContext context) throws ResolutionException {

        Map<Resource, List<Wire>> wiremap;
        Map<Resource, Wiring> wirings;

        aquireFrameworkLock();
        try {
            wiremap = resolver.resolve(context);
            wirings = applyResolverResults(wiremap);
        } finally {
            releaseFrameworkLock();
        }

        // Send the {@link BundleEvent.RESOLVED} event outside the lock
        sendBundleResolvedEvents(wiremap);

        return wirings;
    }

    Map<Resource, Wiring> resolveAndApply(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) throws ResolutionException {
        XEnvironment env = injectedEnvironment.getValue();
        XResolveContext context = createResolveContext(env, mandatory, optional);
        return resolveAndApply(context);
    }

    private void appendOptionalFragments(Collection<? extends Resource> mandatory, Collection<Resource> optional) {
        Collection<Capability> hostcaps = getHostCapabilities(mandatory);
        if (hostcaps.isEmpty() == false) {
            optional.addAll(findAttachableFragments(hostcaps));
        }
    }

    // Append the set of all unresolved resources if there is at least one optional package requirement
    private void appendOptionalHostBundles(Collection<? extends Resource> mandatory, Collection<Resource> optional) {
        for (Resource res : mandatory) {
            for (Requirement req : res.getRequirements(PackageNamespace.PACKAGE_NAMESPACE)) {
                XPackageRequirement preq = (XPackageRequirement) req;
                if (preq.isOptional()) {
                    BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
                    for (XBundle bundle : bundleManager.getBundles(Bundle.INSTALLED)) {
                        XResource auxrev = bundle.getBundleRevision();
                        if (!bundle.isFragment() && !mandatory.contains(auxrev)) {
                            optional.add(auxrev);
                        }
                    }
                    return;
                }
            }
        }
    }

    private Collection<Capability> getHostCapabilities(Collection<? extends Resource> resources) {
        Collection<Capability> result = new HashSet<Capability>();
        for (Resource res : resources) {
            List<Capability> caps = res.getCapabilities(HostNamespace.HOST_NAMESPACE);
            if (caps.size() == 1)
                result.add(caps.get(0));
        }
        return result;
    }

    private Collection<Resource> filterSingletons(Collection<? extends Resource> resources) {
        Map<String, Resource> singletons = new HashMap<String, Resource>();
        List<Resource> result = new ArrayList<Resource>(resources);
        Iterator<Resource> iterator = result.iterator();
        while (iterator.hasNext()) {
            XResource xres = (XResource) iterator.next();
            XIdentityCapability icap = xres.getIdentityCapability();
            if (icap.isSingleton()) {
                if (singletons.get(icap.getSymbolicName()) != null) {
                    iterator.remove();
                } else {
                    singletons.put(icap.getSymbolicName(), xres);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private Collection<XResource> findAttachableFragments(Collection<? extends Capability> hostcaps) {
        Set<XResource> result = new HashSet<XResource>();
        XEnvironment env = injectedEnvironment.getValue();
        for (XResource res : env.getResources(IdentityNamespace.TYPE_FRAGMENT)) {
            Requirement req = res.getRequirements(HostNamespace.HOST_NAMESPACE).get(0);
            XRequirement xreq = (XRequirement) req;
            for (Capability cap : hostcaps) {
                if (xreq.matches(cap)) {
                    result.add(res);
                }
            }
        }
        if (result.isEmpty() == false) {
            LOGGER.debugf("Adding attachable fragments: %s", result);
        }
        return result;
    }

    private Map<Resource, Wiring> applyResolverResults(Map<Resource, List<Wire>> wiremap) throws ResolutionException {

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
        XEnvironment env = injectedEnvironment.getValue();
        Map<Resource, Wiring> wirings = env.updateWiring(wiremap);
        for (Entry<Resource, Wiring> entry : wirings.entrySet()) {
            XBundleRevision res = (XBundleRevision) entry.getKey();
            res.addAttachment(Wiring.class, entry.getValue());
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
                NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
                if (libMetaData != null) {
                    NativeCodePlugin nativeCodePlugin = injectedNativeCode.getValue();
                    nativeCodePlugin.resolveNativeCode(userRev);
                }
            }
        }
    }

    private void addModules(Map<BundleRevision, List<BundleWire>> wiremap) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            XBundleRevision brev = (XBundleRevision) entry.getKey();
            if (brev.isFragment() == false) {
                List<BundleWire> wires = wiremap.get(brev);
                ModuleIdentifier identifier = moduleManager.addModule(brev, wires);
                brev.addAttachment(ModuleIdentifier.class, identifier);
            }
        }
    }

    private void createModuleServices(Map<BundleRevision, List<BundleWire>> wiremap) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        ModuleLoaderPlugin moduleLoader = injectedModuleLoader.getValue();
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            XBundleRevision brev = (XBundleRevision) entry.getKey();
            XBundle bundle = brev.getBundle();
            if (bundle != null && bundle.getBundleId() != 0 && !brev.isFragment()) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(brev);
                moduleLoader.createModuleService(brev, identifier);
            }
        }
    }

    private void setBundleStatesToResolved(Map<BundleRevision, List<BundleWire>> wiremap) {
        for (Map.Entry<BundleRevision, List<BundleWire>> entry : wiremap.entrySet()) {
            Bundle bundle = entry.getKey().getBundle();
            if (bundle instanceof AbstractBundleState) {
                AbstractBundleState bundleState = (AbstractBundleState)bundle;
				bundleState.changeState(Bundle.RESOLVED, 0);
            }
        }
    }

    private void sendBundleResolvedEvents(Map<Resource, List<Wire>> wiremap) {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        for (Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XBundleRevision brev = (XBundleRevision) entry.getKey();
            Bundle bundle = brev.getBundle();
            if (bundle instanceof AbstractBundleState) {
                AbstractBundleState bundleState = (AbstractBundleState)bundle;
                if (bundleManager.isFrameworkCreated()) {
                    bundleState.fireBundleEvent(BundleEvent.RESOLVED);
                }
                // Activate the service that represents bundle state RESOLVED
                ServiceName serviceName = bundleState.getServiceName(Bundle.RESOLVED);
                bundleManager.setServiceMode(serviceName, Mode.ACTIVE);
            }
        }
    }

    private void aquireFrameworkLock() throws ResolutionException {
        try {
            LockManagerPlugin lockManager = injectedLockManager.getValue();
            lockManager.aquireFrameworkLock();
        } catch (TimeoutException ex) {
            throw new ResolutionException(ex);
        }
    }

    private void releaseFrameworkLock() {
        LockManagerPlugin lockManager = injectedLockManager.getValue();
        lockManager.releaseFrameworkLock();
    }
}
