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
package org.jboss.osgi.framework.internal;

import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.url.AbstractURLStreamHandlerService;
import org.osgi.service.url.URLConstants;
import org.osgi.service.url.URLStreamHandlerService;
import org.osgi.service.url.URLStreamHandlerSetter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ContentHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.ServiceLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class URLHandlerTestCase extends OSGiFrameworkTest {

    @Test
    public void testURLHandling() throws Exception {
        URLStreamHandlerService protocol1Svc = new TestURLStreamHandlerService("test_protocol1");
        Dictionary<String, Object> props1 = new Hashtable<String, Object>();
        props1.put(URLConstants.URL_HANDLER_PROTOCOL, "protocol1");
        ServiceRegistration reg1 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), protocol1Svc, props1);

        URLStreamHandlerService protocol2Svc = new TestURLStreamHandlerService("test_protocol2");
        Dictionary<String, Object> props2 = new Hashtable<String, Object>();
        props2.put(URLConstants.URL_HANDLER_PROTOCOL, new String[] { "protocol2", "altprot2" });
        ServiceRegistration reg2 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), protocol2Svc, props2);

        URL url = new URL("protocol1://blah");
        assertEquals("test_protocol1blah", new String(suckStream(url.openStream())));

        URL url2 = new URL("protocol2://foo");
        assertEquals("test_protocol2foo", new String(suckStream(url2.openStream())));

        URL url3 = new URL("altprot2://bar");
        assertEquals("test_protocol2bar", new String(suckStream(url3.openStream())));

        try {
            new URL("protocol3://blahdiblah");
            fail("protocol3 is not registered so a URL containing this protocol should throw a MalformedURLException.");
        } catch (MalformedURLException mue) {
            // good
        }

        reg1.unregister();
        try {
            new URL("protocol1://foobar");
            fail("protocol1 is now unregistered so a URL containing this protocol should throw a MalformedURLException.");
        } catch (MalformedURLException mue) {
            // good
        }

        reg2.unregister();
    }

    @Test
    public void testServiceRanking() throws Exception {
        URLStreamHandlerService svc1 = new TestURLStreamHandlerService("tp1");
        Dictionary<String, Object> props1 = new Hashtable<String, Object>();
        props1.put(URLConstants.URL_HANDLER_PROTOCOL, "p1");
        props1.put(Constants.SERVICE_RANKING, 10);
        ServiceRegistration reg1 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), svc1, props1);

        URLStreamHandlerService svc2 = new TestURLStreamHandlerService("tp2");
        Dictionary<String, Object> props2 = new Hashtable<String, Object>();
        props2.put(URLConstants.URL_HANDLER_PROTOCOL, "p1");
        props2.put(Constants.SERVICE_RANKING, 15);
        ServiceRegistration reg2 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), svc2, props2);

        URLStreamHandlerService svc3 = new TestURLStreamHandlerService("tp3");
        Dictionary<String, Object> props3 = new Hashtable<String, Object>();
        props3.put(URLConstants.URL_HANDLER_PROTOCOL, "p1");
        props3.put(Constants.SERVICE_RANKING, 5);
        ServiceRegistration reg3 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), svc3, props3);

        URL url = new URL("p1://testing");
        assertEquals("tp2testing", new String(suckStream(url.openStream())));

        reg2.unregister();
        URL url2 = new URL("p1://testing");
        assertEquals("tp1testing", new String(suckStream(url2.openStream())));

        reg3.unregister();
        URL url3 = new URL("p1://testing");
        assertEquals("tp1testing", new String(suckStream(url3.openStream())));

        URLStreamHandlerService svc4 = new TestURLStreamHandlerService("tp4");
        Dictionary<String, Object> props4 = new Hashtable<String, Object>();
        props4.put(URLConstants.URL_HANDLER_PROTOCOL, "p1");
        props4.put(Constants.SERVICE_RANKING, 7);
        ServiceRegistration reg4 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), svc4, props4);

        URL url4 = new URL("p1://testing");
        assertEquals("tp1testing", new String(suckStream(url4.openStream())));

        URLStreamHandlerService svc5 = new TestURLStreamHandlerService("tp5");
        Dictionary<String, Object> props5 = new Hashtable<String, Object>();
        props5.put(URLConstants.URL_HANDLER_PROTOCOL, "p1");
        props5.put(Constants.SERVICE_RANKING, 11);
        ServiceRegistration reg5 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), svc5, props5);

        URL url5 = new URL("p1://testing");
        assertEquals("tp5testing", new String(suckStream(url5.openStream())));

        reg1.unregister();
        reg4.unregister();
        reg5.unregister();
    }

    @Test
    public void testContentHandler() throws Exception {
        URLStreamHandlerService svc1 = new TestURLStreamHandlerService("tp1", "foo/bar");
        Dictionary<String, Object> props1 = new Hashtable<String, Object>();
        props1.put(URLConstants.URL_HANDLER_PROTOCOL, "p1");
        props1.put(Constants.SERVICE_RANKING, 10);
        ServiceRegistration reg1 = getSystemContext().registerService(URLStreamHandlerService.class.getName(), svc1, props1);

        ContentHandler ch1 = new TestContentHandler("test_content");
        Dictionary<String, Object> chprops1 = new Hashtable<String, Object>();
        chprops1.put(URLConstants.URL_CONTENT_MIMETYPE, new String[] { "foo/bar" });
        ServiceRegistration reg2 = getSystemContext().registerService(ContentHandler.class.getName(), ch1, chprops1);

        URL url = new URL("p1://test");
        Object ob = url.getContent();
        assertEquals("Get content", "test_content", ob);

        reg2.unregister();
        reg1.unregister();
    }

    @Test
    public void testDelegateMethods() throws Exception {
        URLStreamHandlerService svc = new DelegationTestURLStreamHandlerService();
        Dictionary<String, Object> props = new Hashtable<String, Object>();
        props.put(URLConstants.URL_HANDLER_PROTOCOL, "jbossosgitest");
        ServiceRegistration reg = getSystemContext().registerService(URLStreamHandlerService.class.getName(), svc, props);

        URLStreamHandlerFactory factory = new OSGiStreamHandlerFactoryService();
        URLStreamHandler handler = factory.createURLStreamHandler("jbossosgitest");

        // Invoke methods through reflection to make sure the proxying works ok.
        Class<?> hcls = handler.getClass();
        Method defaultPortMethod = hcls.getDeclaredMethod("getDefaultPort");
        defaultPortMethod.setAccessible(true);
        assertEquals(979, defaultPortMethod.invoke(handler));

        Method equalsMethod = hcls.getDeclaredMethod("equals", URL.class, URL.class);
        equalsMethod.setAccessible(true);
        URL u1 = new URL("file://");
        URL u2 = new URL("http://127.0.0.1");
        assertTrue((Boolean) equalsMethod.invoke(handler, u1, u1));
        assertFalse("This special testing implementation returns false for every URL other than file:// even if they are the same",
                (Boolean) equalsMethod.invoke(handler, u2, u2));
        assertFalse((Boolean) equalsMethod.invoke(handler, u1, u2));

        Method hashCodeMethod = hcls.getDeclaredMethod("hashCode", URL.class);
        hashCodeMethod.setAccessible(true);
        assertEquals(327, hashCodeMethod.invoke(handler, u1));

        Method sameFileMethod = hcls.getDeclaredMethod("sameFile", URL.class, URL.class);
        sameFileMethod.setAccessible(true);
        assertTrue((Boolean) sameFileMethod.invoke(handler, u2, u2));
        assertFalse("This special testing implementation returns false for every URL other than http://127.0.0.1 even if they are the same",
                (Boolean) sameFileMethod.invoke(handler, u1, u1));
        assertFalse((Boolean) sameFileMethod.invoke(handler, u1, u2));

        Method hostAddressMethod = hcls.getDeclaredMethod("getHostAddress", URL.class);
        hostAddressMethod.setAccessible(true);
        assertEquals(InetAddress.getLocalHost(), hostAddressMethod.invoke(handler, u1));
        assertNull(hostAddressMethod.invoke(handler, u2));

        Method hostsEqualMethod = hcls.getDeclaredMethod("hostsEqual", URL.class, URL.class);
        hostsEqualMethod.setAccessible(true);
        URL u3 = new URL("http://0.0.0.0");
        assertTrue((Boolean) hostsEqualMethod.invoke(handler, u3, u3));
        assertFalse("This special testing implementation returns false for every URL other than http://0.0.0.0 even if they are the same",
                (Boolean) hostsEqualMethod.invoke(handler, u3, u2));
        assertFalse((Boolean) hostsEqualMethod.invoke(handler, u1, u1));

        Method toExternalFormMethod = hcls.getDeclaredMethod("toExternalForm", URL.class);
        toExternalFormMethod.setAccessible(true);
        assertEquals("elif", toExternalFormMethod.invoke(handler, u1));

        Method openConnection = hcls.getDeclaredMethod("openConnection", URL.class);
        openConnection.setAccessible(true);
        TestURLConnection tc1 = (TestURLConnection) openConnection.invoke(handler, u1);
        assertEquals(u1, tc1.getURL());
        assertNull(tc1.getProxy());

        Method openConnectionProxy = hcls.getDeclaredMethod("openConnection", URL.class, Proxy.class);
        openConnectionProxy.setAccessible(true);
        Proxy p = new Proxy(null, new InetSocketAddress(32767));
        TestURLConnection tc2 = (TestURLConnection) openConnectionProxy.invoke(handler, u2, p);
        assertEquals(u2, tc2.getURL());
        assertSame(p, tc2.getProxy());

        reg.unregister();
    }

    @Test
    public void testServiceLoader() throws Exception {
        ServiceLoader<URLStreamHandlerFactory> sl = ServiceLoader.load(URLStreamHandlerFactory.class, getSystemContext().getClass().getClassLoader());
        URLStreamHandlerFactory factory = null;
        for (URLStreamHandlerFactory f : sl) {
            if (f instanceof URLStreamHandlerFactory) {
                factory = f;
                break;
            }
        }
        Assert.assertNotNull("ServiceLoader should find our factory", factory);
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

    private static class TestURLConnection extends URLConnection {

        private final Proxy proxy;

        protected TestURLConnection(URL url, Proxy p) {
            super(url);
            proxy = p;
        }

        public Proxy getProxy() {
            return proxy;
        }

        @Override
        public void connect() throws IOException {
        }
    }

    private static class TestURLStreamHandlerService extends AbstractURLStreamHandlerService {

        private final String data;
        private final String contentType;

        public TestURLStreamHandlerService(String data) {
            this(data, null);
        }

        public TestURLStreamHandlerService(String data, String contentType) {
            this.data = data;
            this.contentType = contentType;
        }

        @Override
        public URLConnection openConnection(final URL u) throws IOException {
            return new URLConnection(u) {

                @Override
                public void connect() throws IOException {
                }

                @Override
                public String getContentType() {
                    return contentType;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    String content = data + u.getHost();
                    return new ByteArrayInputStream(content.getBytes());
                }
            };
        }
    }

    public class TestContentHandler extends ContentHandler {

        private final String data;

        public TestContentHandler(String data) {
            this.data = data;
        }

        @Override
        public Object getContent(URLConnection urlc) throws IOException {
            return data;
        }
    }

    // Testing implementation with funny behaviour which can be tested for
    public static class DelegationTestURLStreamHandlerService implements URLStreamHandlerService {

        @Override
        public URLConnection openConnection(URL u) throws IOException {
            return new TestURLConnection(u, null);
        }

        public URLConnection openConnection(URL u, Proxy p) throws IOException {
            return new TestURLConnection(u, p);
        }

        @Override
        public void parseURL(URLStreamHandlerSetter realHandler, URL u, String spec, int start, int limit) {
        }

        @Override
        public String toExternalForm(URL u) {
            return new StringBuffer(u.getProtocol()).reverse().toString();
        }

        @Override
        public boolean equals(URL u1, URL u2) {
            URL u = null;
            try {
                u = new URL("file://");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return u1.equals(u2) && u1.equals(u);
        }

        @Override
        public int getDefaultPort() {
            return 979;
        }

        @Override
        public InetAddress getHostAddress(URL u) {
            if (u.toString().startsWith("file:")) {
                try {
                    return InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        public int hashCode(URL u) {
            return 327;
        }

        @Override
        public boolean hostsEqual(URL u1, URL u2) {
            URL u = null;
            try {
                u = new URL("http://0.0.0.0");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return u1.equals(u2) && u1.equals(u);
        }

        @Override
        public boolean sameFile(URL u1, URL u2) {
            URL u = null;
            try {
                u = new URL("http://127.0.0.1");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return u1.equals(u2) && u1.equals(u);
        }
    }
}
