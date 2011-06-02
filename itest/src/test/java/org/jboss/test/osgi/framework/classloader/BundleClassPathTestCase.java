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
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;

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

            URL url = bundle.getEntry("message.txt");
            assertEquals("/message.txt", url.getPath());

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("Hello from Resource", br.readLine());
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetJarResource() throws Exception {
        URL bundleURL = getTestArchiveURL("bundle-classpath.war");
        Bundle bundle = installBundle(bundleURL.toExternalForm());
        try {
            URL compareURL = getTestArchiveURL("bundle-classpath-c.jar");
            byte[] compareBytes = suck(compareURL.openStream());
            assertTrue("Precondition", compareBytes.length > 0);

            URL jarURL = bundle.getResource("bundle-classpath-c.jar");
            byte[] actualBytes = suck(jarURL.openStream());
            assertTrue("Returned bytes should be the same", Arrays.equals(compareBytes, actualBytes));

            // TODO also check that the jar: url works on the URL returned
        } finally {
            bundle.uninstall();
        }
    }

    private static byte[] suck(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            byte[] bytes = new byte[8192];

            int length = 0;
            int offset = 0;

            while ((length = is.read(bytes, offset, bytes.length - offset)) != -1) {
                offset += length;

                if (offset == bytes.length) {
                    baos.write(bytes, 0, bytes.length);
                    offset = 0;
                }
            }
            if (offset != 0) {
                baos.write(bytes, 0, offset);
            }

            return baos.toByteArray();
        } finally {
            is.close();
        }
    }

}
