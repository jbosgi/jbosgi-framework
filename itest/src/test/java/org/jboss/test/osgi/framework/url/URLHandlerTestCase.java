/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.test.osgi.framework.url;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.test.osgi.framework.bundle.support.z.ObjectZ;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;

/**
 * @author David Bosschaert
 */
public class URLHandlerTestCase extends OSGiFrameworkTest {
    @Test
    public void testInstallBundleCustomURLHandler() throws Exception {
        String path = getTestArchivePath("simple-bundle.jar");
        InputStream is = new FileInputStream(path);
        byte[] bytes = suckStream(is);

        URLStreamHandlerService protocolService = new TestURLStreamHandlerService(bytes);
        Dictionary<String, String> props = new Hashtable<String, String>();
        props.put(URLConstants.URL_HANDLER_PROTOCOL, "test");
        ServiceRegistration reg = getSystemContext().registerService(URLStreamHandlerService.class.getName(), protocolService, props);

        BundleContext context = getFramework().getBundleContext();
         Bundle bundle = context.installBundle("test:foobar");
        try {
            Assert.assertNotNull(bundle.loadClass(ObjectZ.class.getName()));
        } finally {
            reg.unregister();
            bundle.uninstall();
        }
    }

    private static class TestURLStreamHandlerService extends AbstractURLStreamHandlerService {
        private final byte[] data;

        public TestURLStreamHandlerService(byte [] data) {
            this.data = data;
        }

        @Override
        public URLConnection openConnection(final URL u) throws IOException {
            return new URLConnection(u) {

                @Override
                public void connect() throws IOException {
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(data);
                }
            };
        }
    }

    private static void pumpStream(InputStream is, OutputStream os) throws IOException {
        byte[] bytes = new byte[8192];

        int length = 0;
        int offset = 0;

        while ((length = is.read(bytes, offset, bytes.length - offset)) != -1) {
            offset += length;

            if (offset == bytes.length) {
                os.write(bytes, 0, bytes.length);
                offset = 0;
            }
        }
        if (offset != 0) {
            os.write(bytes, 0, offset);
        }
    }

    private static byte[] suckStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            pumpStream(is, baos);
            return baos.toByteArray();
        } finally {
            is.close();
        }
    }
}
