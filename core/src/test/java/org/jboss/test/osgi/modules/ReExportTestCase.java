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
import org.jboss.test.osgi.modules.d.D;
import org.jboss.test.osgi.modules.e.E;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test Re-Export for classes and resources
 *
 * @author Thomas.Diesler@jboss.com
 * @since 26-Jan-2011
 */
public class ReExportTestCase extends ModulesTestBase {

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
    public void testReexport() throws Exception {

        // ModuleD
        // +--> ModuleC
        //      +--> ModuleA
        //      +-reex-> ModuleB

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

        ModuleIdentifier identifierC = ModuleIdentifier.create("moduleC");
        ModuleSpec.Builder specBuilderC = ModuleSpec.build(identifierC);
        specBuilderC.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        specBuilderC.addDependency(DependencySpec.createModuleDependencySpec(getPathFilter(D.class), getPathFilter(D.class), getModuleLoader(), identifierB, false));
        addModuleSpec(specBuilderC.create());

        ModuleIdentifier identifierD = ModuleIdentifier.create("moduleD");
        ModuleSpec.Builder specBuilderD = ModuleSpec.build(identifierD);
        specBuilderD.addDependency(DependencySpec.createModuleDependencySpec(identifierC));
        addModuleSpec(specBuilderD.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierC, A.class.getName());
        assertLoadClassFail(identifierD, A.class.getName());

        assertLoadClass(identifierA, B.class.getName());
        assertLoadClass(identifierC, B.class.getName());
        assertLoadClassFail(identifierD, B.class.getName());

        assertNotNull(getResource(identifierA, getResourcePath(A.class)));
        assertNotNull(getResource(identifierC, getResourcePath(A.class)));
        assertNull(getResource(identifierD, getResourcePath(A.class)));

        assertNotNull(getResource(identifierA, getResourcePath(B.class)));
        assertNotNull(getResource(identifierC, getResourcePath(B.class)));
        assertNull(getResource(identifierD, getResourcePath(B.class)));

        assertLoadClass(identifierB, D.class.getName());
        assertLoadClass(identifierC, D.class.getName());
        assertLoadClass(identifierD, D.class.getName());

        assertLoadClass(identifierB, E.class.getName());
        assertLoadClassFail(identifierC, E.class.getName());
        assertLoadClassFail(identifierD, E.class.getName());

        assertNotNull(getResource(identifierB, getResourcePath(D.class)));
        assertNotNull(getResource(identifierC, getResourcePath(D.class)));
        assertNotNull(getResource(identifierD, getResourcePath(D.class)));

        assertNotNull(getResource(identifierB, getResourcePath(E.class)));
        assertNull(getResource(identifierC, getResourcePath(E.class)));
        assertNull(getResource(identifierD, getResourcePath(E.class)));
    }

    private String getResourcePath(Class<?> clazz) {
        return clazz.getName().replace('.', '/') + ".class";
    }

    private static JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(A.class, B.class);
        return archive;
    }

    private static JavaArchive getModuleB() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addClasses(D.class, E.class);
        return archive;
    }
}
