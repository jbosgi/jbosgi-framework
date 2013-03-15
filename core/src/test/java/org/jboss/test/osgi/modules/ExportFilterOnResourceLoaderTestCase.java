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
import org.jboss.modules.filter.PathFilters;
import org.jboss.osgi.framework.spi.VirtualFileResourceLoader;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.modules.a.A;
import org.jboss.test.osgi.modules.b.B;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * [MODULES-64] ExportFilter on ResourceLoader has no effect
 *
 * @author Thomas.Diesler@jboss.com
 * @since 15-Sep-2010
 */
public class ExportFilterOnResourceLoaderTestCase extends ModulesTestBase {

    private VirtualFile virtualFileA;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        virtualFileA = toVirtualFile(getModuleA());
    }

    @After
    public void tearDown() throws Exception {
        VFSUtils.safeClose(virtualFileA);
    }

    @Test
    public void testExportFilterOnResourceLoader() throws Exception {
        JavaArchive archiveA = getModuleA();
        ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA, getPathFilter(A.class)));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClassFail(identifierA, B.class.getName());
    }

    @Test
    public void testExportFilterOnLocalLoader() throws Exception {
        JavaArchive archiveA = getModuleA();
        ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec(PathFilters.acceptAll(), getPathFilter(A.class)));
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        ModuleIdentifier identifierB = ModuleIdentifier.create("moduleB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        addModuleSpec(specBuilderB.create());

        assertLoadClass(identifierB, A.class.getName());
        assertLoadClassFail(identifierB, B.class.getName());
    }

    private JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(A.class, B.class);
        return archive;
    }
}
