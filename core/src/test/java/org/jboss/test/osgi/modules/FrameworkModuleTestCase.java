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

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.PathFilter;
import org.jboss.modules.filter.PathFilters;
import org.jboss.osgi.framework.util.VirtualFileResourceLoader;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * Test delegation to the framework module
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 28-Jan-2011
 */
public class FrameworkModuleTestCase extends ModulesTestBase {

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
    public void testNotAvailableOnModule() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("moduleA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, "javax.security.auth.x500.X500Principal", identifierA);
        assertLoadClassFail(identifierA, "org.osgi.framework.Bundle");
    }

    @Test
    public void testNotAvailableOnSystemModule() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("moduleA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        PathFilter importFilter = getSystemFilter();
        PathFilter exportFilter = PathFilters.acceptAll();
        specBuilderA.addDependency(DependencySpec.createSystemDependencySpec(importFilter, exportFilter, getSystemPaths()));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClassFail(identifierA, "org.osgi.framework.Bundle");
        assertLoadClass(identifierA, "javax.security.auth.x500.X500Principal");
    }

    @Test
    public void testAvailableFrameworkModule() throws Exception {
        ModuleIdentifier identifierF = ModuleIdentifier.create("framework");
        ModuleSpec.Builder specBuilderF = ModuleSpec.build(identifierF);
        PathFilter importFilter = PathFilters.in(getFrameworkPaths());
        PathFilter exportFilter = PathFilters.acceptAll();
        FrameworkLocalLoader localLoader = new FrameworkLocalLoader(Bundle.class.getClassLoader());
        specBuilderF.addDependency(DependencySpec.createLocalDependencySpec(importFilter, exportFilter, localLoader, getFrameworkPaths()));
        addModuleSpec(specBuilderF.create());

        assertLoadClass(identifierF, "org.osgi.framework.Bundle");
        assertLoadClassFail(identifierF, "javax.security.auth.x500.X500Principal");
    }

    @Test
    public void testFrameworkDelegatesToSystem() throws Exception {
        ModuleIdentifier identifierF = ModuleIdentifier.create("framework");
        ModuleSpec.Builder specBuilderF = ModuleSpec.build(identifierF);
        PathFilter importFilter = getSystemFilter();
        PathFilter exportFilter = PathFilters.acceptAll();
        specBuilderF.addDependency(DependencySpec.createSystemDependencySpec(importFilter, exportFilter, getSystemPaths()));
        importFilter = PathFilters.in(getFrameworkPaths());
        exportFilter = PathFilters.acceptAll();
        FrameworkLocalLoader localLoader = new FrameworkLocalLoader(Bundle.class.getClassLoader());
        specBuilderF.addDependency(DependencySpec.createLocalDependencySpec(importFilter, exportFilter, localLoader, getFrameworkPaths()));
        addModuleSpec(specBuilderF.create());

        assertLoadClass(identifierF, "org.osgi.framework.Bundle");
        assertLoadClass(identifierF, "javax.security.auth.x500.X500Principal");
    }

    @Test
    public void testModuleDelegatesToFramework() throws Exception {
        ModuleIdentifier identifierF = ModuleIdentifier.create("framework");
        ModuleSpec.Builder specBuilderF = ModuleSpec.build(identifierF);
        PathFilter importFilter = getSystemFilter();
        PathFilter exportFilter = PathFilters.acceptAll();
        specBuilderF.addDependency(DependencySpec.createSystemDependencySpec(importFilter, exportFilter, getSystemPaths()));
        importFilter = PathFilters.in(getFrameworkPaths());
        exportFilter = PathFilters.acceptAll();
        FrameworkLocalLoader localLoader = new FrameworkLocalLoader(Bundle.class.getClassLoader());
        specBuilderF.addDependency(DependencySpec.createLocalDependencySpec(importFilter, exportFilter, localLoader, getFrameworkPaths()));
        addModuleSpec(specBuilderF.create());

        ModuleIdentifier identifierA = ModuleIdentifier.create("moduleA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createModuleDependencySpec(identifierF));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, "org.osgi.framework.Bundle");
        assertLoadClass(identifierA, "javax.security.auth.x500.X500Principal");
    }

    private Set<String> getFrameworkPaths() {
        Set<String> paths = new HashSet<String>();
        paths.add("org/osgi/framework");
        return Collections.unmodifiableSet(paths);
    }

    private Set<String> getSystemPaths() {
        Set<String> paths = new HashSet<String>();
        paths.add("javax/security/auth/x500");
        return paths;
    }

    private PathFilter getSystemFilter() {
        return PathFilters.in(getSystemPaths());
    }

    private JavaArchive getModuleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClass("javax.security.auth.x500.X500Principal");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                return builder.openStream();
            }
        });
        return archive;
    }

    static class FrameworkLocalLoader implements LocalLoader {
        private ClassLoader classLoader;

        public FrameworkLocalLoader(ClassLoader classLoader) {
            if (classLoader == null)
                throw new IllegalArgumentException("Null classLoader");
            this.classLoader = classLoader;
        }

        @Override
        public Class<?> loadClassLocal(String name, boolean resolve) {
            try {
                return classLoader.loadClass(name);
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
            return Collections.emptyList();
        }
    }
}
