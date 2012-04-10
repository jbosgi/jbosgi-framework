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
package org.jboss.test.osgi.modules;

import java.util.Collections;
import java.util.List;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.osgi.framework.util.VirtualFileResourceLoader;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.modules.a.A;
import org.jboss.test.osgi.modules.b.B;
import org.jboss.test.osgi.modules.c.C;
import org.jboss.test.osgi.modules.d.D;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test low level modules use cases.
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 15-Sep-2010
 */
public class ModulesTestCase extends ModulesTestBase {

    private VirtualFile virtualFileA;
    private VirtualFile virtualFileB;
    private VirtualFile virtualFileC;
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        virtualFileA = toVirtualFile(getModuleA());
        virtualFileB = toVirtualFile(getModuleB());
        virtualFileC = toVirtualFile(getModuleC());
    }

    @After
    public void tearDown() throws Exception {
        VFSUtils.safeClose(virtualFileA);
        VFSUtils.safeClose(virtualFileB);
        VFSUtils.safeClose(virtualFileC);
    }

    @Test
    public void testNoResourceRoot() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        addModuleSpec(specBuilderA.create());

        assertLoadClassFail(identifierA, A.class.getName());
        assertLoadClassFail(identifierA, B.class.getName());
    }

    @Test
    public void testResourceLoader() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoader = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());
    }

    @Test
    public void testExportFilterOnResourceLoader() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoader = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, A.class.getName());
        // [TODO] MODULES-64 ExportFilter on ResourceLoader has no effect
        // assertLoadClassFails(identifierA, B.class.getName());
    }

    @Test
    public void testImportFilterOnLocalDependency() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoader = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec(getPathFilter(A.class), PathFilters.acceptAll()));
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClassFail(identifierA, B.class.getName());
    }

    @Test
    public void testLazyActivation() throws Exception {
        PathFilter eagerPathsFilter = getPathFilter(A.class);
        PathFilter lazyPathsFilter = PathFilters.not(eagerPathsFilter);
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoader = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoader));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec(eagerPathsFilter, PathFilters.acceptAll()));
        specBuilderA.setFallbackLoader(new RelinkFallbackLoader(identifierA, lazyPathsFilter));
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());
    }

    @Test
    public void testDependencyNotWired() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("archiveB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderB.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        assertLoadClassFail(identifierB, C.class.getName());
        assertLoadClass(identifierB, D.class.getName());
    }

    @Test
    public void testDependencyWiredNoFilters() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("archiveB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        addModuleSpec(specBuilderB.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        assertLoadClass(identifierB, A.class.getName());
        assertLoadClass(identifierB, C.class.getName());
    }

    @Test
    public void testDependencyTwoExportersNotWired() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierC = ModuleIdentifier.create("archiveC");
        ModuleSpec.Builder specBuilderC = ModuleSpec.build(identifierC);
        VirtualFileResourceLoader resourceLoaderC = new VirtualFileResourceLoader(virtualFileC);
        specBuilderC.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderC));
        specBuilderC.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderC.create());

        assertLoadClass(identifierA, A.class.getName(), identifierA);
        assertLoadClass(identifierC, A.class.getName(), identifierC);
    }

    @Test
    public void testDependencyHidesLocal() throws Exception {
        
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierC = ModuleIdentifier.create("archiveC");
        ModuleSpec.Builder specBuilderC = ModuleSpec.build(identifierC);
        VirtualFileResourceLoader resourceLoaderC = new VirtualFileResourceLoader(virtualFileC);
        specBuilderC.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderC));
        specBuilderC.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        specBuilderC.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderC.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        // moduleC also contains A, which however should be loaded from moduleA
        assertLoadClass(identifierC, A.class.getName(), identifierA);
        assertLoadClass(identifierC, B.class.getName());
        assertLoadClass(identifierC, C.class.getName());
    }

    @Test
    public void testDependencyExportFilter() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec(PathFilters.acceptAll(), getPathFilter(A.class)));
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("archiveB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        addModuleSpec(specBuilderB.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        // moduleA has an export filter on A, B is not visible
        assertLoadClass(identifierB, A.class.getName(), identifierA);
        assertLoadClassFail(identifierB, B.class.getName());

        assertLoadClass(identifierB, C.class.getName());
        assertLoadClass(identifierB, D.class.getName());
    }

    @Test
    public void testDependencyImportFilter() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("archiveB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(getPathFilter(A.class), PathFilters.acceptAll(), getModuleLoader(), identifierA, false));
        addModuleSpec(specBuilderB.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        // The dependency on moduleA has an import filter on A, B is not visible
        assertLoadClass(identifierB, A.class.getName(), identifierA);
        assertLoadClassFail(identifierB, B.class.getName());

        assertLoadClass(identifierB, C.class.getName());
        assertLoadClass(identifierB, D.class.getName());
    }

    @Test
    public void testDependencyNoReExport() throws Exception {
        // ModuleX -> ModuleB -> ModuleA

        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("archiveB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        addModuleSpec(specBuilderB.create());

        ModuleIdentifier identifierX = ModuleIdentifier.create("dummy");
        ModuleSpec.Builder specBuilderX = ModuleSpec.build(identifierX);
        specBuilderX.addDependency(DependencySpec.createModuleDependencySpec(identifierB));
        addModuleSpec(specBuilderX.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        assertLoadClass(identifierB, C.class.getName());
        assertLoadClass(identifierB, D.class.getName());
        assertLoadClass(identifierB, A.class.getName());
        assertLoadClass(identifierB, B.class.getName());

        assertLoadClass(identifierX, C.class.getName());
        assertLoadClass(identifierX, D.class.getName());
        assertLoadClassFail(identifierX, A.class.getName());
        assertLoadClassFail(identifierX, B.class.getName());
    }

    @Test
    public void testDependencyExplicitReExport() throws Exception {
        // ModuleX -> ModuleB -> ModuleA

        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("archiveB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(getPathFilter(A.class), identifierA, false));
        addModuleSpec(specBuilderB.create());

        ModuleIdentifier identifierX = ModuleIdentifier.create("dummy");
        ModuleSpec.Builder specBuilderX = ModuleSpec.build(identifierX);
        specBuilderX.addDependency(DependencySpec.createModuleDependencySpec(identifierB));
        addModuleSpec(specBuilderX.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        assertLoadClass(identifierB, C.class.getName());
        assertLoadClass(identifierB, D.class.getName());
        assertLoadClass(identifierB, A.class.getName());
        assertLoadClass(identifierB, B.class.getName());

        assertLoadClass(identifierX, C.class.getName());
        assertLoadClass(identifierX, D.class.getName());
        assertLoadClass(identifierX, A.class.getName());
        assertLoadClassFail(identifierX, B.class.getName());
    }

    @Test
    public void testDependencyReExportAll() throws Exception {
        // ModuleX -> ModuleB -> ModuleA

        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec(PathFilters.acceptAll(), getPathFilter(A.class)));
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("archiveB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA, true));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderB.create());

        ModuleIdentifier identifierX = ModuleIdentifier.create("dummy");
        ModuleSpec.Builder specBuilderX = ModuleSpec.build(identifierX);
        specBuilderX.addDependency(DependencySpec.createModuleDependencySpec(identifierB, true));
        addModuleSpec(specBuilderX.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        assertLoadClass(identifierB, C.class.getName());
        assertLoadClass(identifierB, D.class.getName());
        assertLoadClass(identifierB, A.class.getName());
        assertLoadClassFail(identifierB, B.class.getName());

        assertLoadClass(identifierX, C.class.getName());
        assertLoadClass(identifierX, D.class.getName());
        assertLoadClass(identifierX, A.class.getName());
        assertLoadClassFail(identifierX, B.class.getName());
    }

    class RelinkFallbackLoader implements LocalLoader {

        private final ModuleIdentifier identifier;
        private final PathFilter activationFilter;

        RelinkFallbackLoader(ModuleIdentifier identifier, PathFilter activationFilter) {
            this.identifier = identifier;
            this.activationFilter = activationFilter;
        }

        @Override
        public Class<?> loadClassLocal(String className, boolean resolve) {
            if (activationFilter.accept(getPathForClassName(className)) == false)
                return null;

            try {
                ModuleLoaderSupport moduleLoader = getModuleLoader();
                Module module = moduleLoader.loadModule(identifier);
                List<DependencySpec> dependencies = Collections.singletonList(DependencySpec.createLocalDependencySpec());
                moduleLoader.setAndRelinkDependencies(module, dependencies);
                return module.getClassLoader().loadClass(className);
            } catch (ModuleLoadException ex) {
                throw new IllegalStateException(ex);
            } catch (ClassNotFoundException ex) {
                return null;
            }
        }

        @Override
        public Package loadPackageLocal(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Resource> loadResourceLocal(String name) {
            return null;
        }
    }

    private JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(A.class, B.class);
        return archive;
    }

    private JavaArchive getModuleB() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addClasses(C.class, D.class);
        return archive;
    }

    private JavaArchive getModuleC() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleC");
        archive.addClasses(A.class, C.class);
        return archive;
    }
}
