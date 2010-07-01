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
package org.jboss.test.osgi.simple;

//$Id: SimpleBundleTestCase.java 102773 2010-03-23 11:38:05Z thomas.diesler@jboss.com $

import static org.junit.Assert.assertEquals;

import org.jboss.osgi.testing.OSGiBundle;
import org.jboss.osgi.testing.OSGiRuntime;
import org.jboss.osgi.testing.OSGiRuntimeTest;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * A test that deployes a bundle and verifies its state
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
@Ignore
public class SimpleRuntimeTestCase extends OSGiRuntimeTest
{
   @Test
   public void testBundleInstall() throws Exception
   {
      // Uses the JBossOSGi SPI provided runtime abstraction
      OSGiRuntime runtime = getDefaultRuntime();
      OSGiBundle bundle = runtime.installBundle("simple-bundle.jar");

      assertEquals("simple-bundle", bundle.getSymbolicName());

      bundle.start();
      assertEquals("Bundle state", Bundle.ACTIVE, bundle.getState());

      bundle.uninstall();
      assertEquals("Bundle state", Bundle.UNINSTALLED, bundle.getState());

      runtime.shutdown();
   }
}