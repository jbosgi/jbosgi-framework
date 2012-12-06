package org.jboss.test.osgi.framework.xservice;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.osgi.framework.spi.AbstractBundleRevisionAdaptor;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.FrameworkModuleLoader;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.VirtualFileResourceLoader;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XBundleRevisionBuilderFactory;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResourceBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * Test Module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jun-2012
 */
public abstract class AbstractModuleIntegrationTest extends OSGiFrameworkTest {

    private Map<Module, VirtualFile> vfsmap = new HashMap<Module, VirtualFile>();

    protected Module loadModule(JavaArchive archive) throws Exception {
        List<ModuleIdentifier> moddeps = Collections.emptyList();
        return loadModule(archive, moddeps);
    }

    protected Module loadModule(JavaArchive archive, List<ModuleIdentifier> moddeps) throws Exception {

        VirtualFile virtualFile = toVirtualFile(archive);

        // Create the {@link ModuleSpec}
        ModuleIdentifier identifier = ModuleIdentifier.fromString(archive.getName());
        ModuleSpec.Builder specBuilder = ModuleSpec.build(identifier);
        for (ModuleIdentifier depid : moddeps) {
            specBuilder.addDependency(DependencySpec.createModuleDependencySpec(depid));
        }
        VirtualFileResourceLoader resourceLoader = new VirtualFileResourceLoader(virtualFile);
        specBuilder.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader));
        specBuilder.addDependency(DependencySpec.createLocalDependencySpec());
        ModuleSpec moduleSpec = specBuilder.create();

        // Add the {@link ModuleSpec} to the {@link ModuleLoaderProvider}
        BundleManager bundleManager = getBundleManager();
        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        ServiceController<?> service = serviceContainer.getRequiredService(IntegrationServices.FRAMEWORK_MODULE_LOADER);
        FrameworkModuleLoader moduleLoader = (FrameworkModuleLoader) service.getValue();
        moduleLoader.addModuleSpec(Mockito.mock(XBundleRevision.class), moduleSpec);

        // Load the {@link Module}
        Module module = moduleLoader.getModuleLoader().loadModule(identifier);
        vfsmap.put(module, virtualFile);
        return module;
    }

    protected BundleManager getBundleManager() throws BundleException {
        return getFramework().adapt(BundleManager.class);
    }

    protected void removeModule(Module module) throws Exception {
        BundleManager bundleManager = getBundleManager();
        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        ServiceController<?> service = serviceContainer.getRequiredService(IntegrationServices.FRAMEWORK_MODULE_LOADER);
        FrameworkModuleLoader moduleLoader = (FrameworkModuleLoader) service.getValue();
        XBundleRevision brev = Mockito.mock(XBundleRevision.class);
        Mockito.when(brev.getModuleIdentifier()).thenReturn(module.getIdentifier());
        moduleLoader.removeModule(brev);
        VFSUtils.safeClose(vfsmap.remove(module));
    }

    protected XBundleRevision installResource(final Module module) throws BundleException {
        // Build the {@link XBundleRevision}
        final BundleContext context = getSystemContext();
        XBundleRevisionBuilderFactory factory = new XBundleRevisionBuilderFactory() {
            @Override
            public XBundleRevision createResource() {
                return new AbstractBundleRevisionAdaptor(context, module);
            }
        };
        XResourceBuilder builder = XBundleRevisionBuilderFactory.create(factory);
        XBundleRevision brev = (XBundleRevision) builder.loadFrom(module).getResource();

        // Add the {@link XBundleRevision} to the {@link XEnvironment}
        XBundle sysbundle = (XBundle) context.getBundle();
        XEnvironment env = sysbundle.adapt(XEnvironment.class);
        env.installResources(brev);
        return brev;
    }

    protected void uninstallResource(final XBundleRevision brev) throws BundleException {
        // Remove the {@link XBundleRevision} from the {@link XEnvironment}
        XBundle sysbundle = (XBundle) getSystemContext().getBundle();
        XEnvironment env = sysbundle.adapt(XEnvironment.class);
        env.uninstallResources(brev);
    }

    protected XBundle getSystemBundle() throws BundleException {
        return (XBundle) getSystemContext().getBundle();
    }
}
