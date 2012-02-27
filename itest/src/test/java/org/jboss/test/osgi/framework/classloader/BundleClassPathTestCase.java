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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.test.osgi.framework.classloader.support.a.A;
import org.jboss.test.osgi.framework.classloader.support.b.B;
import org.jboss.test.osgi.framework.classloader.support.c.CA;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * BundleClassPathTest.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
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
            URL jarURL = bundle.getResource("bundle-classpath-c.jar");
            byte[] jarBytes = suck(jarURL.openStream());
            assertTrue(jarBytes.length > 0);

            URL compareURL = getTestArchiveURL("bundle-classpath-c.jar");
            assertTrue("Precondition", !compareURL.equals(jarURL));
            URL compareClsURL = new URL("jar:" + compareURL + "!/org/jboss/test/osgi/framework/classloader/support/c/CA.class");
            byte[] compareClsBytes = suck(compareClsURL.openStream());
            assertTrue("precondition", compareClsBytes.length > 0);

            URL actClsURL = new URL("jar:" + jarURL + "!/org/jboss/test/osgi/framework/classloader/support/c/CA.class");
            byte[] actualClsBytes = suck(actClsURL.openStream());
            assertTrue(Arrays.equals(compareClsBytes, actualClsBytes));
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
