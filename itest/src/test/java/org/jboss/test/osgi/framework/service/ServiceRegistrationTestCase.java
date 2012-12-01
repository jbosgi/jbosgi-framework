package org.jboss.test.osgi.framework.service;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Hashtable;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.b.ServiceB;
import org.jboss.test.osgi.framework.service.support.SimpleServiceFactory;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * ServiceRegistrationTest.
 *
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author thomas.diesler@jboss.com
 */
public class ServiceRegistrationTestCase extends OSGiFrameworkTest {

    @Test
    public void testGetReference() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            ServiceRegistration registration = bundleContext.registerService(BundleContext.class.getName(), bundleContext, null);
            assertNotNull(registration);

            ServiceReference reference = registration.getReference();
            assertNotNull(reference);

            ServiceReference reference2 = bundleContext.getServiceReference(BundleContext.class.getName());
            assertEquals(reference, reference2);

            Object object = bundleContext.getService(reference);
            assertEquals(bundleContext, object);

            reference2 = registration.getReference();
            assertEquals(reference, reference2);

            registration.unregister();
            try {
                registration.getReference();
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }

            ServiceRegistration registration2 = bundleContext.registerService(BundleContext.class.getName(), bundleContext, null);
            assertNotNull(registration);
            assertNotSame(registration, registration2);

            reference2 = registration2.getReference();
            assertNotNull(reference2);
            assertNotSame(reference, reference2);

            bundle.stop();
            try {
                registration2.getReference();
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testSetProperties() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            String propertyA = "org.jboss.osgi.test.PropertyA";
            String propertyALower = "org.jboss.osgi.test.propertya";

            Hashtable<String, Object> properties = new Hashtable<String, Object>();
            properties.put(propertyA, "testA");
            ServiceRegistration registration = bundleContext.registerService(BundleContext.class.getName(), bundleContext, properties);
            assertNotNull(registration);
            ServiceReference reference = registration.getReference();
            assertNotNull(reference);
            assertEquals("testA", reference.getProperty(propertyA));
            assertEquals("testA", reference.getProperty(propertyALower));

            Object serviceID = reference.getProperty(Constants.SERVICE_ID);
            Object objectClass = reference.getProperty(Constants.OBJECTCLASS);

            assertAllReferences(bundleContext, null, "(" + propertyA + "=testA)", reference);
            assertAllReferences(bundleContext, null, "(" + propertyALower + "=testA)", reference);
            assertAllReferences(bundleContext, null, "(" + Constants.SERVICE_ID + "=" + serviceID + ")", reference);

            bundleContext.addServiceListener(this);

            properties = new Hashtable<String, Object>();
            properties.put(propertyA, "testAChanged");
            registration.setProperties(properties);
            assertServiceEvent(ServiceEvent.MODIFIED, reference);
            assertEquals("testAChanged", reference.getProperty(propertyA));
            assertNoAllReferences(bundleContext, null, "(" + propertyA + "=testA)");
            assertNoAllReferences(bundleContext, null, "(" + propertyALower + "=testA)");
            assertAllReferences(bundleContext, null, "(" + propertyA + "=testAChanged)", reference);
            assertAllReferences(bundleContext, null, "(" + propertyALower + "=testAChanged)", reference);

            registration.setProperties(null);
            assertServiceEvent(ServiceEvent.MODIFIED, reference);
            assertNull(reference.getProperty(propertyA));
            assertNoAllReferences(bundleContext, null, "(" + propertyA + "=testA)");
            assertNoAllReferences(bundleContext, null, "(" + propertyALower + "=testA)");
            assertNoAllReferences(bundleContext, null, "(" + propertyA + "=testAChanged)");
            assertNoAllReferences(bundleContext, null, "(" + propertyALower + "=testAChanged)");

            properties = new Hashtable<String, Object>();
            properties.put(propertyA, "testA2");
            properties.put(Constants.SERVICE_ID, "rubbish1");
            properties.put(Constants.OBJECTCLASS, "rubbish2");
            registration.setProperties(properties);
            assertServiceEvent(ServiceEvent.MODIFIED, reference);
            assertEquals("testA2", reference.getProperty(propertyA));
            assertEquals("testA2", reference.getProperty(propertyALower));
            assertEquals(serviceID, reference.getProperty(Constants.SERVICE_ID));
            assertEquals(serviceID, reference.getProperty(Constants.SERVICE_ID.toLowerCase()));
            assertEquals(objectClass, reference.getProperty(Constants.OBJECTCLASS));
            assertEquals(objectClass, reference.getProperty(Constants.OBJECTCLASS.toLowerCase()));

            assertNoAllReferences(bundleContext, null, "(" + Constants.SERVICE_ID + "=rubbish1)");

            assertAllReferences(bundleContext, null, "(" + Constants.SERVICE_ID + "=" + serviceID + ")", reference);

            properties = new Hashtable<String, Object>();
            properties.put("a", "1");
            properties.put("A", "2");
            try {
                registration.setProperties(properties);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }
            assertNoServiceEvent();

            registration.unregister();
            assertServiceEvent(ServiceEvent.UNREGISTERING, reference);

            try {
                registration.setProperties(new Hashtable<String, Object>());
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
            assertNoServiceEvent();
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testSetPropertiesAfterStop() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext bundleContext = bundle.getBundleContext();
            assertNotNull(bundleContext);

            ServiceRegistration registration = bundleContext.registerService(BundleContext.class.getName(), bundleContext, null);
            assertNotNull(registration);

            bundle.stop();

            try {
                registration.setProperties(new Hashtable<String, Object>());
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
            assertNoServiceEvent();
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testUnregister() throws Exception {
        Bundle bundle1 = installBundle(getBundleArchiveA());
        try {
            bundle1.start();
            BundleContext context1 = bundle1.getBundleContext();
            assertNotNull(context1);

            SimpleServiceFactory factory = new SimpleServiceFactory(context1, null);
            ServiceRegistration sreg1 = context1.registerService(BundleContext.class.getName(), factory, null);
            assertNotNull(sreg1);

            ServiceReference sref1 = sreg1.getReference();
            assertNotNull(sref1);

            ServiceReference sref2 = context1.getServiceReference(BundleContext.class.getName());
            assertEquals(sref1, sref2);

            ServiceReference[] inUse = bundle1.getServicesInUse();
            assertNull(inUse);

            context1.getService(sref1);
            inUse = bundle1.getServicesInUse();
            assertArrayEquals(new ServiceReference[] { sref1 }, inUse);

            Bundle bundle2 = installBundle(getBundleArchiveB());
            try {
                bundle2.start();
                BundleContext context2 = bundle2.getBundleContext();
                assertNotNull(context2);
                context2.getService(sref1);
                inUse = bundle2.getServicesInUse();
                assertArrayEquals(new ServiceReference[] { sref1 }, inUse);

                assertNull(factory.ungetBundle);
                assertNull(factory.ungetRegistration);
                assertNull(factory.ungetService);

                context1.addServiceListener(this);
                sreg1.unregister();

                sref2 = context1.getServiceReference(BundleContext.class.getName());
                assertNull("" + sref2, sref2);

                Object actual = context1.getService(sref1);
                assertNull("" + actual, actual);

                assertServiceEvent(ServiceEvent.UNREGISTERING, sref1);

                inUse = bundle1.getServicesInUse();
                assertNull(inUse);
                inUse = bundle2.getServicesInUse();
                assertNull(inUse);

                assertEquals(sreg1, factory.ungetRegistration);
                assertEquals(context1, factory.ungetService);
            } finally {
                bundle2.uninstall();
            }

            try {
                sreg1.unregister();
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle1.uninstall();
        }
    }

    @Test
    public void testUnregisterAfterStop() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);

            ServiceRegistration reg = context.registerService(BundleContext.class.getName(), context, null);
            assertNotNull(reg);

            bundle.stop();

            try {
                reg.unregister();
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
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
}
