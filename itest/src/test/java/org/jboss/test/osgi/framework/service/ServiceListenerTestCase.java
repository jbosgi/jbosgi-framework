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
import org.jboss.test.osgi.framework.service.support.a.A;
import org.jboss.test.osgi.framework.service.support.b.B;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test {@link ServiceListener} registration.
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Mar-2010
 */
public class ServiceListenerTestCase extends OSGiFrameworkTest {

    @Test
    public void testServiceListener() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);

            assertNoServiceEvent();
            context.addServiceListener(this);

            ServiceRegistration sreg = context.registerService(BundleContext.class.getName(), context, null);
            ServiceReference sref = sreg.getReference();

            assertServiceEvent(ServiceEvent.REGISTERED, sref);

            sreg.unregister();
            assertServiceEvent(ServiceEvent.UNREGISTERING, sref);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testObjectClassFilter() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);
            assertNoServiceEvent();

            String filter = "(" + Constants.OBJECTCLASS + "=" + BundleContext.class.getName() + ")";
            context.addServiceListener(this, filter);

            ServiceRegistration sreg = context.registerService(BundleContext.class.getName(), context, null);
            ServiceReference sref = sreg.getReference();

            assertServiceEvent(ServiceEvent.REGISTERED, sref);

            sreg.unregister();
            assertServiceEvent(ServiceEvent.UNREGISTERING, sref);

            filter = "(objectClass=dummy)";
            context.addServiceListener(this, filter);

            sreg = context.registerService(BundleContext.class.getName(), context, null);
            assertNoServiceEvent();

            sreg.unregister();
            assertNoServiceEvent();
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testModifyServiceProperties() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context = bundle.getBundleContext();
            assertNotNull(context);
            assertNoServiceEvent();

            String filter = "(&(objectClass=org.osgi.framework.BundleContext)(foo=bar))";
            context.addServiceListener(this, filter);

            Hashtable<String, Object> props = new Hashtable<String, Object>();
            props.put("foo", "bar");
            ServiceRegistration sreg = context.registerService(BundleContext.class.getName(), context, props);
            ServiceReference sref = sreg.getReference();

            assertServiceEvent(ServiceEvent.REGISTERED, sref);

            props.put("xxx", "yyy");
            sreg.setProperties(props);
            assertServiceEvent(ServiceEvent.MODIFIED, sref);

            props.put("foo", "notbar");
            sreg.setProperties(props);
            assertServiceEvent(ServiceEvent.MODIFIED_ENDMATCH, sref);

            props.put("foo", "bar");
            sreg.setProperties(props);
            assertServiceEvent(ServiceEvent.MODIFIED, sref);

            sreg.unregister();
            assertServiceEvent(ServiceEvent.UNREGISTERING, sref);
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testRegBundleIsRefBundle() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();

            final CountDownLatch latch = new CountDownLatch(1);
            ServiceListener listener = new ServiceListener() {
                public void serviceChanged(ServiceEvent event) {
                    latch.countDown();
                }
            };
            BundleContext context = bundle.getBundleContext();
            context.addServiceListener(listener);
                    
            Object service = bundle.loadClass(A.class.getName()).newInstance();
            ServiceRegistration reg = context.registerService(A.class.getName(), service, null);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundle, A.class.getName()));

            if (latch.await(5, TimeUnit.SECONDS) == false)
                throw new TimeoutException();

        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testRegValueIsServiceFactory() throws Exception {
        final Bundle bundleA = installBundle(getBundleArchiveA());
        final Bundle bundleB = installBundle(getBundleArchiveB());
        try {
            bundleA.start();
            bundleB.start();

            BundleContext contextA = bundleA.getBundleContext();
            BundleContext contextB = bundleB.getBundleContext();
            
            final CountDownLatch latch = new CountDownLatch(1);
            ServiceListener listener = new ServiceListener() {
                public void serviceChanged(ServiceEvent event) {
                    latch.countDown();
                }
            };
            contextB.addServiceListener(listener);

            Object service = new ServiceFactory() {
                public Object getService(Bundle bundle, ServiceRegistration registration) {
                    try {
                        return bundleA.loadClass(A.class.getName()).newInstance();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
                public void ungetService(Bundle bundle, ServiceRegistration registration, Object service) {
                }
            };
            ServiceRegistration reg = contextA.registerService(A.class.getName(), service, null);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundleA, A.class.getName()));
            assertTrue(ref.isAssignableTo(bundleB, A.class.getName()));

            if (latch.await(5, TimeUnit.SECONDS) == false)
                throw new TimeoutException();

        } finally {
            bundleA.uninstall();
            bundleB.uninstall();
        }
    }

    @Test
    public void testRefBundleCannotSeeServiceClass() throws Exception {
        final Bundle bundleA = installBundle(getBundleArchiveA());
        final Bundle bundleB = installBundle(getBundleArchiveB());
        try {
            bundleA.start();
            bundleB.start();

            BundleContext contextA = bundleA.getBundleContext();
            BundleContext contextB = bundleB.getBundleContext();

            final CountDownLatch latch = new CountDownLatch(1);
            ServiceListener listener = new ServiceListener() {
                public void serviceChanged(ServiceEvent event) {
                    latch.countDown();
                }
            };
            contextB.addServiceListener(listener);

            Object service = bundleA.loadClass(A.class.getName()).newInstance();
            ServiceRegistration reg = contextA.registerService(A.class.getName(), service, null);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundleA, A.class.getName()));
            assertTrue(ref.isAssignableTo(bundleB, A.class.getName()));

            if (latch.await(5, TimeUnit.SECONDS) == false)
                throw new TimeoutException();

        } finally {
            bundleA.uninstall();
            bundleB.uninstall();
        }
    }

    @Test
    public void testOwnerCannotSeeServiceClass() throws Exception {
        final Bundle bundleA = installBundle(getBundleArchiveA());
        final Bundle bundleB = installBundle(getBundleArchiveB());
        try {
            bundleA.start();
            bundleB.start();

            BundleContext contextA = bundleA.getBundleContext();
            BundleContext contextB = bundleB.getBundleContext();

            final CountDownLatch latch = new CountDownLatch(1);
            ServiceListener listener = new ServiceListener() {
                public void serviceChanged(ServiceEvent event) {
                    latch.countDown();
                }
            };
            contextB.addServiceListener(listener);

            Object service = bundleB.loadClass(B.class.getName()).newInstance();
            ServiceRegistration reg = contextA.registerService(B.class.getName(), service, null);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundleA, B.class.getName()));
            assertTrue(ref.isAssignableTo(bundleB, B.class.getName()));

            if (latch.await(5, TimeUnit.SECONDS) == false)
                throw new TimeoutException();

        } finally {
            bundleA.uninstall();
            bundleB.uninstall();
        }
    }

    @Test
    public void testOwnerAndRegBundleUseDifferentSource() throws Exception {
        final Bundle bundleA = installBundle(getBundleArchiveA());
        final Bundle bundleB = installBundle(getBundleArchiveC());
        try {
            bundleA.start();
            bundleB.start();

            BundleContext contextA = bundleA.getBundleContext();

            Object service = bundleA.loadClass(A.class.getName()).newInstance();
            ServiceRegistration reg = contextA.registerService(A.class.getName(), service, null);

            ServiceReference ref = reg.getReference();
            assertTrue(ref.isAssignableTo(bundleA, A.class.getName()));
            assertFalse(ref.isAssignableTo(bundleB, A.class.getName()));
        } finally {
            bundleA.uninstall();
            bundleB.uninstall();
        }
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
        archive.addClasses(B.class);
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
