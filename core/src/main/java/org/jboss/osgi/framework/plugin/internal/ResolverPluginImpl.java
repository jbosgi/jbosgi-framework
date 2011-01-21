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
package org.jboss.osgi.framework.plugin.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.AbstractRevision;
import org.jboss.osgi.framework.bundle.AbstractUserBundle;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.FragmentRevision;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.framework.plugin.NativeCodePlugin;
import org.jboss.osgi.framework.plugin.ResolverPlugin;
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
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2009
 */
public class ResolverPluginImpl extends AbstractPlugin implements ResolverPlugin {

    // Provide logging
    final Logger log = Logger.getLogger(ResolverPluginImpl.class);

    private final XResolverFactory factory;
    private final NativeCodePlugin nativeCodePlugin;
    private final ModuleManagerPlugin moduleManager;

    private XResolver resolver;

    public ResolverPluginImpl(BundleManager bundleManager) {
        super(bundleManager);
        factory = XResolverFactory.getInstance(getClass().getClassLoader());
        nativeCodePlugin = getOptionalPlugin(NativeCodePlugin.class);
        moduleManager = getPlugin(ModuleManagerPlugin.class);
    }

    @Override
    public void initPlugin() {
        // Create the {@link XResolver}
        resolver = factory.newResolver();
    }

    @Override
    public void destroyPlugin() {
        // Destroy the {@link XResolver}
        resolver = null;
    }

    @Override
    public XResolver getResolver() {
        return resolver;
    }

    @Override
    public XModuleBuilder getModuleBuilder() {
        return factory.newModuleBuilder();
    }

    @Override
    public void addModule(XModule resModule) {
        resolver.addModule(resModule);
    }

    @Override
    public void removeModule(XModule resModule) {
        resolver.removeModule(resModule);
    }

    @Override
    public XModule getModuleById(XModuleIdentity moduleId) {
        return resolver != null ? resolver.getModuleById(moduleId) : null;
    }

    @Override
    public void resolve(XModule resModule) throws BundleException {
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

    @Override
    public boolean resolveAll(Set<XModule> unresolved) {

        if (unresolved == null) {
            unresolved = new HashSet<XModule>();
            
            // Only bundles that are in state INSTALLED and are
            // registered with the resolver qualify as resolvable
            List<AbstractBundle> allBundles = getBundleManager().getBundles();
            for (AbstractBundle bundleState : allBundles) {
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
        applyResolverResults(resolved);

        return allResolved;
    }

    private void applyResolverResults(List<XModule> resolved) {
        // Attach the fragments to host
        attachFragmentsToHost(resolved);

        // For every resolved host bundle create the {@link ModuleSpec}
        addModules(resolved);

        // For every resolved host bundle load the module. This creates the {@link ModuleClassLoader}
        loadModules(resolved);

        // Resolve native code libraries if there are any
        resolveNativeCodeLibraries(resolved);

        // Change the bundle state to RESOLVED
        setBundleToResolved(resolved);
    }

    private void attachFragmentsToHost(List<XModule> resolved) {
        for (XModule aux : resolved) {
            if (aux.isFragment() == true) {
                FragmentRevision fragRev = (FragmentRevision) aux.getAttachment(AbstractRevision.class);
                fragRev.attachToHost();
            }
        }
    }

    private void addModules(List<XModule> resolved) {
        for (XModule aux : resolved) {
            if (aux.isFragment() == false) {
                moduleManager.addModule(aux);
            }
        }
    }

    private void loadModules(List<XModule> resolved) {
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

    private void resolveNativeCodeLibraries(List<XModule> resolved) {
        XModule systemModule = getBundleManager().getSystemBundle().getResolverModule();
        for (XModule aux : resolved) {
            if (aux != systemModule) {
                Bundle bundle = aux.getAttachment(Bundle.class);
                AbstractUserBundle bundleState = AbstractUserBundle.assertBundleState(bundle);
                Deployment deployment = bundleState.getDeployment();

                // Resolve the native code libraries, if there are any
                NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
                if (nativeCodePlugin != null && libMetaData != null)
                    nativeCodePlugin.resolveNativeCode(bundleState);
            }
        }
    }

    private void setBundleToResolved(List<XModule> resolved) {
        for (XModule aux : resolved) {
            Bundle bundle = aux.getAttachment(Bundle.class);
            AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
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