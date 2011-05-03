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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.net.URL;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.service.log.LogService;

/**
 * Test the DynamicImport-Package manifest header.
 * 
 * @author thomas.diesler@jboss.com
 * @since 26-Mar-2010
 */
public class DynamicLogServiceTestCase extends OSGiFrameworkTest {

    @Test
    public void testPackageAvailableOnInstall() throws Exception {
        Bundle cmpd = installBundle("bundles/org.osgi.compendium.jar");
        assertBundleState(Bundle.INSTALLED, cmpd.getState());
        try {
            Bundle bundleC = installBundle(getLogServiceArchive());
            assertBundleState(Bundle.INSTALLED, bundleC.getState());
            try {
                bundleC.start();
                assertBundleState(Bundle.ACTIVE, bundleC.getState());
                assertLoadClass(bundleC, LogService.class.getName());
            } finally {
                bundleC.uninstall();
            }
        } finally {
            cmpd.uninstall();
        }
    }

    @Test
    public void testPackageNotAvailableOnInstall() throws Exception {
        Bundle bundleC = installBundle(getLogServiceArchive());
        assertBundleState(Bundle.INSTALLED, bundleC.getState());
        try {
            bundleC.start();
            assertBundleState(Bundle.ACTIVE, bundleC.getState());
            assertLoadClassFail(bundleC, LogService.class.getName());

            Bundle cmpd = installBundle("bundles/org.osgi.compendium.jar");
            try {
                assertLoadClass(bundleC, LogService.class.getName());
            } finally {
                cmpd.uninstall();
            }
        } finally {
            bundleC.uninstall();
        }
    }

    @Test
    public void testResourceAvailableOnInstall() throws Exception {
        Bundle cmpd = installBundle("bundles/org.osgi.compendium.jar");
        assertBundleState(Bundle.INSTALLED, cmpd.getState());
        try {
            Bundle bundleC = installBundle(getLogServiceArchive());
            assertBundleState(Bundle.INSTALLED, bundleC.getState());
            try {
                String resPath = LogService.class.getName().replace('.', '/') + ".class";
                URL resURL = bundleC.getResource(resPath);
                assertNotNull("Resource found", resURL);
                assertBundleState(Bundle.RESOLVED, bundleC.getState());
            } finally {
                bundleC.uninstall();
            }
        } finally {
            cmpd.uninstall();
        }
    }

    @Test
    public void testResourceNotAvailableOnInstall() throws Exception {
        Bundle bundleC = installBundle(getLogServiceArchive());
        assertBundleState(Bundle.INSTALLED, bundleC.getState());
        try {
            String resPath = LogService.class.getName().replace('.', '/') + ".class";
            URL resURL = bundleC.getResource(resPath);
            assertNull("Resource not found", resURL);
            assertBundleState(Bundle.RESOLVED, bundleC.getState());

            Bundle cmpd = installBundle("bundles/org.osgi.compendium.jar");
            try {
                resURL = bundleC.getResource(resPath);
                assertNotNull("Resource found", resURL);
            } finally {
                cmpd.uninstall();
            }
        } finally {
            bundleC.uninstall();
        }
    }

    private JavaArchive getLogServiceArchive() {
        
        // Bundle-SymbolicName: dynamic-log-service
        // DynamicImport-Package: org.osgi.service.log
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "dynamic-log-service");
        archive.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addDynamicImportPackages("org.osgi.service.log");
                return builder.openStream();
            }
        });
        return archive;
    }
}
