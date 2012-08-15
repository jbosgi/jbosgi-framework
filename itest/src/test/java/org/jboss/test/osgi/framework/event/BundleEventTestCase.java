package org.jboss.test.osgi.framework.event;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import junit.framework.Assert;

import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.SynchronousBundleListener;

/**
 * Test the bundle event use cases.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Aug-2012
 */
public class BundleEventTestCase extends OSGiFrameworkTest {

    @Test
    public void testSimpleResourceAccess() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            String line = getResourceContent(bundleA, "resources/simple.txt");
            Assert.assertEquals("hello world", line);
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testResourceAccessFromResolveEvent() throws Exception {
        final StringBuffer buffer = new StringBuffer();
        BundleListener listener = new SynchronousBundleListener() {

            @Override
            public void bundleChanged(BundleEvent event) {
                if (BundleEvent.RESOLVED == event.getType()) {
                    String line;
                    try {
                        line = getResourceContent(event.getBundle(), "resources/simple.txt");
                    } catch (IOException ex) {
                        line = ex.toString();
                    }
                    buffer.append(line);
                }
            }
        };
        BundleContext context = getSystemContext();
        context.addBundleListener(listener);

        Bundle bundleA = installBundle(getBundleA());
        try {
            bundleA.start();
            Assert.assertEquals("hello world", buffer.toString());
        } finally {
            bundleA.uninstall();
        }
    }

    private  String getResourceContent(Bundle bundle, String resource) throws IOException {
        URL url = bundle.getResource(resource);
        return new BufferedReader(new InputStreamReader(url.openStream())).readLine();
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.addAsResource(new StringAsset("hello world"), "resources/simple.txt");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                return builder.openStream();
            }
        });
        return archive;
    }
}