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
package org.jboss.test.osgi.framework.startlevel;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.lifecycle1.ActivatorA;
import org.jboss.test.osgi.framework.bundle.support.lifecycle2.ActivatorB;
import org.jboss.test.osgi.framework.bundle.support.lifecycle3.ActivatorC;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.service.startlevel.StartLevel;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Test start level.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Apr-2010
 */
public class StartLevelTestCase extends OSGiFrameworkTest {

    private static final int BUNDLE_START_LEVEL_CHANGE_SLEEP = 500;

    @BeforeClass
    public static void beforeClass() {
        // prevent framework creation
    }

    @Test
    public void testDefaultInitialStartLevel() throws Exception {
        Framework framework = createFramework();
        try {
            framework.start();
            assertEquals(1, getStartLevel().getStartLevel());
        } finally {
            framework.stop();
            framework.waitForStop(2000);
        }
    }

    @Test
    public void testNonDefaultInitialStartLevel() throws Exception {
        Properties oldProps = new Properties();
        oldProps.putAll(System.getProperties());

        System.setProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL, "51");
        Framework framework = createFramework();
        try {
            framework.start();

            BundleContext bc = framework.getBundleContext();
            ServiceReference sref = bc.getServiceReference(StartLevel.class.getName());
            StartLevel sl = (StartLevel) bc.getService(sref);
            assertEquals(51, sl.getStartLevel());
        } finally {
            framework.stop();
            framework.waitForStop(2000);
            System.setProperties(oldProps);
        }
    }

    @Test
    public void testOrderedStop() throws Exception {
        System.setProperty("LifecycleOrdering", "");
        JavaArchive archive1 = createTestBundle("b1.jar", ActivatorA.class);
        JavaArchive archive2 = createTestBundle("b2.jar", ActivatorB.class);
        JavaArchive archive3 = createTestBundle("b3.jar", ActivatorC.class);

        Framework framework = createFramework();
        try {
            framework.start();

            BundleContext syscontext = framework.getBundleContext();
            ServiceReference sref = syscontext.getServiceReference(StartLevel.class.getName());
            StartLevel startLevel = (StartLevel) syscontext.getService(sref);

            Bundle bundleA = installBundle(archive1);
            assertEquals(Bundle.INSTALLED, bundleA.getState());
            bundleA.start();
            assertEquals(Bundle.ACTIVE, bundleA.getState());

            startLevel.setInitialBundleStartLevel(7);
            Bundle bundleB = installBundle(archive2);
            bundleB.start();
            assertEquals("Start level of 7", Bundle.INSTALLED, bundleB.getState());

            startLevel.setInitialBundleStartLevel(5);
            Bundle bundleC = installBundle(archive3);
            bundleC.start();
            assertEquals("Start level of 5", Bundle.INSTALLED, bundleC.getState());

            syscontext.addFrameworkListener(this);
            startLevel.setStartLevel(10);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, syscontext.getBundle(), null);
            syscontext.removeFrameworkListener(this);

            assertEquals(Bundle.ACTIVE, bundleA.getState());
            assertEquals(Bundle.ACTIVE, bundleB.getState());
            assertEquals(Bundle.ACTIVE, bundleC.getState());

            synchronized ("LifecycleOrdering") {
                assertEquals("start1start3start2", System.getProperty("LifecycleOrdering"));
            }
        } finally {
            framework.stop();
            framework.waitForStop(2000);
        }
        synchronized ("LifecycleOrdering") {
            assertEquals("start1start3start2stop2stop3stop1", System.getProperty("LifecycleOrdering"));
        }
    }

    @Test
    public void testChangingStartLevel() throws Exception {
        JavaArchive archive1 = createTestBundle("b1.jar", ActivatorA.class);
        Framework framework = createFramework();
        try {
            framework.start();

            BundleContext bc = framework.getBundleContext();
            ServiceReference sref = bc.getServiceReference(StartLevel.class.getName());
            StartLevel sl = (StartLevel) bc.getService(sref);

            sl.setInitialBundleStartLevel(10);

            bc.addFrameworkListener(this);
            sl.setStartLevel(5);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework.getBundleContext().getBundle(), null);
            assertEquals(5, sl.getStartLevel());

            Bundle b1 = installBundle(archive1);
            assertEquals(Bundle.INSTALLED, b1.getState());
            assertEquals(10, sl.getBundleStartLevel(b1));

            sl.setStartLevel(15);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework.getBundleContext().getBundle(), null);
            assertEquals(15, sl.getStartLevel());
            assertEquals("The bundle should not yet be started as bundle.start() was never called in the first place.", Bundle.INSTALLED, b1.getState());

            sl.setStartLevel(5);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework.getBundleContext().getBundle(), null);
            assertEquals(5, sl.getStartLevel());

            b1.start();
            assertEquals("The bundle should not yet be started since the start level is too low.", Bundle.INSTALLED, b1.getState());

            sl.setStartLevel(15);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework.getBundleContext().getBundle(), null);
            assertEquals(15, sl.getStartLevel());
            assertEquals(Bundle.ACTIVE, b1.getState());

            b1.uninstall();
        } finally {
            framework.stop();
            framework.waitForStop(2000);
        }
    }

    @Test
    public void testChangingBundleStartLevel() throws Exception {
        JavaArchive archive1 = createTestBundle("b1.jar", ActivatorA.class);

        Framework framework = createFramework();
        try {
            framework.start();

            BundleContext syscontext = framework.getBundleContext();
            ServiceReference sref = syscontext.getServiceReference(StartLevel.class.getName());
            StartLevel startLevel = (StartLevel) syscontext.getService(sref);

            startLevel.setInitialBundleStartLevel(10);

            syscontext.addFrameworkListener(this);
            startLevel.setStartLevel(5);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework.getBundleContext().getBundle(), null);
            assertEquals(5, startLevel.getStartLevel());

            Bundle bundleA = installBundle(archive1);
            assertEquals(Bundle.INSTALLED, bundleA.getState());
            assertEquals(10, startLevel.getBundleStartLevel(bundleA));

            startLevel.setBundleStartLevel(bundleA, 3);
            // Unfortunately this test has to use sleeps as there are no events sent here
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            assertEquals(3, startLevel.getBundleStartLevel(bundleA));
            assertEquals("The bundle should not yet be started as bundle.start() was never called in the first place.", Bundle.INSTALLED, bundleA.getState());

            startLevel.setBundleStartLevel(bundleA, 8);
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            assertEquals(8, startLevel.getBundleStartLevel(bundleA));
            assertEquals("The bundle should not yet be started as bundle.start() was never called in the first place.", Bundle.INSTALLED, bundleA.getState());

            bundleA.start();
            assertEquals("The bundle should not yet be started since the start level is too low.", Bundle.INSTALLED, bundleA.getState());

            startLevel.setBundleStartLevel(bundleA, 3);
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            assertEquals(3, startLevel.getBundleStartLevel(bundleA));
            assertEquals(Bundle.ACTIVE, bundleA.getState());

            startLevel.setBundleStartLevel(bundleA, 8);
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            assertEquals(8, startLevel.getBundleStartLevel(bundleA));
            assertEquals("The bundle should have been stopped as its start level was set to a higher one than the current.", Bundle.RESOLVED, bundleA.getState());

            bundleA.uninstall();
        } finally {
            framework.stop();
            framework.waitForStop(2000);
        }
    }

    private JavaArchive createTestBundle(String name, final Class<? extends BundleActivator> activator) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(activator);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        archive.addClass(activator);
        return archive;
    }
}