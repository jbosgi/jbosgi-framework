/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.felix.FelixResolver;
import org.jboss.osgi.resolver.spi.AbstractResolveContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.resolver.ResolutionException;
import org.osgi.service.resolver.Resolver;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class DefaultResolverPlugin extends AbstractPluginService<ResolverPlugin> implements ResolverPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(DefaultResolverPlugin.class);

    private final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private Resolver resolver;

    static void addService(ServiceTarget serviceTarget) {
        DefaultResolverPlugin service = new DefaultResolverPlugin();
        ServiceBuilder<ResolverPlugin> builder = serviceTarget.addService(InternalServices.RESOLVER_PLUGIN, service);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, service.injectedEnvironment);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, service.injectedNativeCode);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, service.injectedModuleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultResolverPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        resolver = new FelixResolver();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        resolver = null;
    }

    @Override
    public ResolverPlugin getValue() {
        return this;
    }

    @Override
    public Map<Resource, List<Wire>> resolve(final Collection<? extends Resource> mandatory, final Collection<? extends Resource> optional) throws ResolutionException {
        XResolveContext context = new AbstractResolveContext(injectedEnvironment.getValue()) {

            @Override
            public Collection<Resource> getMandatoryResources() {
                return filterSingletons(mandatory);
            }

            @Override
            public Collection<Resource> getOptionalResources() {
                return appendOptionalFragments(mandatory, optional);
            }
        };
        return resolver.resolve(context);
    }

    @Override
    public synchronized void resolveAndApply(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) throws ResolutionException {
        Map<Resource, List<Wire>> wiremap = resolve(mandatory, optional);
        for (Entry<Resource, Wiring> entry : applyResolverResults(wiremap).entrySet()) {
            XResource res = (XResource) entry.getKey();
            res.addAttachment(Wiring.class, entry.getValue());
        }
    }

    private Collection<Resource> appendOptionalFragments(Collection<? extends Resource> mandatory, Collection<? extends Resource> optional) {
        Collection<Capability> hostcaps = getHostCapabilities(mandatory);
        Collection<Resource> result = new HashSet<Resource>();
        if (hostcaps.isEmpty() == false) {
            result.addAll(optional != null ? optional : Collections.EMPTY_SET);
            result.addAll(findAttachableFragments(hostcaps));
        }
        return result;
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

    private Collection<? extends Resource> findAttachableFragments(Collection<? extends Capability> hostcaps) {
        Set<Resource> result = new HashSet<Resource>();
        XEnvironment env = injectedEnvironment.getValue();
        for (Resource res : env.getResources(Collections.singleton(IdentityNamespace.TYPE_FRAGMENT))) {
            Requirement req = res.getRequirements(HostNamespace.HOST_NAMESPACE).get(0);
            for (Capability cap : hostcaps) {
                if (env.matches((XRequirement)req, (XCapability)cap)) {
                    result.add(res);
                }
            }
        }
        log.debugf("attachable fragments: %s", result);
        return result;
    }

    private Map<Resource, Wiring> applyResolverResults(Map<Resource, List<Wire>> wiremap) throws ResolutionException {

        // [TODO] Revisit how we apply the resolution results
        // An exception in one of the steps may leave the framework partially modified

        // Attach the fragments to host
        attachFragmentsToHost(wiremap);

        try {

            // Resolve native code libraries if there are any
            resolveNativeCodeLibraries(wiremap);

        } catch (BundleException ex) {
            throw new ResolutionException(ex);
        }

        // For every resolved host bundle create the {@link ModuleSpec}
        addModules(wiremap);

        // For every resolved host bundle load the module. This creates the {@link ModuleClassLoader}
        loadModules(wiremap);

        // Change the bundle state to RESOLVED
        setBundleToResolved(wiremap);

        // Construct and apply the resource wiring map
        XEnvironment env = injectedEnvironment.getValue();
        return env.updateWiring(wiremap);
    }

    private void attachFragmentsToHost(Map<Resource, List<Wire>> wiremap) {
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res.isFragment()) {
                FragmentBundleRevision fragRev = (FragmentBundleRevision) res;
                for (Wire wire : entry.getValue()) {
                    Capability cap = wire.getCapability();
                    if (HostNamespace.HOST_NAMESPACE.equals(cap.getNamespace())) {
                        HostBundleRevision hostRev = (HostBundleRevision) cap.getResource();
                        fragRev.attachToHost(hostRev);
                    }
                }
            }
        }
    }

    private void resolveNativeCodeLibraries(Map<Resource, List<Wire>> wiremap) throws BundleException {
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res instanceof UserBundleRevision) {
                UserBundleRevision userRev = (UserBundleRevision) res;
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

    private void addModules(Map<Resource, List<Wire>> wiremap) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res.isFragment() == false) {
                List<Wire> wires = wiremap.get(res);
                moduleManager.addModule(res, wires);
            }
        }
    }

    private void loadModules(Map<Resource, List<Wire>> wiremap) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res.isFragment() == false) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(res);
                try {
                    moduleManager.loadModule(identifier);
                } catch (ModuleLoadException ex) {
                    throw new IllegalStateException("Cannot load module: " + identifier, ex);
                }
            }
        }
    }

    private void setBundleToResolved(Map<Resource, List<Wire>> wiremap) {
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            if (entry.getKey() instanceof AbstractBundleRevision) {
                AbstractBundleRevision brev = (AbstractBundleRevision) entry.getKey();
                brev.getBundleState().changeState(Bundle.RESOLVED);
            }
        }
    }
}