package org.jboss.test.osgi.framework.url;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
public class CustomURLHandlerTestCase extends OSGiFrameworkTest {
    @Test
    public void testInstallBundleCustomURLHandler() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getBundleArchive().as(ZipExporter.class).exportTo(baos);

        URLStreamHandlerService protocolService = new TestURLStreamHandlerService(baos.toByteArray());
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

        public TestURLStreamHandlerService(byte[] data) {
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

    private static JavaArchive getBundleArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "url-handler");
        archive.addClasses(ObjectZ.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addExportPackages(ObjectZ.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
