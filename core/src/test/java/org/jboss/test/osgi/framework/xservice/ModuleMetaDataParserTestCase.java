/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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
package org.jboss.test.osgi.framework.xservice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileReader;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.modules.ModuleMetaData;
import org.jboss.osgi.modules.ModuleMetaDataParser;
import org.jboss.osgi.modules.ModuleMetaData.Dependency;
import org.jboss.osgi.testing.OSGiTest;
import org.junit.Test;

/**
 * Test the {@link ModuleMetaDataParser}
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jul-2010
 */
public class ModuleMetaDataParserTestCase extends OSGiTest
{
   @Test
   public void testModuleA() throws Exception
   {
      File resFile = getResourceFile("xservice/moduleA/META-INF/jbosgi-xservice.properties");
      assertNotNull("File exists", resFile);

      ModuleMetaDataParser parser = new ModuleMetaDataParser();
      ModuleMetaData metadata = parser.parse(new FileReader(resFile));
      assertNotNull("ModuleMetaData exists", metadata);

      ModuleIdentifier identifier = metadata.getIdentifier();
      assertEquals("moduleA", identifier.getName());
      assertEquals("1.0", identifier.getSlot());

      Dependency[] dependencies = metadata.getDependencies();
      assertNotNull("Dependencies not null", dependencies);
      assertEquals(1, dependencies.length);

      Dependency dependency = dependencies[0];
      identifier = dependency.getIdentifier();
      assertEquals("system.bundle", identifier.getName());
      assertEquals("main", identifier.getSlot());

      String[] exportPaths = metadata.getExportPaths();
      assertNotNull("EportPaths not null", exportPaths);
      assertEquals(1, exportPaths.length);

      assertEquals("org/jboss/test/osgi/framework/xservice/moduleA", exportPaths[0]);
   }
}
