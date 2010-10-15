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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Collections;
import java.util.ServiceLoader;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleSpec;
import org.jboss.modules.PathFilter;
import org.jboss.modules.PathFilters;
import org.jboss.osgi.framework.loading.VirtualFileResourceLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.modules.a.Foo;
import org.junit.Test;

/**
 * Test usage of the java.util.ServiceLoader API
 *
 * @author Thomas.Diesler@jboss.com
 * @since 22-Sep-2010
 */
public class ServiceLoaderTestCase extends ModulesTestBase
{
   String resName = "META-INF/services/" + Foo.class.getName();

   @Test
   public void testServiceLoader() throws Exception
   {
      JavaArchive archiveA = getArchiveA();

      ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());
      ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
      specBuilderA.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveA)));
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
   public void testLoadFromDependency() throws Exception
   {
      JavaArchive archiveA = getArchiveA();

      ModuleIdentifier identifierA = ModuleIdentifier.create(archiveA.getName());
      ModuleSpec.Builder specBuilderA = ModuleSpec.build(identifierA);
      PathFilter exportFilter = PathFilters.in(Collections.singleton("META-INF/services"));
      specBuilderA.addResourceRoot(new VirtualFileResourceLoader(toVirtualFile(archiveA)));
      specBuilderA.addDependency(DependencySpec.createLocalDependencySpec(PathFilters.acceptAll(), exportFilter));
      addModuleSpec(specBuilderA.create());

      ModuleIdentifier identifierB = ModuleIdentifier.create("moduleB");
      ModuleSpec.Builder specBuilderB = ModuleSpec.build(identifierB);
      specBuilderB.addDependency(DependencySpec.createModuleDependencySpec(identifierA));
      addModuleSpec(specBuilderB.create());

      Module moduleB = loadModule(identifierB);
      ModuleClassLoader classloader = moduleB.getClassLoader();

      URL resURL = classloader.getResource(resName);
      assertNotNull("Resource found", resURL);

      ServiceLoader<Foo> serviceLoader = ServiceLoader.load(Foo.class);
      assertNotNull("ServiceLoader not null", serviceLoader);
      assertFalse("ServiceLoader no next", serviceLoader.iterator().hasNext());

      serviceLoader = ServiceLoader.load(Foo.class, classloader);
      assertNotNull("ServiceLoader not null", serviceLoader);
      assertTrue("ServiceLoader next", serviceLoader.iterator().hasNext());
   }

   private JavaArchive getArchiveA()
   {
      JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
      archive.addClasses(Foo.class);
      archive.addResource("modules/" + resName, resName);
      return archive;
   }
}
