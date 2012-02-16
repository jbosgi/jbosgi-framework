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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResolverCallback;
import org.jboss.osgi.resolver.XResolverException;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.resolver.XWire;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 06-Jul-2009
 */
final class LegacyResolverPlugin extends AbstractPluginService<LegacyResolverPlugin> {

    // Provide logging
    final Logger log = Logger.getLogger(LegacyResolverPlugin.class);

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<ModuleManagerPlugin> injectedModuleManager = new InjectedValue<ModuleManagerPlugin>();
    private final InjectedValue<NativeCodePlugin> injectedNativeCode = new InjectedValue<NativeCodePlugin>();
    private final XResolverFactory factory;
    private XResolver resolver;


    static void addService(ServiceTarget serviceTarget) {
        LegacyResolverPlugin service = new LegacyResolverPlugin();
        ServiceBuilder<LegacyResolverPlugin> builder = serviceTarget.addService(InternalServices.LEGACY_RESOLVER_PLUGIN, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(InternalServices.MODULE_MANGER_PLUGIN, ModuleManagerPlugin.class, service.injectedModuleManager);
        builder.addDependency(InternalServices.NATIVE_CODE_PLUGIN, NativeCodePlugin.class, service.injectedNativeCode);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private LegacyResolverPlugin() {
        factory = XResolverFactory.getInstance(getClass().getClassLoader());
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        resolver = factory.newResolver();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        resolver = null;
    }

    @Override
    public LegacyResolverPlugin getValue() {
        return this;
    }

    /**
     * Get the resolver instance
     */
    XResolver getResolver() {
        return resolver;
    }

    /**
     * Get a new module builder instance
     */
    XModuleBuilder getModuleBuilder() {
        return factory.newModuleBuilder();
    }

    /**
     * Add a module to the resolver.
     *
     * @param resModule the resolver module
     */
    void addModule(XModule resModule) {
        resolver.addModule(resModule);
    }

    /**
     * Remove a module from the resolver.
     *
     * @param resModule the resolver module
     */
    void removeModule(XModule resModule) {
        resolver.removeModule(resModule);
    }

    /**
     * Get the module for the given id
     *
     * @return The module or null
     */
    XModule getModuleById(XModuleIdentity moduleId) {
        return resolver != null ? resolver.getModuleById(moduleId) : null;
    }

    /**
     * Resolve the given modules.
     *
     * @param resModule the module to resolve
     * @return The set of resolved modules or an empty set
     * @throws BundleException If the resolver could not resolve the module
     */
    void resolve(XModule resModule) throws BundleException {
        List<XModule> resolved = new ArrayList<XModule>();
        resolver.setCallbackHandler(new ResolverCallback(resolved));
        try {
            resolver.resolve(resModule);
        } catch (XResolverException ex) {
            throw new BundleException("Cannot resolve bundle resModule: " + resModule, ex);
        }

        // Load the resolved module
        applyResolverResults(resolved);
    }

    /**
     * Resolve the given set of modules.
     *
     * @param unresolved the modules to resolve
     * @return True if all modules could be resolved
     */
    boolean resolveAll(Set<XModule> unresolved) {

        if (unresolved == null) {
            unresolved = new HashSet<XModule>();

            // Only bundles that are in state INSTALLED and are
            // registered with the resolver qualify as resolvable
            BundleManager bundleManager = injectedBundleManager.getValue();
            Set<AbstractBundleState> allBundles = bundleManager.getBundles();
            for (AbstractBundleState bundleState : allBundles) {
                if (bundleState.getState() == Bundle.INSTALLED) {
                    XModule auxModule = bundleState.getResolverModule();
                    XModuleIdentity moduleId = auxModule.getModuleId();
                    if (getModuleById(moduleId) != null) {
                        unresolved.add(auxModule);
                    }
                }
            }
        }

        List<XModule> resolved = new ArrayList<XModule>();
        resolver.setCallbackHandler(new ResolverCallback(resolved));

        // Resolve the modules
        log.debugf("Resolve modules: %s", unresolved);
        boolean allResolved = resolver.resolveAll(unresolved);

        // Report resolver errors
        if (allResolved == false) {
            for (XModule resModule : unresolved) {
                if (resModule.isResolved() == false) {
                    XResolverException ex = resModule.getAttachment(XResolverException.class);
                    log.errorf(ex, "Cannot resolve: %s", resModule);
                }
            }
        }

        // Apply resolver results
        try {
            applyResolverResults(resolved);
        } catch (BundleException ex) {
            log.debugf(ex, "Exception when applying resolver results.");
            allResolved = false;
        }

        return allResolved;
    }

    private void applyResolverResults(List<XModule> resolved) throws BundleException {
        // Attach the fragments to host
        attachFragmentsToHost(resolved);

        // Resolve native code libraries if there are any
        resolveNativeCodeLibraries(resolved);

        // For every resolved host bundle create the {@link ModuleSpec}
        addModules(resolved);

        // For every resolved host bundle load the module. This creates the {@link ModuleClassLoader}
        loadModules(resolved);

        // Change the bundle state to RESOLVED
        setBundleToResolved(resolved);
    }

    private void attachFragmentsToHost(List<XModule> resolved) {
        for (XModule aux : resolved) {
            if (aux.isFragment() == true) {
                FragmentBundleRevision fragRev = (FragmentBundleRevision) aux.getAttachment(AbstractBundleRevision.class);
                fragRev.attachToHost();
            }
        }
    }

    private void addModules(List<XModule> resolved) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        for (XModule aux : resolved) {
            if (aux.isFragment() == false) {
                moduleManager.addModule(aux);
            }
        }
    }

    private void loadModules(List<XModule> resolved) {
        ModuleManagerPlugin moduleManager = injectedModuleManager.getValue();
        for (XModule aux : resolved) {
            if (aux.isFragment() == false) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(aux);
                try {
                    moduleManager.loadModule(identifier);
                } catch (ModuleLoadException ex) {
                    throw new IllegalStateException("Cannot load module: " + identifier, ex);
                }
            }
        }
    }

    private void resolveNativeCodeLibraries(List<XModule> resolved) throws BundleException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        XModule systemModule = bundleManager.getSystemBundle().getResolverModule();
        for (XModule aux : resolved) {
            if (aux != systemModule) {
                Bundle bundle = aux.getAttachment(Bundle.class);
                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                Deployment deployment = userBundle.getDeployment();

                // Resolve the native code libraries, if there are any
                NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
                if (libMetaData != null) {
                    NativeCodePlugin nativeCodePlugin = injectedNativeCode.getValue();
                    nativeCodePlugin.resolveNativeCode(userBundle);
                }
            }
        }
    }

    private void setBundleToResolved(List<XModule> resolved) {
        for (XModule aux : resolved) {
            Bundle bundle = aux.getAttachment(Bundle.class);
            AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
            bundleState.changeState(Bundle.RESOLVED);
        }
    }

    class ResolverCallback implements XResolverCallback {

        private List<XModule> resolved;

        ResolverCallback(List<XModule> resolved) {
            this.resolved = resolved;
        }

        @Override
        public void markResolved(XModule module) {
            // Construct debug message
            if (log.isDebugEnabled()) {
                StringBuffer buffer = new StringBuffer("Mark resolved: " + module);
                for (XWire wire : module.getWires())
                    buffer.append("\n " + wire.toString());

                log.debugf(buffer.toString());
            }
            resolved.add(module);
        }
    }
}