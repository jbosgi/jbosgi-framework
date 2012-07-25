package org.jboss.test.osgi.framework.jbosgi477;
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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import java.io.InputStream;

import static org.junit.Assert.fail;

/**
 * [JBOSGI-477] Unsupported execution environment OSGi/Minimum-1.1
 *
 * https://jira.jboss.org/jira/browse/JBOSGI-477
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2011
 */
public class OSGi477TestCase extends OSGiFrameworkTest {

    @Test
    public void testOSGiMinimum() throws Exception {

        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi477-minimum11");
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addManifestHeader(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT, "OSGi/Minimum-1.1");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        bundleA.start();
        assertBundleState(Bundle.ACTIVE, bundleA.getState());

        bundleA.uninstall();
    }

    @Test
    public void testJavaSE16() throws Exception {

        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi477-javase16");
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addManifestHeader(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT, "JavaSE-1.6");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        bundleA.start();
        assertBundleState(Bundle.ACTIVE, bundleA.getState());

        bundleA.uninstall();
    }

    @Test
    public void testFail() throws Exception {

        final JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "jbosgi477-fail");
        archiveA.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archiveA.getName());
                builder.addManifestHeader(Constants.BUNDLE_REQUIREDEXECUTIONENVIRONMENT, "Bugus");
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        try {
            bundleA.start();
            fail("BundleException expected");
        } catch (BundleException e) {
            // expected
        }

        bundleA.uninstall();
    }
}
