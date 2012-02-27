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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.b.ActivatorB;
import org.jboss.test.osgi.framework.bundle.support.b.ServiceB;
import org.jboss.test.osgi.framework.bundle.support.x.ServiceX;
import org.junit.Test;
import org.osgi.framework.Bundle;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Bundle lifecycle TestCase.
 * 
 * Bundle B depends on X
 * 
 * @author thomas.diesler@jboss.com
 * @since 21-Sep-2010
 */
public class BundleReInstallTestCase extends OSGiFrameworkTest {

    @Test
    public void testSimpleReInstall() throws Exception {
        JavaArchive archiveX = assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class);
        Bundle bundleX = installBundle(archiveX);
        try {
            assertBundleState(Bundle.INSTALLED, bundleX.getState());

            bundleX.start();
            assertBundleState(Bundle.ACTIVE, bundleX.getState());

            bundleX.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

            bundleX = installBundle(archiveX);
            assertBundleState(Bundle.INSTALLED, bundleX.getState());
        } finally {
            bundleX.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleX.getState());
        }
    }

    @Test
    public void testUninstallWithWiredCapability() throws Exception {
        Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
        assertBundleState(Bundle.INSTALLED, bundleB.getState());

        Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        assertBundleState(Bundle.INSTALLED, bundleX.getState());

        bundleB.start();
        assertBundleState(Bundle.RESOLVED, bundleX.getState());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());

        long bid = bundleB.getBundleId();
        long xid = bundleX.getBundleId();

        bundleX.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

        // This is probably specific to our framework
        // We retain the UNINSTALLED bundle for as long as it has wires
        bundleX = getSystemContext().getBundle(xid);
        assertNotNull("BundleX still available", bundleX);
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

        bundleB.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

        // Uninstalling BundleB will also remove BundleX
        bundleX = getSystemContext().getBundle(xid);
        assertNull("BundleX not available", bundleX);

        // BundleB is removed because it has only wires to UNINSTALLED bundles
        bundleB = getSystemContext().getBundle(bid);
        assertNull("BundleB not available", bundleB);
    }

    @Test
    public void testUnInstallWithWiredRequirement() throws Exception {
        Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
        assertBundleState(Bundle.INSTALLED, bundleB.getState());

        Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        assertBundleState(Bundle.INSTALLED, bundleX.getState());

        bundleB.start();
        assertBundleState(Bundle.RESOLVED, bundleX.getState());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());

        long bid = bundleB.getBundleId();
        long xid = bundleX.getBundleId();

        bundleB.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleB.getState());

        // BundleB is removed because it does not have wired capabilities
        bundleB = getSystemContext().getBundle(bid);
        assertNull("BundleB not available", bundleB);

        bundleX.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

        bundleX = getSystemContext().getBundle(xid);
        assertNull("BundleX not available", bundleX);
    }

    @Test
    public void testReInstallWithWiredCapability() throws Exception {
        Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
        assertBundleState(Bundle.INSTALLED, bundleB.getState());

        Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        assertBundleState(Bundle.INSTALLED, bundleX.getState());

        bundleB.start();
        assertBundleState(Bundle.RESOLVED, bundleX.getState());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());

        Class<?> classXone = bundleB.loadClass(ServiceX.class.getName());
        assertNotNull("BundleB loads ServiceX", classXone);

        long xid = bundleX.getBundleId();

        bundleX.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

        bundleX = getSystemContext().getBundle(xid);
        assertNotNull("BundleX still available", bundleX);
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

        // Re-Install BundleX although it is still available
        bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        assertBundleState(Bundle.INSTALLED, bundleX.getState());

        Class<?> classXtwo = bundleB.loadClass(ServiceX.class.getName());
        assertNotNull("BundleB loads ServiceX", classXtwo);
        assertSame("Reload ServiceX from same ClassLoader", classXone, classXtwo);

        bundleX.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

        bundleB.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleX.getState());
    }
}
