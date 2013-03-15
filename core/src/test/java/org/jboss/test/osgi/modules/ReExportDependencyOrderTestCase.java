package org.jboss.test.osgi.modules;
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

import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.osgi.framework.spi.VirtualFileResourceLoader;
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
 * [MODULES-37] Re-Export depends on module dependency order
 *
 * @author Thomas.Diesler@jboss.com
 * @since 15-Sep-2010
 */
public class ReExportDependencyOrderTestCase extends ModulesTestBase {

    private VirtualFile virtualFileA;
    private VirtualFile virtualFileB;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        virtualFileA = toVirtualFile(getModuleA());
        virtualFileB = toVirtualFile(getModuleB());
    }

    @After
    public void tearDown() throws Exception {
        VFSUtils.safeClose(virtualFileA);
        VFSUtils.safeClose(virtualFileB);
    }

    @Test
    public void testDirectDependencyFirst() throws Exception {

        // ModuleD
        // +--> ModuleC
        // +-reex-> ModuleA
        // +-reex-> ModuleB
        // +--> ModuleA

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
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderB.create());

        ModuleIdentifier identifierC = ModuleIdentifier.create("moduleC");
        ModuleSpec.Builder specBuilderC = ModuleSpec.build(identifierC);
        specBuilderC.addDependency(DependencySpec.createModuleDependencySpec(identifierA, true)); // moduleA
        specBuilderC.addDependency(DependencySpec.createModuleDependencySpec(identifierB, true)); // moduleB
        addModuleSpec(specBuilderC.create());

        ModuleIdentifier identifierD = ModuleIdentifier.create("moduleD");
        ModuleSpec.Builder specBuilderD = ModuleSpec.build(identifierD);
        DependencySpec modDepC = DependencySpec.createModuleDependencySpec(identifierC);
        specBuilderD.addDependency(modDepC);
        addModuleSpec(specBuilderD.create());

        assertLoadClass(identifierD, A.class.getName());
        assertLoadClass(identifierD, C.class.getName());
    }

    @Test
    public void testDirectDependencySecond() throws Exception {

        // ModuleD
        // +--> ModuleC
        // +-reex-> ModuleB
        // | +--> ModuleA
        // +-reex-> ModuleA

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
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderB.create());

        ModuleIdentifier identifierC = ModuleIdentifier.create("moduleC");
        ModuleSpec.Builder specBuilderC = ModuleSpec.build(identifierC);
        specBuilderC.addDependency(DependencySpec.createModuleDependencySpec(identifierB, true)); // moduleB
        specBuilderC.addDependency(DependencySpec.createModuleDependencySpec(identifierA, true)); // moduleA
        addModuleSpec(specBuilderC.create());

        ModuleIdentifier identifierD = ModuleIdentifier.create("moduleD");
        ModuleSpec.Builder specBuilderD = ModuleSpec.build(identifierD);
        specBuilderD.addDependency(DependencySpec.createModuleDependencySpec(identifierC));
        addModuleSpec(specBuilderD.create());

        assertLoadClass(identifierD, A.class.getName());
        assertLoadClass(identifierD, C.class.getName());
    }

    private static JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(A.class, B.class);
        return archive;
    }

    private static JavaArchive getModuleB() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addClasses(C.class, D.class);
        return archive;
    }
}
