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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.service.support.SimpleServiceFactory;
import org.jboss.test.osgi.framework.service.support.a.A;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test {@link ServiceFactory} functionality.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author thomas.diesler@jboss.com
 */
public class ServiceFactoryTestCase extends OSGiFrameworkTest {

    static String OBJCLASS = BundleContext.class.getName();

    @Test
    public void testRegisterServiceFactory() throws Exception {
        Bundle bundleA = installBundle(getBundleArchiveA());
        try {
            bundleA.start();
            BundleContext contextA = bundleA.getBundleContext();
            assertNotNull(contextA);

            SimpleServiceFactory serviceFactory = new SimpleServiceFactory(contextA, null);
            ServiceRegistration sregA = contextA.registerService(OBJCLASS, serviceFactory, null);

            ServiceReference srefA = sregA.getReference();
            Object actual = contextA.getService(srefA);
            assertEquals(contextA, actual);
            assertEquals(bundleA.getSymbolicName(), serviceFactory.getBundle.getSymbolicName());
            assertEquals(1, serviceFactory.getCount);

            srefA = contextA.getServiceReference(OBJCLASS);
            actual = contextA.getService(srefA);
            assertEquals(contextA, actual);
            assertEquals(bundleA.getSymbolicName(), serviceFactory.getBundle.getSymbolicName());
            assertEquals(1, serviceFactory.getCount);

            Bundle bundleB = installBundle(getBundleArchiveB());
            try {
                bundleB.start();
                BundleContext contextB = bundleB.getBundleContext();
                assertNotNull(contextB);

                ServiceReference srefB = contextB.getServiceReference(OBJCLASS);
                actual = contextB.getService(srefB);
                assertEquals(contextA, actual);

                assertEquals(bundleB.getSymbolicName(), serviceFactory.getBundle.getSymbolicName());
                assertEquals(2, serviceFactory.getCount);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetServiceFactory() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);

            ServiceRegistration sreg = context.registerService(OBJCLASS, new SimpleServiceFactory(context, null), null);
            ServiceReference sref = sreg.getReference();

            Object actual = context.getService(sref);
            assertEquals(context, actual);

            actual = context.getService(sref);
            assertEquals(context, actual);

            assertTrue(context.ungetService(sref));
            assertTrue(context.ungetService(sref));
            assertFalse(context.ungetService(sref));

            sreg.unregister();
            actual = context.getService(sref);
            assertNull("Service null", actual);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetServiceException() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);

            context.addFrameworkListener(this);

            Throwable rte = new RuntimeException();
            ServiceRegistration sreg = context.registerService(OBJCLASS, new SimpleServiceFactory(context, rte), null);
            ServiceReference sref = sreg.getReference();

            Object actual = context.getService(sref);
            assertNull("service null", actual);
            sreg.unregister();

            assertFrameworkEvent(FrameworkEvent.ERROR, bundle, ServiceException.class);

            sreg = context.registerService(OBJCLASS, new SimpleServiceFactory(null, null), null);
            sref = sreg.getReference();

            actual = context.getService(sref);
            assertNull("service null", actual);
            sreg.unregister();

            assertFrameworkEvent(FrameworkEvent.ERROR, bundle, ServiceException.class);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetServiceFactoryAfterStop() throws Exception {
        String OBJCLASS = BundleContext.class.getName();
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);

            ServiceRegistration sreg = context.registerService(OBJCLASS, new SimpleServiceFactory(context, null), null);
            ServiceReference sref = sreg.getReference();

            Object actual = context.getService(sref);
            assertEquals(context, actual);

            bundle.stop();
            try {
                context.getService(sref);
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetWrongInterfacesForServiceFactory() throws Exception {
        String[] OBJCLASS = { String.class.getName() };
        String[] OBJCLASSES = { String.class.getName(), BundleContext.class.getName() };

        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);

            context.addFrameworkListener(this);

            SimpleServiceFactory factory = new SimpleServiceFactory(context, null);
            ServiceRegistration sreg = context.registerService(OBJCLASS, factory, null);
            ServiceReference sref = sreg.getReference();
            Object actual = context.getService(sref);
            assertNull("Service null", actual);
            assertFalse(context.ungetService(sref));
            assertEquals("ungetService() not called", 0, factory.ungetCount);

            assertFrameworkEvent(FrameworkEvent.ERROR, bundle, ServiceException.class);

            factory = new SimpleServiceFactory(context, null);
            sreg = context.registerService(OBJCLASSES, factory, null);
            sref = sreg.getReference();
            actual = context.getService(sref);
            assertNull("Service null", actual);
            assertFalse(context.ungetService(sref));
            assertEquals("ungetService() not called", 0, factory.ungetCount);

            assertFrameworkEvent(FrameworkEvent.ERROR, bundle, ServiceException.class);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testServiceFactoryUsingBundles() throws Exception {
        final boolean[] allGood = new boolean[2];
        ServiceFactory factory = new ServiceFactory() {

            @Override
            public Object getService(Bundle bundle, ServiceRegistration sreg) {
                ServiceReference sref = sreg.getReference();
                Bundle[] users = sref.getUsingBundles();
                assertNotNull("Users not null", users);
                assertEquals(1, users.length);
                assertEquals(bundle, users[0]);
                allGood[0] = true;
                return new Runnable() {

                    public void run() {
                    }
                };
            }

            @Override
            public void ungetService(Bundle bundle, ServiceRegistration sreg, Object service) {
                ServiceReference sref = sreg.getReference();
                Bundle[] users = sref.getUsingBundles();
                assertNotNull("Users not null", users);
                assertEquals(1, users.length);
                assertEquals(bundle, users[0]);
                allGood[1] = true;
            }
        };
        BundleContext context = getFramework().getBundleContext();
        ServiceRegistration sreg = context.registerService(Runnable.class.getName(), factory, null);
        ServiceReference sref = sreg.getReference();

        Bundle[] users = sref.getUsingBundles();
        assertNull("Null users", users);

        Runnable was = (Runnable) context.getService(sref);
        assertNotNull("Service not null", was);
        users = sref.getUsingBundles();
        assertNotNull("Users not null", users);
        assertEquals(1, users.length);
        assertEquals(context.getBundle(), users[0]);
        assertTrue("getService good", allGood[0]);

        sreg.unregister();

        was = (Runnable) context.getService(sref);
        assertNull("Service null", was);
        assertTrue("ungetService good", allGood[1]);
    }

    private JavaArchive getBundleArchiveA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple1");
        archive.addClasses(A.class);
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
        archive.addClasses(A.class);
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
