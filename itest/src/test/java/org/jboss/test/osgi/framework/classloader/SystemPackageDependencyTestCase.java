/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.test.osgi.framework.classloader;

import org.jboss.logging.Logger;
import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.LoggingActivator;
import org.jboss.test.osgi.framework.classloader.support.SimpleManagementActivator;
import org.jboss.test.osgi.framework.classloader.support.XMLParserActivatorExt;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleActivator;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleService;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.util.xml.XMLParserActivator;

import javax.management.MBeanServer;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Test requirements on the system bundle
 *
 * @author thomas.diesler@jboss.com
 * @since 01-Feb-2011
 */
public class SystemPackageDependencyTestCase extends OSGiTest {

    @Test
    public void testNoFrameworkImport() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleA();
        try {
            // Invoke a simple activator, do not import the org.osgi.framework package
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            try {
                bundle.start();
                fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
            assertBundleState(Bundle.RESOLVED, bundle.getState());
        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testFrameworkImport() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleB();
        try {
            // Invoke a simple activator, import the org.osgi.framework package
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testNoLoggingImport() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleC();
        try {
            // Invoke a simple logging activator, import the org.osgi.framework package, but not the org.jboss.logging package
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            try {
                bundle.start();
                fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
            assertBundleState(Bundle.RESOLVED, bundle.getState());
            bundle.uninstall();
        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testLoggingImportFail() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleD();
        try {
            // Invoke a simple logging activator, import the org.osgi.framework package and the org.jboss.logging package
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            try {
                bundle.start();
                fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
            assertBundleState(Bundle.INSTALLED, bundle.getState());
        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testLoggingImport() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        configuration.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "org.jboss.logging;version=3.0");
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleD();
        try {
            // Invoke a simple logging activator, import the org.osgi.framework package and the org.jboss.logging package
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testNoManagementImport() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleE();
        try {
            // Invoke a simple JMX activator, import the org.osgi.framework package, but not the javax.management package
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());

            // Note: Apache Felix does not support this, Equinox does.
            try {
                bundle.start();
                fail("BundleException expected");
            } catch (BundleException ex) {
                // expected
            }
            assertBundleState(Bundle.RESOLVED, bundle.getState());

        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testManagementImport() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleF();
        try {
            // Invoke a simple JMX activator, import the org.osgi.framework package, and the javax.management package
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testXMLParserImportFromFramework() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        JavaArchive archive = getBundleG();
        try {
            BundleContext context = framework.getBundleContext();
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            shutdownFramework(framework);
        }
    }

    @Test
    public void testXMLParserImportFromCompendium() throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        Framework framework = createFramework(configuration);
        BundleContext context = framework.getBundleContext();
        File compFile = getTestArchiveFile("bundles/org.osgi.compendium.jar");
        Bundle compendium = context.installBundle(compFile.getCanonicalPath());
		assertLoadClass(compendium, XMLParserActivator.class.getName(), compendium);
        JavaArchive archive = getBundleG();
        try {
            Bundle bundle = context.installBundle(archive.getName(), toInputStream(archive));
            assertBundleState(Bundle.INSTALLED, bundle.getState());
            bundle.start();
            assertBundleState(Bundle.ACTIVE, bundle.getState());
        } finally {
            shutdownFramework(framework);
        }
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.addClasses(SimpleActivator.class, SimpleService.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(SimpleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleB");
        archive.addClasses(SimpleActivator.class, SimpleService.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(SimpleActivator.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleC");
        archive.addClasses(LoggingActivator.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(LoggingActivator.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleD() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleD");
        archive.addClasses(LoggingActivator.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(LoggingActivator.class);
                builder.addImportPackages(BundleActivator.class, Logger.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleE() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleE");
        archive.addClasses(SimpleManagementActivator.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(SimpleManagementActivator.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleF() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleF");
        archive.addClasses(SimpleManagementActivator.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(SimpleManagementActivator.class);
                builder.addImportPackages(BundleActivator.class, MBeanServer.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleG() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleG");
        archive.addClasses(XMLParserActivatorExt.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(XMLParserActivatorExt.class);
                builder.addImportPackages(BundleActivator.class, XMLParserActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private Framework createFramework(Map<String, String> configuration) throws BundleException {
        configuration.put("org.osgi.framework.storage", "target/osgi-store");
        configuration.put("org.osgi.framework.storage.clean", "onFirstInit");
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(configuration);
        framework.start();
        return framework;
    }

    private void shutdownFramework(Framework framework) throws BundleException, InterruptedException {
        framework.stop();
        framework.waitForStop(5000);
    }
}