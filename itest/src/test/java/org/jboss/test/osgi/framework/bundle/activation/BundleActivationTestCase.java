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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jboss.arquillian.api.ArchiveProvider;
import org.jboss.arquillian.api.DeploymentProvider;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleActivator;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleService;
import org.jboss.test.osgi.framework.simple.bundleB.BeanB;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;

/**
 * BundleContextTest.
 * 
 * @author thomas.diesler@jboss.com
 */
@RunWith(Arquillian.class)
public class BundleActivationTestCase extends OSGiTest {

    private static final String LAZY_SERVICE_PROVIDER = "lazy-service-provider";
    private static final String LAZY_SERVICE_PROVIDER_INCLUDE = "lazy-service-provider-include";
    private static final String LAZY_SERVICE_PROVIDER_EXCLUDE = "lazy-service-provider-exclude";

    private static final String LAZY_SERVICE_CONSUMER = "lazy-service-consumer";

    @Inject
    public DeploymentProvider provider;

    @Inject
    public BundleContext context;

    private final List<BundleEvent> events = new ArrayList<BundleEvent>();

    @Test
    public void testLazyActivation() throws Exception {
        context.addBundleListener(new ActivationListener());

        InputStream providerArchive = provider.getClientDeploymentAsStream(LAZY_SERVICE_PROVIDER);
        Bundle providerBundle = context.installBundle(LAZY_SERVICE_PROVIDER, providerArchive);
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            providerBundle.start(Bundle.START_ACTIVATION_POLICY);
            assertBundleState(Bundle.STARTING, providerBundle.getState());

            assertEquals(3, events.size());
            assertEquals(BundleEvent.INSTALLED, events.remove(0).getType());
            assertEquals(BundleEvent.RESOLVED, events.remove(0).getType());
            assertEquals(BundleEvent.LAZY_ACTIVATION, events.remove(0).getType());

            Class<?> serviceClass = providerBundle.loadClass("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            ServiceReference sref = context.getServiceReference("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
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
        context.addBundleListener(new ActivationListener());

        InputStream providerArchive = provider.getClientDeploymentAsStream(LAZY_SERVICE_PROVIDER);
        Bundle providerBundle = context.installBundle(LAZY_SERVICE_PROVIDER, providerArchive);
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            Class<?> serviceClass = providerBundle.loadClass("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            ServiceReference sref = context.getServiceReference("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNotNull("Service not null", sref);
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testLoadClassActivationWithInclude() throws Exception {
        context.addBundleListener(new ActivationListener());

        InputStream providerArchive = provider.getClientDeploymentAsStream(LAZY_SERVICE_PROVIDER_INCLUDE);
        Bundle providerBundle = context.installBundle(LAZY_SERVICE_PROVIDER_INCLUDE, providerArchive);
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            Class<?> serviceClass = providerBundle.loadClass("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            ServiceReference sref = context.getServiceReference("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNotNull("Service not null", sref);
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testLoadClassActivationWithExclude() throws Exception {
        context.addBundleListener(new ActivationListener());

        InputStream providerArchive = provider.getClientDeploymentAsStream(LAZY_SERVICE_PROVIDER_EXCLUDE);
        Bundle providerBundle = context.installBundle(LAZY_SERVICE_PROVIDER_EXCLUDE, providerArchive);
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            Class<?> serviceClass = providerBundle.loadClass("org.jboss.test.osgi.framework.simple.bundleB.BeanB");
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.RESOLVED, providerBundle.getState());

            ServiceReference sref = context.getServiceReference("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNull("Service null", sref);

            serviceClass = providerBundle.loadClass("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNotNull("Service class not null", serviceClass);

            assertBundleState(Bundle.ACTIVE, providerBundle.getState());

            sref = context.getServiceReference("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
            assertNotNull("Service not null", sref);
        } finally {
            providerBundle.uninstall();
        }
    }

    @Test
    public void testTransitiveActivation() throws Exception {
        context.addBundleListener(new ActivationListener());

        InputStream providerArchive = provider.getClientDeploymentAsStream(LAZY_SERVICE_PROVIDER);
        Bundle providerBundle = context.installBundle(LAZY_SERVICE_PROVIDER, providerArchive);
        try {
            assertBundleState(Bundle.INSTALLED, providerBundle.getState());

            InputStream consumerArchive = provider.getClientDeploymentAsStream(LAZY_SERVICE_CONSUMER);
            Bundle consumerBundle = context.installBundle(LAZY_SERVICE_CONSUMER, consumerArchive);
            try {
                assertBundleState(Bundle.INSTALLED, consumerBundle.getState());

                Class<?> serviceClass = consumerBundle.loadClass("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
                assertNotNull("Service class not null", serviceClass);

                assertBundleState(Bundle.ACTIVE, providerBundle.getState());
                assertBundleState(Bundle.RESOLVED, consumerBundle.getState());

                ServiceReference sref = context.getServiceReference("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
                assertNotNull("Service not null", sref);
            } finally {
                consumerBundle.uninstall();
            }
        } finally {
            providerBundle.uninstall();
        }
    }

    class ActivationListener implements SynchronousBundleListener {

        @Override
        public void bundleChanged(BundleEvent event) {
            events.add(event);
        }
    }

    @ArchiveProvider
    public static JavaArchive getBundleArchive(final String archiveName) {
        if (LAZY_SERVICE_PROVIDER.equals(archiveName))
            return getLazyServiceProvider();
        if (LAZY_SERVICE_PROVIDER_INCLUDE.equals(archiveName))
            return getLazyServiceProviderWithInclude();
        if (LAZY_SERVICE_PROVIDER_EXCLUDE.equals(archiveName))
            return getLazyServiceProviderWithExclude();
        if (LAZY_SERVICE_CONSUMER.equals(archiveName))
            return getLazyServiceConsumer();
        return null;
    }

    private static JavaArchive getLazyServiceProvider() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, LAZY_SERVICE_PROVIDER);
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
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, LAZY_SERVICE_PROVIDER_INCLUDE);
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
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, LAZY_SERVICE_PROVIDER_EXCLUDE);
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
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, LAZY_SERVICE_CONSUMER);
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
