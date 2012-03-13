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

import static org.osgi.framework.resource.ResourceConstants.IDENTITY_TYPE_FRAGMENT;
import static org.osgi.framework.resource.ResourceConstants.WIRING_HOST_NAMESPACE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.EnvironmentPlugin;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.metadata.VersionRange;
import org.jboss.osgi.resolver.XHostRequirement;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.spi.AbstractEnvironment;
import org.jboss.osgi.resolver.spi.FrameworkPreferencesComparator;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.resource.Capability;
import org.osgi.framework.resource.Requirement;
import org.osgi.framework.resource.Resource;
import org.osgi.framework.resource.Wire;
import org.osgi.framework.resource.Wiring;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.resolver.ResolutionException;

/**
 * The default delegate plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class DefaultEnvironmentPlugin extends AbstractEnvironment implements Service<EnvironmentPlugin>, EnvironmentPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(DefaultEnvironmentPlugin.class);

    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();

    static boolean USE_NEW_PATH = true;

    static void addService(ServiceTarget serviceTarget) {
        DefaultEnvironmentPlugin service = new DefaultEnvironmentPlugin();
        ServiceBuilder<EnvironmentPlugin> builder = serviceTarget.addService(Services.ENVIRONMENT_PLUGIN, service);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, service.injectedNativeCode);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, service.injectedModuleManager);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultEnvironmentPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public EnvironmentPlugin getValue() {
        return this;
    }

    @Override
    public Collection<? extends Resource> findAttachableFragments(Collection<? extends Capability> hostcaps) {
        Set<Resource> result = new HashSet<Resource>();
        for (Resource res : getResources(IDENTITY_TYPE_FRAGMENT)) {
            Requirement req = res.getRequirements(WIRING_HOST_NAMESPACE).get(0);
            for (Capability cap : hostcaps) {
                if (req.matches(cap)) {
                    result.add(res);
                }
            }
        }
        log.debugf("attachable fragments: %s", result);
        return result;
    }

    @Override
    public Collection<Resource> filterSingletons(Collection<? extends Resource> resources) {
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


    @Override
    public Comparator<Capability> getComparator() {
        final AbstractEnvironment env = this;
        return new FrameworkPreferencesComparator() {
            @Override
            protected Wiring getWiring(Resource res) {
                return env.getWiring(res);
            }

            @Override
            public long getResourceIndex(Resource res) {
                return env.getResourceIndex(res);
            }
        };
    }

    @Override
    public Wiring createWiring(Resource res, List<Wire> wires) {
        AbstractBundleRevision brev = (AbstractBundleRevision) res;
        return new AbstractBundleWiring(brev, wires);
    }

    @Override
    public Wiring applyWiring(Resource res, Wiring wiring) {
        AbstractBundleRevision brev = (AbstractBundleRevision) res;
        brev.setWiring((BundleWiring) wiring);
        return wiring;
    }

    @Override
    public SortedSet<Capability> findProviders(Requirement req) {

        // Possibly reduce the set of provided capabilities
        BundleManager bundleManager = injectedBundleManager.getValue();
        SortedSet<Capability> providers = super.findProviders(req);
        Iterator<Capability> capit = providers.iterator();
        while(capit.hasNext()) {
            Capability cap = capit.next();
            XResource res = (XResource) cap.getResource();
            Wiring wiring = getWiring(res);

            boolean removeCapability = false;
            
            // A fragment can only provide a capability if it is either already attached
            // or if there is one possible hosts that it can attach to
            // i.e. one of the hosts in the range is not resolved already
            if (res instanceof FragmentBundleRevision) {
                FragmentBundleRevision fragrev = (FragmentBundleRevision) res;
                if (fragrev.getAttachedHosts().isEmpty()) {
                    XHostRequirement hostreq = (XHostRequirement) res.getRequirements(WIRING_HOST_NAMESPACE).get(0);
                    String symbolicName = hostreq.getSymbolicName();
                    VersionRange versionRange = hostreq.getVersionRange();
                    boolean unresolvedHost = false;
                    Set<AbstractBundleState> hosts = bundleManager.getBundles(symbolicName, versionRange != null ? versionRange.toString() : null);
                    for (AbstractBundleState host : hosts) {
                        if (host.isResolved() == false) {
                            unresolvedHost = true;
                            break;
                        }
                    }
                    removeCapability = !unresolvedHost;
                }
            }
            /*
            else if (wiring != null) {
            	for (Wire wire : wiring.getRequiredResourceWires(cap.getNamespace())) {
            		Requirement wirereq = wire.getRequirement();
            		if (wirereq.matches(cap)) {
            			removeCapability = true;
            			break;
            		}
            	}
            }
            */
            if (removeCapability) {
            	log.debugf("Remove capability: %s", cap);
        		capit.remove();
            }
        }
        return providers;
    }

    @Override
    public synchronized Map<Resource, Wiring> applyResolverResults(Map<Resource, List<Wire>> wiremap) {

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
        return super.applyResolverResults(wiremap);
    }


    private void attachFragmentsToHost(Map<Resource, List<Wire>> wiremap) {
        for (Map.Entry<Resource, List<Wire>> entry : wiremap.entrySet()) {
            XResource res = (XResource) entry.getKey();
            if (res.isFragment()) {
                FragmentBundleRevision fragRev = (FragmentBundleRevision) res;
                for (Wire wire : entry.getValue()) {
                    Capability cap = wire.getCapability();
                    if (WIRING_HOST_NAMESPACE.equals(cap.getNamespace())) {
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
            AbstractBundleRevision brev = (AbstractBundleRevision) entry.getKey();
            AbstractBundleState bundleState = brev.getBundleState();
            bundleState.changeState(Bundle.RESOLVED);
        }
    }
}