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

import java.io.File;
import java.io.FileReader;
import java.util.List;

import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleParser;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.testing.OSGiTest;
import org.jboss.test.osgi.framework.xservice.moduleA.ModuleServiceA;
import org.junit.Test;
import org.osgi.framework.Version;

/**
 * Test the {@link XModuleParser}
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jul-2010
 */
public class XModuleParserTestCase extends OSGiTest
{
   @Test
   public void testModuleA() throws Exception
   {
      File resFile = getResourceFile("xservice/moduleA/META-INF/jbosgi-xservice.properties");
      assertNotNull("File exists", resFile);

      XModuleParser parser = XModuleParser.newInstance();
      XModule resModule = parser.parse(new FileReader(resFile));
      assertNotNull("XModule exists", resModule);

      assertEquals("moduleA", resModule.getName());
      assertEquals(Version.parseVersion("1.0"), resModule.getVersion());

      List<XPackageCapability> packageCaps = resModule.getPackageCapabilities();
      assertNotNull("Export-Package not null", packageCaps);
      assertEquals(1, packageCaps.size());
      XPackageCapability packageCap = packageCaps.get(0);
      assertEquals(ModuleServiceA.class.getPackage().getName(), packageCap.getName());
   }
}
