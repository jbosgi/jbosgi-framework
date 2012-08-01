package org.jboss.test.osgi.framework.jbosgi476;
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

import static org.junit.Assert.assertTrue;

import java.io.InputStream;

import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.Constants;

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
