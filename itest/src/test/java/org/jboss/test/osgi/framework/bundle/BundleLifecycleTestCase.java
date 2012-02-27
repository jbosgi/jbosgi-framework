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
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.framework.bundle.support.a.ActivatorA;
import org.jboss.test.osgi.framework.bundle.support.a.FailOnStartActivator;
import org.jboss.test.osgi.framework.bundle.support.a.ServiceA;
import org.jboss.test.osgi.framework.bundle.support.b.ActivatorB;
import org.jboss.test.osgi.framework.bundle.support.b.LifecycleService;
import org.jboss.test.osgi.framework.bundle.support.b.ServiceB;
import org.jboss.test.osgi.framework.bundle.support.x.ServiceX;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Bundle lifecycle TestCase.
 * 
 * Bundle A depends on B and X Bundle B depends on X
 * 
 * [JBOSGI-38] Investigate bundle install/start behaviour with random deployment order
 * https://jira.jboss.org/jira/browse/JBOSGI-38
 * 
 * @author thomas.diesler@jboss.com
 * @since 15-Dec-2009
 */
public class BundleLifecycleTestCase extends OSGiFrameworkTest {

    @Test
    public void testSimpleStart() throws Exception {
        Archive<?> assemblyA = assembleArchive("lifecycle-service", "/bundles/lifecycle/simple-service", LifecycleService.class);
        Bundle bundleA = installBundle(assemblyA);
        try {
            assertBundleState(Bundle.INSTALLED, bundleA.getState());

            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());

            ServiceReference sref = bundleA.getBundleContext().getServiceReference(LifecycleService.class.getName());
            assertNotNull("Service available", sref);
        } finally {
            bundleA.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
        }
    }

    /**
     * Verifies that the bundle state is RESOLVED after a failure in BundleActivator.start()
     */
    @Test
    public void testDependencyNotAvailable() throws Exception {
        Archive<?> assemblyA = assembleArchive("lifecycle-service", "/bundles/lifecycle/simple-service", LifecycleService.class);
        Bundle bundleA = installBundle(assemblyA);
        try {
            assertBundleState(Bundle.INSTALLED, bundleA.getState());

            // BundleA not started - service not available
            BundleContext systemContext = getFramework().getBundleContext();
            ServiceReference sref = systemContext.getServiceReference(LifecycleService.class.getName());
            assertNull("Service not available", sref);

            Archive<?> assemblyB = assembleArchive("lifecycle-failstart", "/bundles/lifecycle/fail-on-start", FailOnStartActivator.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                bundleB.start();
                fail("BundleException expected");
            } catch (BundleException ex) {
                assertBundleState(Bundle.RESOLVED, bundleB.getState());
            } finally {
                bundleB.uninstall();
                assertBundleState(Bundle.UNINSTALLED, bundleB.getState());
            }
        } finally {
            bundleA.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
        }
    }

    /**
     * Verifies that BundleB can get started when the service is available
     */
    @Test
    public void testDependencyAvailable() throws Exception {
        Archive<?> assemblyA = assembleArchive("lifecycle-service", "/bundles/lifecycle/simple-service", LifecycleService.class);
        Bundle bundleA = installBundle(assemblyA);
        try {
            bundleA.start();
            assertBundleState(Bundle.ACTIVE, bundleA.getState());

            Archive<?> assemblyB = assembleArchive("lifecycle-failstart", "/bundles/lifecycle/fail-on-start", FailOnStartActivator.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                assertBundleState(Bundle.ACTIVE, bundleB.getState());
            } finally {
                bundleB.uninstall();
                assertBundleState(Bundle.UNINSTALLED, bundleB.getState());
            }
        } finally {
            bundleA.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
        }
    }

    /**
     * Verifies that BundleB can get started when the service is made available
     */
    @Test
    public void testStartRetry() throws Exception {
        Archive<?> assemblyA = assembleArchive("lifecycle-service", "/bundles/lifecycle/simple-service", LifecycleService.class);
        Bundle bundleA = installBundle(assemblyA);
        try {
            assertBundleState(Bundle.INSTALLED, bundleA.getState());

            Archive<?> assemblyB = assembleArchive("lifecycle-failstart", "/bundles/lifecycle/fail-on-start", FailOnStartActivator.class);
            Bundle bundleB = installBundle(assemblyB);
            try {
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                try {
                    bundleB.start();
                    fail("BundleException expected");
                } catch (BundleException ex) {
                    assertBundleState(Bundle.RESOLVED, bundleB.getState());

                    // Now, make the service available
                    bundleA.start();
                    assertBundleState(Bundle.ACTIVE, bundleA.getState());
                }

                // BundleB can now be started
                bundleB.start();
                assertBundleState(Bundle.ACTIVE, bundleB.getState());
            } finally {
                bundleB.uninstall();
                assertBundleState(Bundle.UNINSTALLED, bundleB.getState());
            }
        } finally {
            bundleA.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleA.getState());
        }
    }

    /**
     * Verifies that BundleB is still INSTALLED after a failure in PackageAdmin.resolve()
     */
    @Test
    public void testFailToResolve() throws Exception {
        Archive<?> assemblyA = assembleArchive("lifecycle-failstart", "/bundles/lifecycle/fail-on-start", FailOnStartActivator.class);
        Bundle bundleB = installBundle(assemblyA);
        try {
            assertBundleState(Bundle.INSTALLED, bundleB.getState());

            // Get the PackageAdmin service
            BundleContext systemContext = getFramework().getBundleContext();
            ServiceReference sref = systemContext.getServiceReference(PackageAdmin.class.getName());
            PackageAdmin packageAdmin = (PackageAdmin) systemContext.getService(sref);

            // Attempt to explicitly resolve a bundle with missing dependency
            boolean allResolved = packageAdmin.resolveBundles(new Bundle[] { bundleB });
            assertFalse("Resolve fails", allResolved);

            // Verify that the bundle is still in state INSTALLED
            assertBundleState(Bundle.INSTALLED, bundleB.getState());
        } finally {
            bundleB.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleB.getState());
        }
    }

    /**
     * Verifies that we get a BundleException when an invalid bundle is installed
     */
    @Test
    public void testInstallInvalid() throws Exception {
        try {
            installBundle(assembleArchive("missing-symbolic-name", "/bundles/lifecycle/invalid01"));
            fail("BundleException expected");
        } catch (BundleException ex) {
            // expected
        }

        try {
            installBundle(assembleArchive("invalid-export", "/bundles/lifecycle/invalid02"));
            fail("BundleException expected");
        } catch (BundleException ex) {
            // expected
        }
    }

    @Test
    public void testInstallStartX() throws Exception {
        Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleX.getState());

            bundleX.start();
            assertBundleState(Bundle.ACTIVE, bundleX.getState());
        } finally {
            bundleX.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundleX.getState());
        }
    }

    @Test
    public void testInstallXBeforeB() throws Exception {
        // Install X, B

        Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleX.getState());

            Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
            try {
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                bundleB.start();
                assertBundleState(Bundle.RESOLVED, bundleX.getState());
                assertBundleState(Bundle.ACTIVE, bundleB.getState());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleX.uninstall();
        }
    }

    @Test
    public void testInstallBBeforeA() throws Exception {
        // Install X, B, A

        Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleX.getState());

            Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
            try {
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                Bundle bundleA = installBundle(assembleArchive("lifecycle-bundleA", "/bundles/lifecycle/bundleA", ActivatorA.class, ServiceA.class));
                try {
                    assertBundleState(Bundle.INSTALLED, bundleA.getState());

                    bundleA.start();
                    assertBundleState(Bundle.RESOLVED, bundleX.getState());
                    assertBundleState(Bundle.RESOLVED, bundleB.getState());
                    assertBundleState(Bundle.ACTIVE, bundleA.getState());
                } finally {
                    bundleA.uninstall();
                }
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleX.uninstall();
        }
    }

    @Test
    public void testInstallBBeforeX() throws Exception {
        // Install B, X

        Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleB.getState());

            try {
                bundleB.start();
                fail("Unresolved constraint expected");
            } catch (BundleException ex) {
                // expected
            }

            Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
            try {
                assertBundleState(Bundle.INSTALLED, bundleX.getState());

                bundleB.start();
                assertBundleState(Bundle.RESOLVED, bundleX.getState());
                assertBundleState(Bundle.ACTIVE, bundleB.getState());
            } finally {
                bundleX.uninstall();
            }
        } finally {
            bundleB.uninstall();
        }
    }

    @Test
    public void testInstallABeforeB() throws Exception {
        // Install A, B, X

        Bundle bundleA = installBundle(assembleArchive("lifecycle-bundleA", "/bundles/lifecycle/bundleA", ActivatorA.class, ServiceA.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleA.getState());

            Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
            try {
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                try {
                    bundleB.start();
                    fail("Unresolved constraint expected");
                } catch (BundleException ex) {
                    // expected
                }

                Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
                try {
                    assertBundleState(Bundle.INSTALLED, bundleX.getState());

                    bundleB.start();
                    assertBundleState(Bundle.RESOLVED, bundleX.getState());
                    assertBundleState(Bundle.ACTIVE, bundleB.getState());

                    bundleA.start();
                    assertBundleState(Bundle.ACTIVE, bundleA.getState());
                } finally {
                    bundleX.uninstall();
                }
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testUninstallX() throws Exception {
        // Uninstall X, B stays active

        Bundle bundleX = installBundle(assembleArchive("lifecycle-bundleX", "/bundles/lifecycle/bundleX", ServiceX.class));
        try {
            assertBundleState(Bundle.INSTALLED, bundleX.getState());

            Bundle bundleB = installBundle(assembleArchive("lifecycle-bundleB", "/bundles/lifecycle/bundleB", ActivatorB.class, ServiceB.class));
            try {
                assertBundleState(Bundle.INSTALLED, bundleB.getState());

                bundleB.start();
                assertBundleState(Bundle.RESOLVED, bundleX.getState());
                assertBundleState(Bundle.ACTIVE, bundleB.getState());

                bundleX.uninstall();
                assertBundleState(Bundle.UNINSTALLED, bundleX.getState());

                assertBundleState(Bundle.ACTIVE, bundleB.getState());
            } finally {
                bundleB.uninstall();
            }
        } finally {
            if (Bundle.UNINSTALLED != bundleX.getState())
                bundleX.uninstall();
        }
    }
}
