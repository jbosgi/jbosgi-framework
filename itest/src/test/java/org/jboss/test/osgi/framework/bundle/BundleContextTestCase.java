package org.jboss.test.osgi.framework.bundle;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import org.jboss.osgi.metadata.ManifestBuilder;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.SynchronousBundleListener;

/**
 * BundleContextTest.
 *
 * @author thomas.diesler@jboss.com
 */
public class BundleContextTestCase extends OSGiFrameworkTest {

    @Test
    public void testGetBundle() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveA());
        BundleContext context1 = null;
        try {
            bundle1.start();
            context1 = bundle1.getBundleContext();
            assertEquals(bundle1, context1.getBundle());
            assertEquals(bundle1, context1.getBundle(bundle1.getBundleId()));

            Bundle[] bundles = context1.getBundles();
            assertEquals(2, bundles.length);
            assertTrue(Arrays.asList(bundles).contains(bundle1));

            Bundle bundle2 = installBundle(getBundleArchiveB());
            BundleContext context2 = null;
            try {
                bundle2.start();
                context2 = bundle2.getBundleContext();
                assertEquals(bundle2, context2.getBundle());

                bundles = context1.getBundles();
                assertEquals(3, bundles.length);
                assertTrue(Arrays.asList(bundles).contains(bundle1));
                assertTrue(Arrays.asList(bundles).contains(bundle2));

                assertEquals(bundle1, context2.getBundle(bundle1.getBundleId()));
                assertEquals(bundle2, context1.getBundle(bundle2.getBundleId()));
            } finally {
                bundle2.uninstall();
            }

            assertEquals(bundle1, context1.getBundle(bundle1.getBundleId()));
            assertNull(context1.getBundle(bundle2.getBundleId()));

            bundles = context1.getBundles();
            assertEquals(2, bundles.length);
            assertTrue(Arrays.asList(bundles).contains(bundle1));

            try {
                context2.getBundle();
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }

            try {
                context2.getBundle(bundle1.getBundleId());
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }

            try {
                context2.getBundles();
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle1.uninstall();
        }

        try {
            context1.getBundle();
            fail("Should not be here!");
        } catch (IllegalStateException t) {
            // expected
        }

        try {
            context1.getBundle(bundle1.getBundleId());
            fail("Should not be here!");
        } catch (IllegalStateException t) {
            // expected
        }

        try {
            context1.getBundles();
            fail("Should not be here!");
        } catch (IllegalStateException t) {
            // expected
        }
    }

    @Test
    public void testProperties() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);
            assertEquals("1.7", bundleContext.getProperty(Constants.FRAMEWORK_VERSION));
            assertEquals("jboss.org", bundleContext.getProperty(Constants.FRAMEWORK_VENDOR));
            assertEquals(Locale.getDefault().getLanguage(), bundleContext.getProperty(Constants.FRAMEWORK_LANGUAGE));
            assertSystemProperty(bundleContext, "os.name", Constants.FRAMEWORK_OS_NAME);
            String fwOSVersion = bundleContext.getProperty(Constants.FRAMEWORK_OS_VERSION);
            assertTrue(fwOSVersion.length() > 0);
            assertTrue(System.getProperty("os.version").startsWith(fwOSVersion));
            assertSystemProperty(bundleContext, "os.arch", Constants.FRAMEWORK_PROCESSOR);

            assertNull(bundleContext.getProperty(getClass().getName()));
            System.setProperty(getClass().getName(), "test");
            assertEquals("test", bundleContext.getProperty(getClass().getName()));

            bundle.stop();
            try {
                bundleContext.getProperty(getClass().getName());
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testInstallBundle() throws Exception {
        // Test symbolic location
        JavaArchive archive = getBundleArchiveA();
        Bundle bundle = installBundle("/symbolic/location", toInputStream(archive));
        try {
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            assertEquals("/symbolic/location", bundle.getLocation());
        } finally {
            bundle.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testInstallEmptyManifest() throws Exception {

        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "empty-manifest");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                ManifestBuilder builder = ManifestBuilder.newInstance();
                return builder.openStream();
            }
        });

        Bundle bundle = installBundle(archive);
        try {
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            Dictionary headers = bundle.getHeaders();
            assertEquals(1, headers.size());
            assertEquals("1.0", headers.get("manifest-version"));
        } finally {
            bundle.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    public void testExecutionEnvironment() throws Exception {

        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "valid-execution-env");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addRequireExecutionEnvironment("JavaSE-1.6");
                return builder.openStream();
            }
        });

        Bundle bundle = installBundle(archive);
        try {
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            bundle.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    public void testInvalidExecutionEnvironment() throws Exception {

        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "invalid-execution-env");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addRequireExecutionEnvironment("UU/VV", "XX/YY");
                return builder.openStream();
            }
        });

        Bundle bundle = installBundle(archive);
        try {
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            try {
                bundle.start();
                fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
            assertBundleState(Bundle.INSTALLED, bundle.getState());
        } finally {
            bundle.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    public void testNullSymbolicName() throws Exception {

        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "null-symbolic-name");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleName(archive.getName());
                return builder.openStream();
            }
        });

        Bundle bundle = installBundle(archive);
        try {
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            assertNull("Null symbolic name", bundle.getSymbolicName());
            assertEquals("null-symbolic-name:0.0.0", bundle.toString());
            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            bundle.uninstall();
            assertBundleState(Bundle.UNINSTALLED, bundle.getState());
        }
    }

    @Test
    public void testServiceListener() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            try {
                bundleContext.addServiceListener(null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.addServiceListener(null, "(a=b)");
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.removeServiceListener(null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            bundleContext.addServiceListener(this);
            bundleContext = assertServiceLifecycle(bundle, true);
            bundleContext.removeServiceListener(this);

            bundleContext.addServiceListener(this);
            bundleContext.removeServiceListener(this);
            bundleContext = assertServiceLifecycle(bundle, false);

            bundleContext.addServiceListener(this);
            bundleContext.addServiceListener(this);
            bundleContext = assertServiceLifecycle(bundle, true);
            bundleContext.removeServiceListener(this);

            bundleContext.addServiceListener(this, null);
            bundleContext = assertServiceLifecycle(bundle, true);
            bundleContext.removeServiceListener(this);

            bundleContext.addServiceListener(this, null);
            bundleContext.removeServiceListener(this);
            bundleContext = assertServiceLifecycle(bundle, false);

            bundleContext.addServiceListener(this, null);
            bundleContext.addServiceListener(this, null);
            bundleContext = assertServiceLifecycle(bundle, true);
            bundleContext.removeServiceListener(this);

            Dictionary<String, Object> properties = new Hashtable<String, Object>();
            properties.put("a", "b");

            bundleContext.addServiceListener(this, ("(a=b)"));
            bundleContext = assertServiceLifecycle(bundle, properties, true);
            bundleContext.removeServiceListener(this);

            bundleContext.addServiceListener(this, ("(c=d)"));
            bundleContext = assertServiceLifecycle(bundle, properties, false);
            bundleContext.removeServiceListener(this);

            bundleContext.addServiceListener(this, "(a=b)");
            bundleContext.removeServiceListener(this);
            bundleContext = assertServiceLifecycle(bundle, properties, false);

            bundleContext.addServiceListener(this, "(c=d)");
            bundleContext.addServiceListener(this, "(a=b)");
            bundleContext = assertServiceLifecycle(bundle, properties, true);
            bundleContext.removeServiceListener(this);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testBundleListener() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            try {
                bundleContext.addBundleListener(null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.removeBundleListener(null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            bundle.stop();

            getSystemContext().addBundleListener(this);

            bundle.start();
            assertBundleEvent(BundleEvent.STARTING, bundle);
            assertBundleEvent(BundleEvent.STARTED, bundle);
            assertNoBundleEvent();

            bundle.stop();
            assertBundleEvent(BundleEvent.STOPPING, bundle);
            assertBundleEvent(BundleEvent.STOPPED, bundle);
            assertNoBundleEvent();

            getSystemContext().removeBundleListener(this);
            bundle.start();
            bundle.stop();
            assertNoBundleEvent();

            getSystemContext().addBundleListener(this);
            getSystemContext().addBundleListener(this);
            bundle.start();
            assertBundleEvent(BundleEvent.STARTING, bundle);
            assertBundleEvent(BundleEvent.STARTED, bundle);
            assertNoBundleEvent();

            bundle.stop();
            assertBundleEvent(BundleEvent.STOPPING, bundle);
            assertBundleEvent(BundleEvent.STOPPED, bundle);
            assertNoBundleEvent();

            bundle.start();
            assertBundleEvent(BundleEvent.STARTING, bundle);
            assertBundleEvent(BundleEvent.STARTED, bundle);
            assertNoBundleEvent();

            // [TODO] test asynch BundleListener
        } finally {
            bundle.uninstall();
        }
        assertBundleEvent(BundleEvent.STOPPING, bundle);
        assertBundleEvent(BundleEvent.STOPPED, bundle);
        assertBundleEvent(BundleEvent.UNINSTALLED, bundle);
        getSystemContext().removeBundleListener(this);
    }

    @Test
    public void testSynchronousBundleListener() throws Exception {
        final List<BundleEvent> events = new ArrayList<BundleEvent>();
        BundleListener listener = new SynchronousBundleListener() {
            @Override
            public void bundleChanged(BundleEvent event) {
                events.add(event);
            }
        };
        BundleContext context = getSystemContext();
        context.addBundleListener(listener);

        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            bundle.stop();
            bundle.update();
        } finally {
            bundle.uninstall();
        }

        assertEquals("Event count in: " + events, 10, events.size());
        assertEquals(BundleEvent.INSTALLED, events.get(0).getType());
        assertEquals(BundleEvent.RESOLVED, events.get(1).getType());
        assertEquals(BundleEvent.STARTING, events.get(2).getType());
        assertEquals(BundleEvent.STARTED, events.get(3).getType());
        assertEquals(BundleEvent.STOPPING, events.get(4).getType());
        assertEquals(BundleEvent.STOPPED, events.get(5).getType());
        assertEquals(BundleEvent.UNRESOLVED, events.get(6).getType());
        assertEquals(BundleEvent.UPDATED, events.get(7).getType());
        assertEquals(BundleEvent.UNRESOLVED, events.get(8).getType());
        assertEquals(BundleEvent.UNINSTALLED, events.get(9).getType());
    }

    @Test
    public void testFrameworkListener() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            try {
                bundleContext.addFrameworkListener(null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.removeFrameworkListener(null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            // todo test events
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetDataFile() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            File dataFile = bundleContext.getDataFile("blah");
            assertNotNull(dataFile);
            assertTrue(dataFile.toString().endsWith(File.separator + "blah"));
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testStopedBundleContext() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            // The context should be illegal to use.
            bundle.stop();
            try {
                bundleContext.getProperty(getClass().getName());
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }

            // The context should not become reusable after we restart the bundle
            bundle.start();
            try {
                bundleContext.getProperty(getClass().getName());
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
    }

    private void assertSystemProperty(BundleContext bundleContext, String property, String osgiProperty) {
        String expected = System.getProperty(property);
        assertNotNull(expected);
        assertEquals(expected, bundleContext.getProperty(osgiProperty));
    }

    private BundleContext assertServiceLifecycle(Bundle bundle, boolean events) throws Exception {
        return assertServiceLifecycle(bundle, null, events);
    }

    private BundleContext assertServiceLifecycle(Bundle bundle, Dictionary<String, Object> properties, boolean events) throws Exception {
        assertNoServiceEvent();

        BundleContext bundleContext = bundle.getBundleContext();
        ServiceRegistration registration = bundleContext.registerService(BundleContext.class.getName(), bundleContext, properties);
        ServiceReference reference = registration.getReference();

        if (events)
            assertServiceEvent(ServiceEvent.REGISTERED, reference);
        else
            assertNoServiceEvent();

        registration.setProperties(properties);
        if (events)
            assertServiceEvent(ServiceEvent.MODIFIED, reference);
        else
            assertNoServiceEvent();

        registration.unregister();
        if (events)
            assertServiceEvent(ServiceEvent.UNREGISTERING, reference);
        else
            assertNoServiceEvent();

        registration = bundleContext.registerService(BundleContext.class.getName(), bundleContext, properties);
        reference = registration.getReference();
        if (events)
            assertServiceEvent(ServiceEvent.REGISTERED, reference);
        else
            assertNoServiceEvent();

        bundle.stop();
        if (events)
            assertServiceEvent(ServiceEvent.UNREGISTERING, reference);
        else
            assertNoServiceEvent();

        try {
            bundleContext.addServiceListener(this);
            fail("Should not be here!");
        } catch (IllegalStateException t) {
            // expected
        }

        bundle.start();
        bundleContext = bundle.getBundleContext();
        assertNotNull(bundleContext);

        return bundleContext;
    }

    private JavaArchive getBundleArchiveA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple1");
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

    private JavaArchive getBundleArchiveB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple2");
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
