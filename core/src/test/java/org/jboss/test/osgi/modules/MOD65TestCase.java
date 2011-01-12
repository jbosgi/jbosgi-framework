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
import java.util.Set;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.jboss.osgi.framework.loading.VirtualFileResourceLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.modules.a.CircularityError;
import org.jboss.test.osgi.modules.b.CircularityActivator;
import org.jboss.test.osgi.modules.b.CircularityErrorDep;
import org.junit.Ignore;
import org.junit.Test;

/**
 * [MODULES-65] Deadlock when LocalLoader attempts a circular class load
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 20-Dec-2010
 */
public class MOD65TestCase extends ModulesTestBase {

    @Test
    @Ignore("[MODULES-65] Deadlock when LocalLoader attempts a circular class load")
    public void testCircularityError() throws Exception {
        JavaArchive archiveA = getModuleA();
        ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());

        JavaArchive archiveB = getModuleB();
        ModuleIdentifier identifierB = ModuleIdentifier.create(archiveB.getName());

        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        specBuilderA.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveA)));
        LazyActivationLocalLoader localLoader = new LazyActivationLocalLoader(identifierA, identifierB);
        Set<String> lazyPaths = Collections.singleton(getPath(CircularityActivator.class.getName()));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec(localLoader, lazyPaths, true));
        addModuleSpec(specBuilderA.create());

        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        specBuilderB.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveB)));
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderB.create());

        assertLoadClass(identifierB, CircularityError.class.getName());
    }

    class LazyActivationLocalLoader implements LocalLoader {

        private final ModuleIdentifier identifierA, identifierB;

        LazyActivationLocalLoader(ModuleIdentifier identifierA, ModuleIdentifier identifierB) {
            this.identifierA = identifierA;
            this.identifierB = identifierB;
        }

        @Override
        public Class<?> loadClassLocal(String className, boolean resolve) {
            try {
                ModuleLoaderSupport moduleLoader = getModuleLoader();
                Module moduleA = moduleLoader.loadModule(identifierA);
                List<DependencySpec> dependencies = Collections.singletonList(DependencySpec.createLocalDependencySpec());
                moduleLoader.setAndRelinkDependencies(moduleA, dependencies);
                Class<?> definedClass = moduleA.getClassLoader().loadClass(className);

                // After defining the class the LocalLoader may call into the ModuleActivator
                // which in turn may try to load a class that initiated a call to this local loader
                Module moduleB = moduleLoader.loadModule(identifierB);
                moduleB.getClassLoader().loadClass(CircularityError.class.getName());

                return definedClass;
            } catch (Throwable th) {
                throw new IllegalStateException(th);
            }
        }

        @Override
        public List<Resource> loadResourceLocal(String name) {
            return null;
        }

        @Override
        public Resource loadResourceLocal(String root, String name) {
            return null;
        }
    }

    private JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(CircularityErrorDep.class, CircularityActivator.class);
        return archive;
    }

    private JavaArchive getModuleB() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addClasses(CircularityError.class);
        return archive;
    }
}
