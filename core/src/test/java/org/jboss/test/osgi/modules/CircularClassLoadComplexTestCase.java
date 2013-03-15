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

import org.jboss.modules.ClassSpec;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.osgi.framework.spi.VirtualFileResourceLoader;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.modules.a.CircularityError;
import org.jboss.test.osgi.modules.b.CircularityActivator;
import org.jboss.test.osgi.modules.b.CircularityErrorDep;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * [MODULES-65] Deadlock on circular class load
 *
 * @author Thomas.Diesler@jboss.com
 * @since 20-Dec-2010
 */
public class CircularClassLoadComplexTestCase extends ModulesTestBase {

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
    public void testPostDefineHook() throws Exception {
        JavaArchive archiveA = getModuleA();
        ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());

        JavaArchive archiveB = getModuleB();
        ModuleIdentifier identifierB = ModuleIdentifier.create(archiveB.getName());

        Set<String> sysPaths = Collections.singleton("org/jboss/modules");
        PathFilter sysImports = PathFilters.is("org/jboss/modules");
        PathFilter sysExports = PathFilters.rejectAll();

        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        VirtualFileResourceLoader resourceLoaderB = new VirtualFileResourceLoader(virtualFileB);
        specBuilderB.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderB));
        specBuilderB.addDependency(DependencySpec.createLocalDependencySpec());
        specBuilderB.addDependency(DependencySpec.createSystemDependencySpec(sysImports, sysExports, sysPaths));
        Set<String> lazyPaths = Collections.singleton(getPathForClassName(CircularityErrorDep.class.getName()));
        specBuilderB.setModuleClassLoaderFactory(new PostDefineModuleClassLoader.Factory(lazyPaths));
        addModuleSpec(specBuilderB.create());

        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        specBuilderA.addDependency(DependencySpec.createModuleDependencySpec(identifierB));
        addModuleSpec(specBuilderA.create());

        List<ModuleIdentifier> activated = new ArrayList<ModuleIdentifier>();
        Class<?> clazz = loadClassLazily(identifierA, CircularityError.class.getName(), activated);
        assertNotNull("Class not null", clazz);
        assertEquals("One module activated", 1, activated.size());
        assertEquals("ModuleB activated", identifierB, activated.get(0));
    }

    Class<?> loadClassLazily(ModuleIdentifier moduleId, String className, List<ModuleIdentifier> activated) throws Exception {
        Class<?> loadedClass = getModuleLoader().loadModule(moduleId).getClassLoader().loadClass(className, true);
        Stack<ModuleIdentifier> stack = LazyActivationStack.getLazyActivationStack();
        while (stack.isEmpty() == false) {
            ModuleIdentifier auxid = stack.pop();
            if (activated.contains(auxid) == false && activateModule(auxid))
                activated.add(auxid);
        }
        return loadedClass;
    }

    static class LazyActivationStack {
        static ThreadLocal<Stack<ModuleIdentifier>> stackAssociation = new ThreadLocal<Stack<ModuleIdentifier>>();

        static Stack<ModuleIdentifier> getLazyActivationStack() {
            Stack<ModuleIdentifier> stack = stackAssociation.get();
            if (stack == null) {
                stack = new Stack<ModuleIdentifier>();
                stackAssociation.set(stack);
            }
            return stack;
        }
    }

    static class PostDefineModuleClassLoader extends ModuleClassLoader {

        private final PathFilter activationFilter;

        private PostDefineModuleClassLoader(Configuration configuration, Set<String> lazyPaths) {
            super(configuration);
            activationFilter = PathFilters.in(lazyPaths);
        }

        @Override
        protected void postDefine(ClassSpec classSpec, Class<?> definedClass) {
            String path = definedClass.getPackage().getName().replace('.', '/');
            if (activationFilter.accept(path)) {
                Stack<ModuleIdentifier> stack = LazyActivationStack.getLazyActivationStack();
                Module module = ((ModuleClassLoader) definedClass.getClassLoader()).getModule();
                ModuleIdentifier identifier = module.getIdentifier();
                if (stack.contains(identifier) == false) {
                    stack.push(identifier);
                }
            }
        }

        static class Factory implements ModuleClassLoaderFactory {

            private final Set<String> lazyPaths;

            private Factory(Set<String> lazyPaths) {
                this.lazyPaths = lazyPaths;
            }

            @Override
            public ModuleClassLoader create(Configuration configuration) {
                return new PostDefineModuleClassLoader(configuration, lazyPaths);
            }
        }
    }

    private JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(CircularityError.class);
        return archive;
    }

    private JavaArchive getModuleB() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addClasses(CircularityErrorDep.class, CircularityActivator.class, ModuleActivator.class);
        File resourceFile = OSGiTestHelper.getResourceFile("modules/moduleB/META-INF/services/" + ModuleActivator.class.getName());
        archive.addAsManifestResource(resourceFile, "services/" + ModuleActivator.class.getName());
        return archive;
    }
}
