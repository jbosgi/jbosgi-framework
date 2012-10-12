package org.jboss.osgi.framework.internal;
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;

import javax.management.MBeanServer;

import org.jboss.modules.Module;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.junit.Before;
import org.junit.Test;
import org.omg.CORBA.ORB;
import org.osgi.framework.BundleActivator;

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
        ModuleManagerPlugin moduleManager = getFrameworkState().getModuleManagerPlugin();
        Module frameworkModule = moduleManager.getFrameworkModule();
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
            classLoader.loadClass(ShrinkWrap.class.getName());
            fail("ClassNotFoundException expected");
        } catch (ClassNotFoundException ex) {
            // expected
        }
    }
}