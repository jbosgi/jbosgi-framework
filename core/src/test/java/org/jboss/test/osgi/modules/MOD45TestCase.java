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
import org.junit.Ignore;
import org.junit.Test;

/**
 * [MODULES-45] Unexpected class load with unwired dependency
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 15-Sep-2010
 */
public class MOD45TestCase extends ModulesTestBase {

    @Test
    @Ignore("[MODULES-45] Unexpected class load with unwired dependency")
    public void testDependencyNotWired() throws Exception {
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
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderB.create());

        assertLoadClass(identifierA, A.class.getName());
        assertLoadClass(identifierA, B.class.getName());

        assertLoadClassFails(identifierB, C.class.getName());
        assertLoadClass(identifierB, D.class.getName());
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
