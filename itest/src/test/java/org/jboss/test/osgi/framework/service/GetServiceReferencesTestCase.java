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
import org.jboss.shrinkwrap.api.Archive;
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
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.io.InputStream;
import java.util.Dictionary;
import java.util.Hashtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * GetServiceReferencesTest.
 * 
 * todo test service permissions
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author Thomas.Diesler@jboss.com
 */
public class GetServiceReferencesTestCase extends OSGiFrameworkTest {

    @Test
    public void testGetServiceReferences() throws Exception {
        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context1 = bundle.getBundleContext();
            assertNotNull(context1);

            assertNoGetReference(context1, A.class.getName());
            assertNoReferences(context1, A.class.getName());
            assertNoAllReferences(context1, A.class.getName());
            assertNoGetReference(context1, B.class.getName());
            assertNoReferences(context1, B.class.getName());
            assertNoAllReferences(context1, B.class.getName());

            Class<?> clazz = bundle.loadClass(A.class.getName());
            Object service1 = clazz.newInstance();
            ServiceRegistration sreg1 = context1.registerService(A.class.getName(), service1, null);
            assertNotNull(sreg1);
            ServiceReference sref1 = sreg1.getReference();
            assertNotNull(sref1);

            assertGetReference(context1, A.class.getName(), sref1);
            assertReferences(context1, A.class.getName(), sref1);
            assertAllReferences(context1, A.class.getName(), sref1);
            assertNoGetReference(context1, B.class.getName());
            assertNoReferences(context1, B.class.getName());
            assertNoAllReferences(context1, B.class.getName());

            sreg1.unregister();

            assertNoGetReference(context1, A.class.getName());
            assertNoReferences(context1, A.class.getName());
            assertNoAllReferences(context1, A.class.getName());
            assertNoGetReference(context1, B.class.getName());
            assertNoReferences(context1, B.class.getName());
            assertNoAllReferences(context1, B.class.getName());

            try {
                context1.getServiceReference(null);
                fail("Should not be here!");
            } catch (IllegalArgumentException t) {
                // expected
            }

            try {
                context1.getServiceReferences(null, "invalid");
                fail("Should not be here!");
            } catch (InvalidSyntaxException t) {
                // expected
            }

            try {
                context1.getAllServiceReferences(null, "invalid");
                fail("Should not be here!");
            } catch (InvalidSyntaxException t) {
                // expected
            }

            bundle.stop();

            try {
                context1.getServiceReference(A.class.getName());
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }

            try {
                context1.getServiceReferences(null, null);
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }

            try {
                context1.getAllServiceReferences(null, null);
                fail("Should not be here!");
            } catch (IllegalStateException t) {
                // expected
            }
        } finally {
            bundle.uninstall();
        }
    }

    @Test
    public void testGetServiceReferencesNoWire() throws Exception {
        JavaArchive archiveA = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archiveA.addClass(A.class);
        archiveA.setManifest(new Asset() {

            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName("bundleA");
                builder.addExportPackages(A.class);
                return builder.openStream();
            }
        });

        Bundle bundleA = installBundle(archiveA);
        try {
            bundleA.start();
            BundleContext contextA = bundleA.getBundleContext();
            assertNotNull(contextA);

            Class<?> clazz = bundleA.loadClass(A.class.getName());
            Object service = clazz.newInstance();
            ServiceRegistration sreg1 = contextA.registerService(A.class.getName(), service, null);
            assertNotNull(sreg1);
            ServiceReference sref1 = sreg1.getReference();
            assertNotNull(sref1);

            BundleContext systemContext = getFramework().getBundleContext();
            ServiceReference sref = systemContext.getServiceReference(A.class.getName());
            assertNotNull("Reference not null", sref);
            assertEquals(sref1, sref);
            ServiceReference[] srefs = systemContext.getServiceReferences(A.class.getName(), null);
            assertEquals(1, srefs.length);
            assertEquals(sref1, srefs[0]);
            srefs = systemContext.getAllServiceReferences(A.class.getName(), null);
            assertEquals(1, srefs.length);
            assertEquals(sref1, srefs[0]);

            sref = contextA.getServiceReference(A.class.getName());
            assertNotNull("Reference not null", sref);
            assertEquals(sref1, sref);
            srefs = contextA.getServiceReferences(A.class.getName(), null);
            assertNotNull("References not null", srefs);
            assertEquals(1, srefs.length);
            assertEquals(sref1, srefs[0]);
            srefs = contextA.getAllServiceReferences(A.class.getName(), null);
            assertNotNull("References not null", srefs);
            assertEquals(1, srefs.length);
            assertEquals(sref1, srefs[0]);

            JavaArchive archiveB = ShrinkWrap.create(JavaArchive.class, "bundleB");
            archiveB.setManifest(new Asset() {

                public InputStream openStream() {
                    OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                    builder.addBundleManifestVersion(2);
                    builder.addBundleSymbolicName("bundleB");
                    return builder.openStream();
                }
            });
            Bundle bundleB = installBundle(archiveB);
            try {
                bundleB.start();
                BundleContext contextB = bundleB.getBundleContext();
                assertNotNull(contextB);

                assertLoadClassFail(bundleB, A.class.getName());

                // Verify that bundle B can see the service registered by bundle A
                // This is so because B does not have a wire to the service package
                // and can therefore not be constraint on this package.
                sref = contextB.getServiceReference(A.class.getName());
                assertNotNull("Reference not null", sref);
                srefs = contextB.getServiceReferences(A.class.getName(), null);
                assertNotNull("References not null", srefs);
                assertEquals(1, srefs.length);
                assertEquals(sref1, srefs[0]);
                srefs = contextB.getAllServiceReferences(A.class.getName(), null);
                assertNotNull("References not null", srefs);
                assertEquals(1, srefs.length);
                assertEquals(sref1, srefs[0]);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetServiceReferencesNoClassNotAssignable() throws Exception {
        assertGetServiceReferencesNotAssignable(null);
    }

    @Test
    public void testGetServiceReferencesNotAssignable() throws Exception {
        assertGetServiceReferencesNotAssignable(A.class.getName());
    }

    private void assertGetServiceReferencesNotAssignable(String className) throws Exception {
        Bundle bundleA = installBundle(getBundleArchiveA());
        try {
            bundleA.start();
            BundleContext contextA = bundleA.getBundleContext();
            assertNotNull(contextA);

            if (className != null)
                assertNoGetReference(contextA, className);

            Class<?> clazz = bundleA.loadClass(A.class.getName());
            Object service1 = clazz.newInstance();
            ServiceRegistration sreg1 = contextA.registerService(A.class.getName(), service1, null);
            assertNotNull(sreg1);
            ServiceReference sref1 = sreg1.getReference();
            assertNotNull(sref1);

            Bundle bundleB = installBundle(getBundleArchiveB());
            try {
                bundleB.start();
                BundleContext contextB = bundleB.getBundleContext();
                assertNotNull(contextB);

                if (className != null)
                    assertNoGetReference(contextB, className);

                clazz = bundleB.loadClass(A.class.getName());
                Object service2 = clazz.newInstance();
                ServiceRegistration sreg2 = contextB.registerService(A.class.getName(), service2, null);
                assertNotNull(sreg2);
                ServiceReference sref2 = sreg2.getReference();
                assertNotNull(sref2);

                if (className != null)
                    assertGetReference(contextA, className, sref1);

                if (className != null)
                    assertGetReference(contextB, className, sref2);

                sreg1.unregister();

                if (className != null)
                    assertNoGetReference(contextA, className);

                if (className != null)
                    assertGetReference(contextB, className, sref2);

                sreg1 = contextA.registerService(A.class.getName(), service1, null);
                assertNotNull(sreg1);
                sref1 = sreg1.getReference();
                assertNotNull(sref1);

                if (className != null)
                    assertGetReference(contextA, className, sref1);

                if (className != null)
                    assertGetReference(contextB, className, sref2);

                sreg2.unregister();

                if (className != null)
                    assertGetReference(contextA, className, sref1);

                if (className != null)
                    assertNoGetReference(contextB, className);

                sreg1.unregister();

                if (className != null)
                    assertNoGetReference(contextA, className);

                if (className != null)
                    assertNoGetReference(contextB, className);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetServiceReferencesNoClassAssignable() throws Exception {
        assertGetServiceReferencesAssignable(null);
    }

    @Test
    public void testGetServiceReferencesClassAssignable() throws Exception {
        assertGetServiceReferencesAssignable(A.class.getName());
    }

    private void assertGetServiceReferencesAssignable(String className) throws Exception {
        // Bundle-ManifestVersion: 2
        // Bundle-SymbolicName: org.jboss.test.osgi.service2
        // Export-Package: org.jboss.test.osgi.service.support.a
        Archive<?> assemblyA = assembleArchive("service2", "/bundles/service/service-bundle2", A.class);
        Bundle bundleA = installBundle(assemblyA);
        try {
            bundleA.start();
            BundleContext context1 = bundleA.getBundleContext();
            assertNotNull(context1);

            if (className != null)
                assertNoGetReference(context1, className);

            Class<?> clazz = bundleA.loadClass(A.class.getName());
            Object service1 = clazz.newInstance();
            ServiceRegistration sreg1 = context1.registerService(A.class.getName(), service1, null);
            assertNotNull(sreg1);
            ServiceReference sref1 = sreg1.getReference();
            assertNotNull(sref1);

            // Bundle-ManifestVersion: 2
            // Bundle-SymbolicName: org.jboss.test.osgi.service1
            // Import-Package: org.jboss.test.osgi.service.support.a
            Archive<?> assemblyB = assembleArchive("service1", "/bundles/service/service-bundle1");
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                BundleContext context2 = bundleB.getBundleContext();
                assertNotNull(context2);

                if (className != null)
                    assertGetReference(context2, className, sref1);

                clazz = bundleB.loadClass(A.class.getName());
                Object service2 = clazz.newInstance();
                ServiceRegistration sreg2 = context2.registerService(A.class.getName(), service2, null);
                assertNotNull(sreg2);
                ServiceReference sref2 = sreg2.getReference();
                assertNotNull(sref2);

                if (className != null)
                    assertGetReference(context1, className, sref1);

                if (className != null)
                    assertGetReference(context2, className, sref1);

                sreg1.unregister();

                if (className != null)
                    assertGetReference(context1, className, sref2);

                if (className != null)
                    assertGetReference(context2, className, sref2);

                sreg1 = context1.registerService(A.class.getName(), service1, null);
                assertNotNull(sreg1);
                sref1 = sreg1.getReference();
                assertNotNull(sref1);

                if (className != null)
                    assertGetReference(context1, className, sref2);

                if (className != null)
                    assertGetReference(context2, className, sref2);

                sreg2.unregister();

                if (className != null)
                    assertGetReference(context1, className, sref1);

                if (className != null)
                    assertGetReference(context2, className, sref1);

                sreg1.unregister();

                if (className != null)
                    assertNoGetReference(context1, className);

                if (className != null)
                    assertNoGetReference(context2, className);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetServiceReferencesRankings() throws Exception {
        String className = A.class.getName();

        // Bundle-ManifestVersion: 2
        // Bundle-SymbolicName: org.jboss.test.osgi.service2
        // Export-Package: org.jboss.test.osgi.service.support.a
        Archive<?> assemblyA = assembleArchive("service2", "/bundles/service/service-bundle2", A.class);
        Bundle bundleA = installBundle(assemblyA);
        try {
            bundleA.start();
            BundleContext context1 = bundleA.getBundleContext();
            assertNotNull(context1);

            assertNoGetReference(context1, className);
            assertNoReferences(context1, className);
            assertNoAllReferences(context1, className);

            Dictionary<String, Object> properties1 = new Hashtable<String, Object>();
            properties1.put(Constants.SERVICE_RANKING, new Integer(1));
            Class<?> clazz = bundleA.loadClass(className);
            Object service1 = clazz.newInstance();
            ServiceRegistration sreg1 = context1.registerService(className, service1, properties1);
            assertNotNull(sreg1);
            ServiceReference sref1 = sreg1.getReference();
            assertNotNull(sref1);

            // Bundle-ManifestVersion: 2
            // Bundle-SymbolicName: org.jboss.test.osgi.service1
            // Import-Package: org.jboss.test.osgi.service.support.a
            Archive<?> assemblyB = assembleArchive("service1", "/bundles/service/service-bundle1");
            Bundle bundleB = installBundle(assemblyB);
            try {
                bundleB.start();
                BundleContext context2 = bundleB.getBundleContext();
                assertNotNull(context2);

                assertGetReference(context2, className, sref1);
                assertReferences(context2, className, sref1);
                assertAllReferences(context2, className, sref1);

                Dictionary<String, Object> properties2 = new Hashtable<String, Object>();
                properties2.put(Constants.SERVICE_RANKING, new Integer(2));
                clazz = bundleB.loadClass(className);
                Object service2 = clazz.newInstance();
                ServiceRegistration sreg2 = context2.registerService(className, service2, properties2);
                assertNotNull(sreg2);
                ServiceReference sref2 = sreg2.getReference();
                assertNotNull(sref2);

                assertGetReference(context1, className, sref2);
                assertReferences(context1, className, sref1, sref2);
                assertAllReferences(context1, className, sref1, sref2);

                assertGetReference(context2, className, sref2);
                assertReferences(context2, className, sref1, sref2);
                assertAllReferences(context2, className, sref1, sref2);

                sreg1.unregister();

                assertGetReference(context1, className, sref2);
                assertReferences(context1, className, sref2);
                assertAllReferences(context1, className, sref2);

                assertGetReference(context2, className, sref2);
                assertReferences(context2, className, sref2);
                assertAllReferences(context2, className, sref2);

                sreg1 = context1.registerService(className, service1, properties1);
                assertNotNull(sreg1);
                sref1 = sreg1.getReference();
                assertNotNull(sref1);

                assertGetReference(context1, className, sref2);
                assertReferences(context1, className, sref1, sref2);
                assertAllReferences(context1, className, sref1, sref2);

                assertGetReference(context2, className, sref2);
                assertReferences(context2, className, sref1, sref2);
                assertAllReferences(context2, className, sref1, sref2);

                sreg2.unregister();

                assertGetReference(context1, className, sref1);
                assertReferences(context1, className, sref1);
                assertAllReferences(context1, className, sref1);

                assertGetReference(context2, className, sref1);
                assertReferences(context2, className, sref1);
                assertAllReferences(context2, className, sref1);

                sreg1.unregister();

                assertNoGetReference(context1, className);
                assertNoReferences(context1, className);
                assertNoAllReferences(context1, className);

                if (className != null)
                    assertNoGetReference(context2, className);
                assertNoReferences(context2, className);
                assertNoAllReferences(context2, className);
            } finally {
                bundleB.uninstall();
            }
        } finally {
            bundleA.uninstall();
        }
    }

    @Test
    public void testGetServiceReferencesFilterted() throws Exception {
        String className = A.class.getName();
        String wrongClassName = B.class.getName();

        Bundle bundle = installBundle(getBundleArchiveA());
        try {
            bundle.start();
            BundleContext context1 = bundle.getBundleContext();
            assertNotNull(context1);

            assertNoGetReference(context1, A.class.getName());
            assertNoReferences(context1, null, "(a=b)");
            assertNoAllReferences(context1, null, "(a=b)");
            assertNoReferences(context1, className, "(a=b)");
            assertNoAllReferences(context1, className, "(a=b)");
            assertNoReferences(context1, wrongClassName, "(a=b)");
            assertNoAllReferences(context1, wrongClassName, "(a=b)");
            assertNoReferences(context1, null, "(c=d)");
            assertNoAllReferences(context1, null, "(c=d)");
            assertNoReferences(context1, className, "(c=d)");
            assertNoAllReferences(context1, className, "(c=d)");
            assertNoReferences(context1, wrongClassName, "(c=d)");
            assertNoAllReferences(context1, wrongClassName, "(c=d)");
            assertNoReferences(context1, null, "(c=x)");
            assertNoAllReferences(context1, null, "(c=x)");
            assertNoReferences(context1, className, "(c=x)");
            assertNoAllReferences(context1, className, "(c=x)");
            assertNoReferences(context1, wrongClassName, "(c=x)");
            assertNoAllReferences(context1, wrongClassName, "(c=x)");
            assertNoReferences(context1, null, "(x=d)");
            assertNoAllReferences(context1, null, "(x=d)");
            assertNoReferences(context1, className, "(x=d)");
            assertNoAllReferences(context1, className, "(x=d)");
            assertNoReferences(context1, wrongClassName, "(x=d)");
            assertNoAllReferences(context1, wrongClassName, "(x=d)");

            Dictionary<String, Object> properties = new Hashtable<String, Object>();
            properties.put("a", "b");
            properties.put("c", "d");

            Class<?> clazz = bundle.loadClass(A.class.getName());
            Object service1 = clazz.newInstance();
            ServiceRegistration sreg1 = context1.registerService(A.class.getName(), service1, properties);
            assertNotNull(sreg1);
            ServiceReference sref1 = sreg1.getReference();
            assertNotNull(sref1);

            assertGetReference(context1, A.class.getName(), sref1);
            assertReferences(context1, null, "(a=b)", sref1);
            assertAllReferences(context1, null, "(a=b)", sref1);
            assertReferences(context1, className, "(a=b)", sref1);
            assertAllReferences(context1, className, "(a=b)", sref1);
            assertNoReferences(context1, wrongClassName, "(a=b)");
            assertNoAllReferences(context1, wrongClassName, "(a=b)");
            assertReferences(context1, null, "(c=d)", sref1);
            assertAllReferences(context1, null, "(c=d)", sref1);
            assertReferences(context1, className, "(c=d)", sref1);
            assertAllReferences(context1, className, "(c=d)", sref1);
            assertNoReferences(context1, wrongClassName, "(c=d)");
            assertNoAllReferences(context1, wrongClassName, "(c=d)");
            assertNoReferences(context1, null, "(c=x)");
            assertNoAllReferences(context1, null, "(c=x)");
            assertNoReferences(context1, className, "(c=x)");
            assertNoAllReferences(context1, className, "(c=x)");
            assertNoReferences(context1, wrongClassName, "(c=x)");
            assertNoAllReferences(context1, wrongClassName, "(c=x)");
            assertNoReferences(context1, null, "(x=d)");
            assertNoAllReferences(context1, null, "(x=d)");
            assertNoReferences(context1, className, "(x=d)");
            assertNoAllReferences(context1, className, "(x=d)");
            assertNoReferences(context1, wrongClassName, "(x=d)");
            assertNoAllReferences(context1, wrongClassName, "(x=d)");

            sreg1.unregister();

            assertNoGetReference(context1, A.class.getName());
            assertNoReferences(context1, null, "(a=b)");
            assertNoAllReferences(context1, null, "(a=b)");
            assertNoReferences(context1, className, "(a=b)");
            assertNoAllReferences(context1, className, "(a=b)");
            assertNoReferences(context1, wrongClassName, "(a=b)");
            assertNoAllReferences(context1, wrongClassName, "(a=b)");
            assertNoReferences(context1, null, "(c=d)");
            assertNoAllReferences(context1, null, "(c=d)");
            assertNoReferences(context1, className, "(c=d)");
            assertNoAllReferences(context1, className, "(c=d)");
            assertNoReferences(context1, wrongClassName, "(c=d)");
            assertNoAllReferences(context1, wrongClassName, "(c=d)");
            assertNoReferences(context1, null, "(c=x)");
            assertNoAllReferences(context1, null, "(c=x)");
            assertNoReferences(context1, className, "(c=x)");
            assertNoAllReferences(context1, className, "(c=x)");
            assertNoReferences(context1, wrongClassName, "(c=x)");
            assertNoAllReferences(context1, wrongClassName, "(c=x)");
            assertNoReferences(context1, null, "(x=d)");
            assertNoAllReferences(context1, null, "(x=d)");
            assertNoReferences(context1, className, "(x=d)");
            assertNoAllReferences(context1, className, "(x=d)");
            assertNoReferences(context1, wrongClassName, "(x=d)");
            assertNoAllReferences(context1, wrongClassName, "(x=d)");
        } finally {
            bundle.uninstall();
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
