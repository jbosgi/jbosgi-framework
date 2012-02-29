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
package org.jboss.test.osgi.framework.bundle.activation;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleActivator;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleService;
import org.jboss.test.osgi.framework.simple.bundleB.BeanB;
import org.jboss.test.osgi.framework.simple.bundleB.SimpleServiceImporter;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.startlevel.StartLevel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * BundleActivationTestCase
 * 
 * @author thomas.diesler@jboss.com
 */
public class BundleActivationTestCase extends OSGiFrameworkTest {

    private final List<BundleEvent> events = new ArrayList<BundleEvent>();

    @Test
    public void testLazyActivation() throws Exception {

        BundleContext context = getSystemContext();
        context.addBundleListener(new ActivationListener());

        Bundle providerBundle = installBundle(getLazyServiceProvider());
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            providerBundle.start(Bundle.START_ACTIVATION_POLICY);
            assertBundleState(Bundle.STARTING, providerBundle.getState());

            assertEquals(3, events.size());
            assertEquals(BundleEvent.INSTALLED, events.remove(0).getType());
            assertEquals(BundleEvent.RESOLVED, events.remove(0).getType());
            assertEquals(BundleEvent.LAZY_ACTIVATION, events.remove(0).getType());

            Class<?> serviceClass = providerBundle.loadClass(SimpleService.class.getName());
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            ServiceReference sref = context.getServiceReference(SimpleService.class.getName());
            assertNotNull("Service not null", sref);

            assertEquals(2, events.size());
            assertEquals(BundleEvent.STARTING, events.remove(0).getType());
            assertEquals(BundleEvent.STARTED, events.remove(0).getType());
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testLoadClassActivation() throws Exception {
        BundleContext context = getSystemContext();
        context.addBundleListener(new ActivationListener());

        Bundle providerBundle = installBundle(getLazyServiceProvider());
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            Class<?> serviceClass = providerBundle.loadClass(SimpleService.class.getName());
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            ServiceReference sref = context.getServiceReference(SimpleService.class.getName());
            assertNotNull("Service not null", sref);
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testLoadClassActivationWithInclude() throws Exception {
        BundleContext context = getSystemContext();
        context.addBundleListener(new ActivationListener());

        Bundle providerBundle = installBundle(getLazyServiceProviderWithInclude());
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            Class<?> serviceClass = providerBundle.loadClass(SimpleService.class.getName());
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            ServiceReference sref = context.getServiceReference(SimpleService.class.getName());
            assertNotNull("Service not null", sref);
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testLoadClassActivationWithExclude() throws Exception {
        BundleContext context = getSystemContext();
        context.addBundleListener(new ActivationListener());

        Bundle providerBundle = installBundle(getLazyServiceProviderWithExclude());
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            Class<?> serviceClass = providerBundle.loadClass(BeanB.class.getName());
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.RESOLVED, providerBundle.getState());

            ServiceReference sref = context.getServiceReference(SimpleService.class.getName());
            assertNull("Service null", sref);

            serviceClass = providerBundle.loadClass(SimpleService.class.getName());
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            sref = context.getServiceReference(SimpleService.class.getName());
            assertNotNull("Service not null", sref);
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testTransitiveActivation() throws Exception {
        BundleContext context = getSystemContext();
        context.addBundleListener(new ActivationListener());

        Bundle providerBundle = installBundle(getLazyServiceProvider());
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            Bundle consumerBundle = installBundle(getLazyServiceConsumer());
            try {
                assertBundleState(Bundle.INSTALLED, consumerBundle.getState());

                Class<?> clazz = consumerBundle.loadClass(SimpleServiceImporter.class.getName());
                Object obj = clazz.newInstance();
                assertNotNull("Instance not null", obj);

                assertBundleState(Bundle.ACTIVE, providerBundle.getState());
                assertBundleState(Bundle.RESOLVED, consumerBundle.getState());

                ServiceReference sref = context.getServiceReference(SimpleService.class.getName());
                assertNotNull("Service not null", sref);
            } finally {
                consumerBundle.uninstall();
            }
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testTransitiveStartWithStartLevel() throws Exception {
        BundleContext context = getSystemContext();
        context.addBundleListener(new ActivationListener());

        StartLevel startLevel = getStartLevel();
        int initialFrameworkSL = startLevel.getStartLevel();
        int initialBundleStartLevel = startLevel.getInitialBundleStartLevel();
        
        startLevel.setInitialBundleStartLevel(initialFrameworkSL + 10);
        try {
            Bundle providerBundle = installBundle(getLazyServiceProvider());
            try {
                assertBundleState(Bundle.INSTALLED, providerBundle.getState());
                
                providerBundle.start(Bundle.START_ACTIVATION_POLICY);
                assertBundleState(Bundle.INSTALLED, providerBundle.getState());

                context.addBundleListener(this);
                context.addFrameworkListener(this);
                
                // Crank up the framework start-level.  This should result in no STARTED event
                startLevel.setStartLevel(initialFrameworkSL + 15);
                assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, context.getBundle(), null);

                startLevel.setStartLevel(initialFrameworkSL);
                assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, context.getBundle(), null);
                
                assertBundleEvent(BundleEvent.RESOLVED, providerBundle);
                assertBundleEvent(BundleEvent.LAZY_ACTIVATION, providerBundle);
                assertBundleEvent(BundleEvent.STOPPING, providerBundle);
                assertBundleEvent(BundleEvent.STOPPED, providerBundle);
                assertNoBundleEvent();
                
                try {
                    providerBundle.start(Bundle.START_TRANSIENT);
                    fail("BundleException expected");
                } catch (BundleException ex) {
                    // expected
                }
                
                // Now call start(START_TRANSIENT) while start-level is met.
                startLevel.setStartLevel(initialFrameworkSL + 15);
                assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, context.getBundle(), null);
                
                providerBundle.start(Bundle.START_TRANSIENT);
                assertBundleState(Bundle.ACTIVE, providerBundle.getState());
                
                assertBundleEvent(BundleEvent.LAZY_ACTIVATION, providerBundle);
                assertBundleEvent(BundleEvent.STARTING, providerBundle);
                assertBundleEvent(BundleEvent.STARTED, providerBundle);
                assertNoBundleEvent();
                
                startLevel.setStartLevel(initialFrameworkSL);
                assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, context.getBundle(), null);
                
                assertBundleEvent(BundleEvent.STOPPING, providerBundle);
                assertBundleEvent(BundleEvent.STOPPED, providerBundle);
                assertNoBundleEvent();
                
                assertBundleState(Bundle.RESOLVED, providerBundle.getState());
            } finally {
                providerBundle.uninstall();
            }
        } finally {
            startLevel.setInitialBundleStartLevel(initialBundleStartLevel);
        }
    }

    class ActivationListener implements SynchronousBundleListener {

        @Override
        public void bundleChanged(BundleEvent event) {
            events.add(event);
        }
    }

    private static JavaArchive getLazyServiceProvider() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "lazy-service-provider");
        archive.addClasses(SimpleActivator.class, SimpleService.class, BeanB.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(SimpleActivator.class.getName());
                builder.addBundleActivationPolicy(Constants.ACTIVATION_LAZY);
                builder.addExportPackages(SimpleService.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private static JavaArchive getLazyServiceProviderWithInclude() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "lazy-service-provider-include");
        archive.addClasses(SimpleActivator.class, SimpleService.class, BeanB.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(SimpleActivator.class.getName());
                builder.addBundleActivationPolicy(Constants.ACTIVATION_LAZY + ";include:='" + SimpleService.class.getPackage().getName() + "'");
                builder.addExportPackages(SimpleService.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private static JavaArchive getLazyServiceProviderWithExclude() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "lazy-service-provider-exclude");
        archive.addClasses(SimpleActivator.class, SimpleService.class, BeanB.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(SimpleActivator.class.getName());
                builder.addBundleActivationPolicy(Constants.ACTIVATION_LAZY + ";exclude:='" + BeanB.class.getPackage().getName() + "'");
                builder.addExportPackages(SimpleService.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private static JavaArchive getLazyServiceConsumer() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "lazy-service-consumer");
        archive.addClasses(SimpleServiceImporter.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages(SimpleService.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
