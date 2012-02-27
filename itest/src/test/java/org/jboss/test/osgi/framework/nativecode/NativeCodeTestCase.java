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
package org.jboss.test.osgi.framework.nativecode;

import org.jboss.osgi.spi.framework.OSGiBootstrap;
import org.jboss.osgi.spi.framework.OSGiBootstrapProvider;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;

import java.util.Properties;

import static org.junit.Assert.fail;

/**
 * Test NativeCode-Library functionality
 * 
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 21-Jan-2010
 */
public class NativeCodeTestCase extends OSGiFrameworkTest {

    @Test
    public void testNativeCode() throws Exception {
        BundleContext context = getFramework().getBundleContext();

        Bundle bundleA = context.installBundle(getTestArchivePath("simple-nativecode.jar"));
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        bundleA.start();
        assertBundleState(Bundle.ACTIVE, bundleA.getState());

        bundleA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
    }

    @Test
    public void testNativeCodeFilterFails() throws Exception {
        BundleContext context = getFramework().getBundleContext();

        Bundle bundleA = context.installBundle(getTestArchivePath("nativecode.jar"));
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        try {
            bundleA.start();
            fail("Bundle's native code filter");
        } catch (BundleException be) {
            // good
        }

        bundleA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
    }

    @Test
    public void testNativeCodeFilterSucceeds() throws Exception {
        Properties oldProps = new Properties();
        oldProps.putAll(System.getProperties());

        try {
            System.setProperty("org.jboss.test.osgi.framework.nativecode", "foo");
            BundleContext context = getFramework().getBundleContext();

            Bundle bundleA = context.installBundle(getTestArchivePath("nativecode2.jar"));
            assertBundleState(Bundle.INSTALLED, bundleA.getState());

            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());

            bundleA.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
        } finally {
            System.setProperties(oldProps);
        }
    }

    @Test
    public void testNativeCodeFilterOptional() throws Exception {
        BundleContext context = getFramework().getBundleContext();

        Bundle bundleA = context.installBundle(getTestArchivePath("nativecode3.jar"));
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        bundleA.start();
        assertBundleState(Bundle.ACTIVE, bundleA.getState());

        bundleA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
    }

    @Test
    public void testNativeCodeFragment() throws Exception {
        BundleContext context = getFramework().getBundleContext();

        Bundle fragment = context.installBundle(getTestArchivePath("fragment-nativecode.jar"));
        assertBundleState(Bundle.INSTALLED, fragment.getState());

        Bundle host = context.installBundle(getTestArchivePath("host-nativecode.jar"));
        assertBundleState(Bundle.INSTALLED, host.getState());

        host.start();
        assertBundleState(Bundle.ACTIVE, host.getState());
        assertBundleState(Bundle.RESOLVED, fragment.getState());

        host.uninstall();
        assertBundleState(Bundle.UNINSTALLED, host.getState());

        fragment.uninstall();
        assertBundleState(Bundle.UNINSTALLED, fragment.getState());
    }

    @Test
    public void testNativeCodeOSVersionFails() throws Exception {
        BundleContext context = getFramework().getBundleContext();

        Bundle bundleA = context.installBundle(getTestArchivePath("nativecode4.jar"));
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        try {
            bundleA.start();
            fail("Bundle's native code osversion");
        } catch (BundleException be) {
            // good
        }

        bundleA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
    }

    @Test
    public void testNativeCodeOSVersionSucceeds() throws Exception {
        Properties oldProps = new Properties();
        oldProps.putAll(System.getProperties());

        try {
            System.setProperty(Constants.FRAMEWORK_OS_VERSION, "999.2.3.beta3");

            OSGiBootstrapProvider bootProvider = OSGiBootstrap.getBootstrapProvider();
            Framework fw = bootProvider.getFramework();
            fw.start();

            BundleContext context = fw.getBundleContext();

            Bundle bundleA = context.installBundle(getTestArchivePath("nativecode4.jar"));
            assertBundleState(Bundle.INSTALLED, bundleA.getState());

            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());

            bundleA.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleA.getState());

            fw.stop();
            fw.waitForStop(2000);
        } finally {
            System.setProperties(oldProps);
        }
    }

    @Test
    public void testNativeCodeLanguageFails() throws Exception {
        BundleContext context = getFramework().getBundleContext();

        Bundle bundleA = context.installBundle(getTestArchivePath("nativecode5.jar"));
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        try {
            bundleA.start();
            fail("Bundle's native code language");
        } catch (BundleException be) {
            // good
        }

        bundleA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
    }
}