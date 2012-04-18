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
package org.jboss.osgi.framework.internal;

import org.jboss.modules.Module;
import org.junit.Before;
import org.junit.Test;
import org.kohsuke.args4j.CmdLineParser;
import org.omg.CORBA.ORB;
import org.osgi.framework.BundleActivator;

import javax.management.MBeanServer;
import java.util.HashMap;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test the bundle content loader.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class FrameworkClassLoaderTestCase extends AbstractFrameworkTest {

    ClassLoader classLoader;

    @Before
    public void before() throws Exception {
        BundleManagerPlugin bundleManager = getBundleManager();
        Module frameworkModule = bundleManager.getSystemBundle().getFrameworkModule();
        classLoader = frameworkModule.getClassLoader();
    }

    @Test
    public void testLoadJavaClass() throws Exception {
        Class<?> result = classLoader.loadClass(HashMap.class.getName());
        assertNotNull("HashMap loaded", result);
        assertTrue("Is assignable", HashMap.class.isAssignableFrom(result));
    }

    @Test
    public void testLoadJavaXSuccess() throws Exception {
        Class<?> result = classLoader.loadClass(MBeanServer.class.getName());
        assertNotNull("MBeanServer loaded", result);
        assertTrue("Is assignable", MBeanServer.class.isAssignableFrom(result));
    }

    @Test
    public void testLoadJavaXFail() throws Exception {
        try {
            classLoader.loadClass(ORB.class.getName());
            fail("ClassNotFoundException expected");
        } catch (ClassNotFoundException ex) {
            // expected
        }
    }

    @Test
    public void testLoadClassSuccess() throws Exception {
        Class<?> result = classLoader.loadClass(BundleActivator.class.getName());
        assertNotNull("BundleActivator loaded", result);
        assertTrue("Is assignable", BundleActivator.class.isAssignableFrom(result));
    }

    @Test
    public void testLoadClassFail() throws Exception {
        try {
            classLoader.loadClass(CmdLineParser.class.getName());
            fail("ClassNotFoundException expected");
        } catch (ClassNotFoundException ex) {
            // expected
        }
    }
}