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
import java.util.HashSet;
import java.util.Set;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.filter.MultiplePathFilterBuilder;
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

/**
 * Test delegation to the system module
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 28-Jan-2011
 */
public class SystemModuleTestCase extends ModulesTestBase {

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
    public void testAvailableOnModule() throws Exception {
        ModuleIdentifier identifierA = ModuleIdentifier.create("moduleA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());
        assertLoadClass(identifierA, "javax.security.auth.x500.X500Principal", identifierA);
    }

    @Test
    public void testDelegationToSystemModule() throws Exception {

        ModuleIdentifier identifierA = ModuleIdentifier.create("moduleA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        PathFilter importFilter = getSystemFilter();
        PathFilter exportFilter = PathFilters.acceptAll();
        specBuilderA.addDependency(DependencySpec.createSystemDependencySpec(importFilter, exportFilter, getSystemPaths()));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierA, "javax.security.auth.x500.X500Principal", null);
    }

    @Test
    public void testTwoHopDelegation() throws Exception {

        ModuleIdentifier identifierB = ModuleIdentifier.create("moduleB");
        ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
        PathFilter importFilter = getSystemFilter();
        PathFilter exportFilter = PathFilters.acceptAll();
        specBuilderB.addDependency(DependencySpec.createSystemDependencySpec(importFilter, exportFilter, getSystemPaths()));
        addModuleSpec(specBuilderB.create());
        
        ModuleIdentifier identifierA = ModuleIdentifier.create("moduleA");
        ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
        VirtualFileResourceLoader resourceLoaderA = new VirtualFileResourceLoader(virtualFileA);
        specBuilderA.addResourceRoot(ResourceLoaderSpec.createResourceLoaderSpec(resourceLoaderA));
        specBuilderA.addDependency(DependencySpec.createModuleDependencySpec(identifierB));
        specBuilderA.addDependency(DependencySpec.createLocalDependencySpec());
        addModuleSpec(specBuilderA.create());

        assertLoadClass(identifierB, "javax.security.auth.x500.X500Principal", null);
        assertLoadClass(identifierA, "javax.security.auth.x500.X500Principal", null);
    }

    private Set<String> getSystemPaths() {
        Set<String> paths = new HashSet<String>();
        paths.add("javax/security/auth/x500");
        return paths;
    }

    private PathFilter getSystemFilter() {
        MultiplePathFilterBuilder pathBuilder = PathFilters.multiplePathFilterBuilder(false);
        pathBuilder.addFilter(PathFilters.isChildOf("javax/security"), true);
        PathFilter importFilter = pathBuilder.create();
        return importFilter;
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
}
