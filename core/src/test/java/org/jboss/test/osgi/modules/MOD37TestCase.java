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

import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.framework.loading.VirtualFileResourceLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.modules.a.A;
import org.jboss.test.osgi.modules.b.B;
import org.jboss.test.osgi.modules.c.C;
import org.jboss.test.osgi.modules.d.D;
import org.junit.Test;

/**
 * [MODULES-37] Re-Export depends on module dependency order
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 15-Sep-2010
 */
public class MOD37TestCase extends ModulesTestBase {

    @Test
    public void testDirectDependencyFirst() throws Exception {
        // ModuleD
        // +--> ModuleC
        // +-reex-> ModuleA
        // +-reex-> ModuleB
        // +--> ModuleA

        JavaArchive archiveA = getModuleA();
        ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        specBuilderA.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveA)));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        JavaArchive archiveB = getModuleB();
        ModuleIdentifier identifierB = ModuleIdentifier.create(archiveB.getName());
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        specBuilderB.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveB)));
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

        JavaArchive archiveA = getModuleA();
        ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        specBuilderA.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveA)));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        JavaArchive archiveB = getModuleB();
        ModuleIdentifier identifierB = ModuleIdentifier.create(archiveB.getName());
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        specBuilderB.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveB)));
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
}
