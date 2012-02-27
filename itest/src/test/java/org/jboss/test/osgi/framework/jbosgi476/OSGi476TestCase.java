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
package org.jboss.test.osgi.framework.jbosgi476;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.Constants;

import java.io.InputStream;

import static org.junit.Assert.assertTrue;

/**
 * [JBOSGI-476] Cannot acquire start/stop lock for lazy bundles
 *
 * https://jira.jboss.org/jira/browse/JBOSGI-476
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2011
 */
public class OSGi476TestCase extends OSGiFrameworkTest {

    @Test
    public void testLazyActivation() throws Exception {

        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi476-bundle");
        archiveA.addClass(OSGi476Activator.class);
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addBundleActivationPolicy(Constants.ACTIVATION_LAZY);
                builder.addBundleActivator(OSGi476Activator.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        long before = System.currentTimeMillis();
        
        bundleA.start(Bundle.START_TRANSIENT);
        assertBundleState(Bundle.ACTIVE, bundleA.getState());
        
        long after = System.currentTimeMillis();
        assertTrue("Start not running into activation lock, should complete in < 2sec", (after - before) < 2000);

        bundleA.uninstall();
    }
}
