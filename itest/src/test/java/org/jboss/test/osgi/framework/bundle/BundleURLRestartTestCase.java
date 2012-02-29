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
package org.jboss.test.osgi.framework.bundle;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;

/**
 * The bundle URLs
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Jan-2011
 */
public class BundleURLRestartTestCase extends OSGiFrameworkTest {

    @BeforeClass
    public static void beforeClass() {
        // prevent framework creation
    }

    @Test
    public void testGetEntry() throws Exception {
        createFramework().start();
        try {
            Bundle bundle = installBundle(getBundleArchive());

            URL url = bundle.getEntry("/resource-one.txt");
            assertEquals("/resource-one.txt", url.getPath());

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());
        } finally {
            shutdownFramework();
        }

        createFramework().start();
        try {
            Bundle bundle = installBundle(getBundleArchive());

            URL url = bundle.getEntry("/resource-one.txt");
            assertEquals("/resource-one.txt", url.getPath());

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
            assertEquals("resource-one", br.readLine());
        } finally {
            shutdownFramework();
        }
    }

    private JavaArchive getBundleArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive.addAsResource("bundles/simple/simple-bundle1/resource-one.txt", "resource-one.txt");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
