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
package org.jboss.test.osgi.framework.classloader;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

import org.jboss.osgi.framework.plugin.internal.BundleProtocolHandler;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.b.B;
import org.jboss.test.osgi.framework.classloader.support.c.CA;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * BundleClassPathTest.
 * 
 * @author thomas.diesler@jboss.com
 * @since 07-Oct-2009
 */
public class BundleClassPathTestCase extends OSGiFrameworkTest {

    @Test
    public void testBundleClassPath() throws Exception {
        URL bundleURL = getTestArchiveURL("bundle-classpath.war");
        Bundle bundle = installBundle(bundleURL.toExternalForm());
        try {
            assertLoadClass(bundle, A.class.getName(), bundle);
            assertLoadClass(bundle, B.class.getName(), bundle);
            assertLoadClass(bundle, CA.class.getName(), bundle);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testEntries() throws Exception {
        URL bundleURL = getTestArchiveURL("bundle-classpath.war");
        Bundle bundle = installBundle(bundleURL.toExternalForm());
        try {
            URL url = bundle.getEntry("message.txt");
            assertEquals(BundleProtocolHandler.getBundleURL(bundle, "message.txt"), url);

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("Hello from Resource", br.readLine());

            /*
             * url = bundle.getEntry("bundle-classpath-b.jar/bundleclasspath/message.txt"); assertEquals("bundle://jbosgi-" +
             * bundle.getBundleId() + "/message.txt", url.toExternalForm());
             * 
             * br = new BufferedReader(new InputStreamReader(url.openStream())); assertEquals("Hello from Resource",
             * br.readLine());
             */
        } finally {
            bundle.uninstall();
        }
    }
}
