/*
 * #%L
 * JBossOSGi Framework iTest
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
package org.jboss.test.osgi.framework.xservice;

import static org.jboss.osgi.framework.IntegrationServices.MODULE_LOADER_PROVIDER;

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
import org.jboss.osgi.framework.AbstractBundleRevisionAdaptor;
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.ModuleLoaderProvider;
import org.jboss.osgi.framework.util.VirtualFileResourceLoader;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XBundleRevisionBuilderFactory;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResourceBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
        XBundle sysbundle = (XBundle) getSystemContext().getBundle();
        BundleManager bundleManager = sysbundle.adapt(BundleManager.class);
        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        ServiceController<?> service = serviceContainer.getRequiredService(MODULE_LOADER_PROVIDER);
        ModuleLoaderProvider provider = (ModuleLoaderProvider) service.getValue();
        provider.addModule(moduleSpec);

        // Load the {@link Module}
        Module module = provider.getModuleLoader().loadModule(identifier);
        vfsmap.put(module, virtualFile);
        return module;
    }

    protected void removeModule(Module module) throws Exception {
        // Remove the {@link Module} from the {@link ModuleLoaderProvider}
        XBundle sysbundle = (XBundle) getSystemContext().getBundle();
        BundleManager bundleManager = sysbundle.adapt(BundleManager.class);
        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        ServiceController<?> service = serviceContainer.getRequiredService(MODULE_LOADER_PROVIDER);
        ModuleLoaderProvider provider = (ModuleLoaderProvider) service.getValue();
        provider.removeModule(module.getIdentifier());
        VFSUtils.safeClose(vfsmap.remove(module));
    }

    protected XBundleRevision installResource(final Module module) throws BundleException {
        // Build the {@link XBundleRevision}
        final BundleContext context = getSystemContext();
        XBundleRevisionBuilderFactory factory = new XBundleRevisionBuilderFactory() {
            public XBundleRevision createResource() {
                return new AbstractBundleRevisionAdaptor(context, module);
            }
        };
        XResourceBuilder builder = XBundleRevisionBuilderFactory.create(factory);
        XBundleRevision moduleRevision = (XBundleRevision) builder.loadFrom(module).getResource();

        // Add the {@link XBundleRevision} to the {@link XEnvironment}
        XBundle sysbundle = (XBundle) context.getBundle();
        XEnvironment env = sysbundle.adapt(XEnvironment.class);
        env.installResources(moduleRevision);
        return moduleRevision;
    }

    protected void uninstallResource(final XBundleRevision moduleRev) throws BundleException {
        // Remove the {@link XBundleRevision} from the {@link XEnvironment}
        XBundle sysbundle = (XBundle) getSystemContext().getBundle();
        XEnvironment env = sysbundle.adapt(XEnvironment.class);
        env.uninstallResources(moduleRev);
    }

    protected XBundle getSystemBundle() throws BundleException {
        return (XBundle) getSystemContext().getBundle();
    }
}