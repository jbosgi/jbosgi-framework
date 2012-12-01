package org.jboss.test.osgi.framework.startlevel;
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

import java.io.InputStream;
import java.util.Properties;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.lifecycle1.ActivatorA;
import org.jboss.test.osgi.framework.bundle.support.lifecycle2.ActivatorB;
import org.jboss.test.osgi.framework.bundle.support.lifecycle3.ActivatorC;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;

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
            FrameworkStartLevel startLevel = framework.adapt(FrameworkStartLevel.class);
            Assert.assertEquals(1, startLevel.getStartLevel());
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
            FrameworkStartLevel startLevel = framework.adapt(FrameworkStartLevel.class);
            Assert.assertEquals(51, startLevel.getStartLevel());
        } finally {
            framework.stop();
            framework.waitForStop(2000);
            System.setProperties(oldProps);
        }
    }

    @Test
    public void testSystemBundle() throws Exception {
        Framework framework = createFramework();
        try {
            framework.start();
            BundleContext syscontext = getSystemContext();
            FrameworkStartLevel frameworkStartLevel = framework.adapt(FrameworkStartLevel.class);
            BundleStartLevel bundleStartLevel = framework.adapt(BundleStartLevel.class);
            int frameworkLevel = frameworkStartLevel.getStartLevel();
            Assert.assertEquals(0, bundleStartLevel.getStartLevel());
            try {
                bundleStartLevel.setStartLevel(42);
                Assert.fail("IllegalArgumentException expected");
            }
            catch (IllegalArgumentException ex) {
                // expected
            }
            frameworkStartLevel.setStartLevel(frameworkLevel, this);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, syscontext.getBundle(), null);
        } finally {
            framework.stop();
            framework.waitForStop(2000);
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
            FrameworkStartLevel startLevel = framework.adapt(FrameworkStartLevel.class);

            Bundle bundleA = installBundle(archive1);
            Assert.assertEquals(Bundle.INSTALLED, bundleA.getState());
            bundleA.start();
            Assert.assertEquals(Bundle.ACTIVE, bundleA.getState());

            startLevel.setInitialBundleStartLevel(7);
            Bundle bundleB = installBundle(archive2);
            bundleB.start();
            Assert.assertEquals("Start level of 7", Bundle.INSTALLED, bundleB.getState());

            startLevel.setInitialBundleStartLevel(5);
            Bundle bundleC = installBundle(archive3);
            bundleC.start();
            Assert.assertEquals("Start level of 5", Bundle.INSTALLED, bundleC.getState());

            startLevel.setStartLevel(10, this);
            Bundle sysbundle = framework.getBundleContext().getBundle();
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, sysbundle, null);

            Assert.assertEquals(Bundle.ACTIVE, bundleA.getState());
            Assert.assertEquals(Bundle.ACTIVE, bundleB.getState());
            Assert.assertEquals(Bundle.ACTIVE, bundleC.getState());

            synchronized ("LifecycleOrdering") {
                Assert.assertEquals("start1start3start2", System.getProperty("LifecycleOrdering"));
            }
        } finally {
            framework.stop();
            framework.waitForStop(2000);
        }
        synchronized ("LifecycleOrdering") {
            Assert.assertEquals("start1start3start2stop2stop3stop1", System.getProperty("LifecycleOrdering"));
        }
    }

    @Test
    public void testChangingStartLevel() throws Exception {
        JavaArchive archive1 = createTestBundle("b1.jar", ActivatorA.class);
        Framework framework = createFramework();
        try {
            framework.start();
            FrameworkStartLevel startLevel = framework.adapt(FrameworkStartLevel.class);

            startLevel.setInitialBundleStartLevel(10);

            startLevel.setStartLevel(5, this);
            Bundle sysbundle = framework.getBundleContext().getBundle();
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, sysbundle, null);
            Assert.assertEquals(5, startLevel.getStartLevel());

            Bundle b1 = installBundle(archive1);
            Assert.assertEquals(Bundle.INSTALLED, b1.getState());
            // TODO BundleStartLevel
            //Assert.assertEquals(10, startLevel.getBundleStartLevel(b1));

            startLevel.setStartLevel(15, this);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, sysbundle, null);
            Assert.assertEquals(15, startLevel.getStartLevel());
            Assert.assertEquals("The bundle should not yet be started as bundle.start() was never called in the first place.", Bundle.INSTALLED, b1.getState());

            startLevel.setStartLevel(5, this);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, sysbundle, null);
            Assert.assertEquals(5, startLevel.getStartLevel());

            b1.start();
            Assert.assertEquals("The bundle should not yet be started since the start level is too low.", Bundle.INSTALLED, b1.getState());

            startLevel.setStartLevel(15, this);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, sysbundle, null);
            Assert.assertEquals(15, startLevel.getStartLevel());
            Assert.assertEquals(Bundle.ACTIVE, b1.getState());

            b1.uninstall();
        } finally {
            framework.stop();
            framework.waitForStop(2000);
        }
    }

    @Test
    @Ignore
    public void testChangingBundleStartLevel() throws Exception {
        JavaArchive archive1 = createTestBundle("b1.jar", ActivatorA.class);

        Framework framework = createFramework();
        try {
            framework.start();

            FrameworkStartLevel framworkStartLevel = framework.adapt(FrameworkStartLevel.class);
            framworkStartLevel.setInitialBundleStartLevel(10);

            framworkStartLevel.setStartLevel(5, this);
            assertFrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, framework.getBundleContext().getBundle(), null);
            Assert.assertEquals(5, framworkStartLevel.getStartLevel());

            Bundle bundleA = installBundle(archive1);
            BundleStartLevel bundleStartLevel = bundleA.adapt(BundleStartLevel.class);
            Assert.assertEquals(Bundle.INSTALLED, bundleA.getState());
            Assert.assertEquals(10, bundleStartLevel.getStartLevel());

            bundleStartLevel.setStartLevel(3);
            // Unfortunately this test has to use sleeps as there are no events sent here
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            Assert.assertEquals(3, bundleStartLevel.getStartLevel());
            Assert.assertEquals("The bundle should not yet be started as bundle.start() was never called in the first place.", Bundle.INSTALLED, bundleA.getState());

            bundleStartLevel.setStartLevel(8);
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            Assert.assertEquals(8, bundleStartLevel.getStartLevel());
            Assert.assertEquals("The bundle should not yet be started as bundle.start() was never called in the first place.", Bundle.INSTALLED, bundleA.getState());

            bundleA.start();
            Assert.assertEquals("The bundle should not yet be started since the start level is too low.", Bundle.INSTALLED, bundleA.getState());

            bundleStartLevel.setStartLevel(3);
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            Assert.assertEquals(3, bundleStartLevel.getStartLevel());
            Assert.assertEquals(Bundle.ACTIVE, bundleA.getState());

            bundleStartLevel.setStartLevel(8);
            Thread.sleep(BUNDLE_START_LEVEL_CHANGE_SLEEP);
            Assert.assertEquals(8, bundleStartLevel.getStartLevel());
            Assert.assertEquals("The bundle should have been stopped as its start level was set to a higher one than the current.", Bundle.RESOLVED, bundleA.getState());

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