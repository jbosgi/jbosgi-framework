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
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.osgi.framework.util.VirtualFileResourceLoader;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.modules.a.Foo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Collections;
import java.util.ServiceLoader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test usage of the java.util.ServiceLoader API
 *
 * @author Thomas.Diesler@jboss.com
 * @since 22-Sep-2010
 */
public class ServiceLoaderTestCase extends ModulesTestBase {

    String resName = "META-INF/services/" + Foo.class.getName();

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
    public void testServiceLoader() throws Exception {

        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        Module moduleA = loadModule(identifierA);
        ModuleClassLoader classloaderA = moduleA.getClassLoader();

        URL resURL = classloaderA.getResource(resName);
        assertNotNull("Resource found", resURL);

        ServiceLoader<Foo> serviceLoader = ServiceLoader.load(Foo.class);
        assertNotNull("ServiceLoader not null", serviceLoader);
        assertFalse("ServiceLoader no next", serviceLoader.iterator().hasNext());

        serviceLoader = ServiceLoader.load(Foo.class, classloaderA);
        assertNotNull("ServiceLoader not null", serviceLoader);
        assertTrue("ServiceLoader next", serviceLoader.iterator().hasNext());
    }

    @Test
    public void testLoadFromDependency() throws Exception {

        ModuleIdentifier identifierA = ModuleIdentifier.create("archiveA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        ModuleIdentifier identifierB = ModuleIdentifier.create("moduleB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        PathFilter importFilter = PathFilters.in(Collections.singleton("META-INF/services"));
        PathFilter exportFilter = PathFilters.acceptAll();
        specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(importFilter, exportFilter, getModuleLoader(), identifierA, false));
        addModuleSpec(specBuilderB.create());

        Module moduleA = loadModule(identifierA);
        ModuleClassLoader classloaderA = moduleA.getClassLoader();

        URL resURL = classloaderA.getResource(resName);
        assertNotNull("Resource found", resURL);

        Module moduleB = loadModule(identifierB);
        ModuleClassLoader classloaderB = moduleB.getClassLoader();

        resURL = classloaderB.getResource(resName);
        assertNotNull("Resource found", resURL);

        ServiceLoader<Foo> serviceLoader = ServiceLoader.load(Foo.class);
        assertNotNull("ServiceLoader not null", serviceLoader);
        assertFalse("ServiceLoader no next", serviceLoader.iterator().hasNext());

        serviceLoader = ServiceLoader.load(Foo.class, classloaderB);
        assertNotNull("ServiceLoader not null", serviceLoader);
        assertTrue("ServiceLoader next", serviceLoader.iterator().hasNext());
    }

    private JavaArchive getModuleA() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(Foo.class);
        archive.addAsResource("modules/" + resName, resName);
        return archive;
    }
}
