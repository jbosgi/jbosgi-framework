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
package org.jboss.test.osgi.framework.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.b.ServiceB;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * RegisterServiceTest.
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author <a href="ales.justin@jboss.org">Ales Justin</a>
 * @author Thomas.Diesler@jboss.com
 * @author David Bosschaert
 */
public class RegisterServiceTestCase extends OSGiFrameworkTest {

    static String OBJCLASS = BundleContext.class.getName();
    static String[] OBJCLASSES = new String[] { OBJCLASS };

    @Test
    public void testRegisterServiceErrors() throws Exception {
        String OBJCLASS = BundleContext.class.getName();
        String[] OBJCLASSES = new String[] { OBJCLASS };

        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            try {
                bundleContext.registerService((String) null, new Object(), null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.registerService((String[]) null, new Object(), null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.registerService(new String[0], new Object(), null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.registerService(OBJCLASS, null, null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.registerService(OBJCLASSES, null, null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.registerService(OBJCLASS, new Object(), null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.registerService(OBJCLASSES, new Object(), null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            Dictionary<String, Object> properties = new Hashtable<String, Object>();
            properties.put("case", "a");
            properties.put("CASE", "a");
            try {
                bundleContext.registerService(OBJCLASS, bundleContext, properties);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                bundleContext.registerService(OBJCLASSES, bundleContext, properties);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            bundle.stop();

            try {
                bundleContext.registerService(OBJCLASS, bundleContext, null);
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }

            try {
                bundleContext.registerService(OBJCLASSES, bundleContext, null);
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testRegisterServiceOBJCLASS() throws Exception {
        Dictionary<String, Object> properties = new Hashtable<String, Object>();
        properties.put(Constants.OBJECTCLASS, new String[] { "rubbish" });

        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            ServiceRegistration registration = bundleContext.registerService(OBJCLASS, bundleContext, null);
            ServiceReference reference = registration.getReference();
            assertObjectClass(OBJCLASS, reference);
            registration.setProperties(properties);
            assertObjectClass(OBJCLASS, reference);
            registration.unregister();

            registration = bundleContext.registerService(OBJCLASSES, bundleContext, null);
            reference = registration.getReference();
            assertObjectClass(OBJCLASSES, reference);
            registration.setProperties(properties);
            assertObjectClass(OBJCLASSES, reference);
            registration.unregister();
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testRegisterService() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            ServiceRegistration registration = bundleContext.registerService(OBJCLASS, bundleContext, null);
            ServiceReference reference = registration.getReference();
            Object actual = bundleContext.getService(reference);
            assertEquals(bundleContext, actual);

            registration = bundleContext.registerService(OBJCLASSES, bundleContext, null);
            reference = registration.getReference();
            actual = bundleContext.getService(reference);
            assertEquals(bundleContext, actual);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testBundleUninstall() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveA());
        try {
            bundle1.start();
            BundleContext bundleContext = bundle1.getBundleContext();
            assertNotNull(bundleContext);

            ServiceRegistration registration = bundleContext.registerService(OBJCLASS, bundleContext, null);
            ServiceReference reference = registration.getReference();
            Object actual = bundleContext.getService(reference);
            assertEquals(bundleContext, actual);

            Bundle bundle2 = installBundle(getBundleArchiveB());
            try {
                bundle2.start();
                BundleContext bundleContext2 = bundle2.getBundleContext();
                assertNotNull(bundleContext2);

                actual = bundleContext2.getService(reference);
                assertEquals(bundleContext, actual);
            } finally {
                bundle2.uninstall();
            }

            actual = bundleContext.getService(reference);
            assertEquals(bundleContext, actual);
        } finally {
            bundle1.uninstall();
        }
    }

    @Test
    public void testRegisteredServices() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveA());
        try {
            bundle1.start();
            BundleContext bundleContext = bundle1.getBundleContext();
            assertNotNull(bundleContext);

            ServiceRegistration registration = bundleContext.registerService(OBJCLASS, bundleContext, null);
            ServiceReference reference = registration.getReference();
            Object actual = bundleContext.getService(reference);
            assertEquals(bundleContext, actual);

            Bundle bundle2 = installBundle(getBundleArchiveB());
            try {
                bundle2.start();
                BundleContext bundleContext2 = bundle2.getBundleContext();
                assertNotNull(bundleContext2);

                actual = bundleContext2.getService(reference);
                assertEquals(bundleContext, actual);

                ServiceReference[] registered = bundle2.getRegisteredServices();
                assertNull(registered);

                registered = bundle1.getRegisteredServices();
                assertArrayEquals(new ServiceReference[]{reference}, registered);
            } finally {
                bundle2.uninstall();
            }

            actual = bundleContext.getService(reference);
            assertEquals(bundleContext, actual);
        } finally {
            bundle1.uninstall();
        }
    }

    @Test
    public void testRegisterServiceUnderBundleWithNoVisibilityOfServiceClass() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveA());
        Bundle bundle2 = installBundle(getBundleArchiveB());
        try {
            bundle1.start();
            bundle2.start();

            Class<?> serviceBClass = bundle2.loadClass(ServiceB.class.getName());
            Object serviceB = serviceBClass.newInstance();
            ServiceRegistration reg = bundle1.getBundleContext().registerService(ServiceB.class.getName(), serviceB, null);
            Object svc = bundle1.getBundleContext().getService(reg.getReference());
            assertSame(serviceB, svc);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundle2, ServiceB.class.getName()));
            assertTrue(ref.isAssignableTo(bundle1, ServiceB.class.getName()));
        } finally {
            bundle2.uninstall();
            bundle1.uninstall();
        }
    }

    @Test
    public void testRegisterServiceNullClassloader() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveA());
        Bundle bundle2 = installBundle(getBundleArchiveB());
        try {
            bundle1.start();
            bundle2.start();

            String service = "hello";
            ServiceRegistration reg = bundle1.getBundleContext().registerService(String.class.getName(), service, null);
            Object svc = bundle1.getBundleContext().getService(reg.getReference());
            assertSame(service, svc);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundle2, String.class.getName()));
            assertTrue(ref.isAssignableTo(bundle1, String.class.getName()));
        } finally {
            bundle2.uninstall();
            bundle1.uninstall();
        }
    }

    @Test
    public void testRegisterServiceNullClassloader2() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveA());
        Bundle bundle2 = installBundle(getBundleArchiveB());
        try {
            bundle1.start();
            bundle2.start();

            Integer service = new Integer(42);
            ServiceRegistration reg = bundle1.getBundleContext().registerService(Integer.class.getName(), service, null);
            Object svc = bundle1.getBundleContext().getService(reg.getReference());
            assertSame(service, svc);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundle2, Number.class.getName()));
            assertTrue(ref.isAssignableTo(bundle1, Number.class.getName()));
        } finally {
            bundle2.uninstall();
            bundle1.uninstall();
        }
    }

    @Test
    public void testRegisterServiceNotAssignable() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveC());
        Bundle bundle2 = installBundle(getBundleArchiveB());
        try {
            bundle1.start();
            bundle2.start();

            Class<?> service1Class = bundle1.loadClass(ServiceB.class.getName());
            Object service1 = service1Class.newInstance();
            ServiceRegistration reg1 = bundle1.getBundleContext().registerService(ServiceB.class.getName(), service1, null);
            Object svc1 = bundle1.getBundleContext().getService(reg1.getReference());
            assertSame(service1, svc1);

            Class<?> service2Class = bundle2.loadClass(ServiceB.class.getName());
            Object service2 = service2Class.newInstance();
            ServiceRegistration reg2 = bundle2.getBundleContext().registerService(ServiceB.class.getName(), service2, null);
            Object svc2 = bundle1.getBundleContext().getService(reg2.getReference());
            assertSame(service2, svc2);

            ServiceReference ref1 = reg1.getReference();
            assertFalse(ref1.isAssignableTo(bundle2, ServiceB.class.getName()));
            assertTrue(ref1.isAssignableTo(bundle1, ServiceB.class.getName()));

            ServiceReference ref2 = reg2.getReference();
            assertTrue(ref2.isAssignableTo(bundle2, ServiceB.class.getName()));
            assertFalse(ref2.isAssignableTo(bundle1, ServiceB.class.getName()));
        } finally {
            bundle2.uninstall();
            bundle1.uninstall();
        }
    }

    protected void assertObjectClass(String expected, ServiceReference reference) {
        assertObjectClass(new String[] { expected }, reference);
    }

    protected void assertObjectClass(String[] expected, ServiceReference reference) {
        Object actual = reference.getProperty(Constants.OBJECTCLASS);
        if (actual == null)
            fail("no object class???");
        if (actual instanceof String[] == false)
            fail(actual + " is not a string array??? " + actual.getClass().getName());
        assertArrayEquals(expected, (String[]) actual);
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
        archive.addClasses(ServiceB.class);
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

    private JavaArchive getBundleArchiveC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple3");
        archive.addClasses(ServiceB.class);
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
