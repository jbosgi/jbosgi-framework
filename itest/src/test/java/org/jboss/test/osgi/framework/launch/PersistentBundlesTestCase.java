package org.jboss.test.osgi.framework.launch;
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

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleA.BeanA;
import org.jboss.test.osgi.framework.simple.bundleB.BeanB;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.startlevel.BundleStartLevel;

/**
 * Test persistent bundles
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Oct-2010
 */
public class PersistentBundlesTestCase extends AbstractFrameworkLaunchTest {

    @Test
    public void testInstalledBundle() throws Exception {
        Map<String, String> initprops = getFrameworkInitProperties(true);
        Framework framework = newFramework(initprops);

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        // Verify system bundle storage dir
        File storageDir = getBundleStorageDir();
        File systemStorageDir = new File(storageDir + "/bundle-0");
        Assert.assertTrue("File exists: " + systemStorageDir, systemStorageDir.exists());

        // Install a bundle and verify its state and storage dir
        Bundle bundle = installBundle(getBundleB());
        Assert.assertEquals("Bundle Id", 1, bundle.getBundleId());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        File bundleStorageDir = new File(storageDir + "/bundle-1");
        Assert.assertTrue("File exists: " + bundleStorageDir, bundleStorageDir.exists());

        // Stop the framework
        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());

        // Check that the storage dirs are still there
        Assert.assertTrue("File exists: " + systemStorageDir, systemStorageDir.exists());
        Assert.assertTrue("File exists: " + bundleStorageDir, bundleStorageDir.exists());

        // Restart the Framework
        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        // Check that the storage dirs are still there
        Assert.assertTrue("File exists: " + systemStorageDir, systemStorageDir.exists());
        Assert.assertTrue("File exists: " + bundleStorageDir, bundleStorageDir.exists());

        bundle = getBundleContext().getBundle(1);
        Assert.assertNotNull("Bundle available", bundle);
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        bundle.uninstall();
        Assert.assertFalse("File deleted: " + bundleStorageDir, bundleStorageDir.exists());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    @Test
    public void testUninstalledBundle() throws Exception {
        Map<String, String> initprops = getFrameworkInitProperties(true);
        Framework framework = newFramework(initprops);

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        Bundle bundleB = installBundle(getBundleB());
        Assert.assertEquals("Bundle Id", 1, bundleB.getBundleId());
        assertBundleState(Bundle.INSTALLED, bundleB.getState());

        File storageDir = getBundleStorageDir();
        File storageDirB = new File(storageDir + "/bundle-1");
        Assert.assertTrue("File exists: " + storageDirB, storageDirB.exists());

        bundleB.uninstall();
        Assert.assertFalse("File deleted: " + storageDirB, storageDirB.exists());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());

        // Restart the Framework
        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());
        Bundle[] bundles = getBundleContext().getBundles();
        Assert.assertEquals("Bundle Id", 1, bundles.length);
        Assert.assertEquals("Bundle Id", 0, bundles[0].getBundleId());
    }

    @Test
    public void testUninstalledDependentBundle() throws Exception {
        Map<String, String> initprops = getFrameworkInitProperties(true);
        Framework framework = newFramework(initprops);

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        Bundle bundleB = installBundle(getBundleB());
        Assert.assertEquals("Bundle Id", 1, bundleB.getBundleId());
        assertBundleState(Bundle.INSTALLED, bundleB.getState());

        File storageDir = getBundleStorageDir();
        File storageDirB = new File(storageDir + "/bundle-1");
        Assert.assertTrue("File exists: " + storageDirB, storageDirB.exists());

        Bundle bundleA = installBundle(getBundleA());
        Assert.assertEquals("Bundle Id", 2, bundleA.getBundleId());
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        File storageDirA = new File(storageDir + "/bundle-2");
        Assert.assertTrue("File exists: " + storageDirA, storageDirA.exists());

        bundleA.start();
        assertBundleState(Bundle.ACTIVE, bundleA.getState());

        bundleB.uninstall();
        Assert.assertTrue("File exists: " + storageDirA, storageDirA.exists());
        Assert.assertFalse("File exists: " + storageDirB, storageDirB.exists());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());

        // Restart the Framework
        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        Bundle[] bundles = getBundleContext().getBundles();
        Assert.assertEquals("Bundle Id", 2, bundles.length);

        bundleA = bundles[0].getBundleId() > 0 ? bundles[0] : bundles[1];
        Assert.assertEquals("Bundle Id", 2, bundleA.getBundleId());
        assertBundleState(Bundle.INSTALLED, bundleA.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    @Test
    public void testActiveBundle() throws Exception {
        Map<String, String> initprops = getFrameworkInitProperties(true);
        Framework framework = newFramework(initprops);

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        Bundle bundle = installBundle(getBundleB());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        bundle.start();
        assertBundleState(Bundle.ACTIVE, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());

        // Restart the Framework
        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        bundle = getBundleContext().getBundle(bundle.getBundleId());
        Assert.assertNotNull("Bundle available", bundle);

        assertBundleState(Bundle.ACTIVE, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    @Test
    @Ignore
    public void testBundleStartLevel() throws Exception {
        Map<String, String> initprops = getFrameworkInitProperties(true);
        Framework framework = newFramework(initprops);

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        JavaArchive archive = getBundleB();
        BundleContext syscontext = framework.getBundleContext();
        Bundle bundle = syscontext.installBundle(archive.getName(), toInputStream(archive));
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        BundleStartLevel startLevel = bundle.adapt(BundleStartLevel.class);
        startLevel.setStartLevel(3);

        bundle.start();
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());

        // Restart the Framework
        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        syscontext = framework.getBundleContext();
        bundle = syscontext.getBundle(bundle.getBundleId());
        Assert.assertNotNull("Bundle available", bundle);

        Assert.assertEquals(3, startLevel.getStartLevel());

        assertBundleState(Bundle.INSTALLED, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    @Test
    public void testStoppedBundle() throws Exception {
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(getFrameworkInitProperties(true));

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        JavaArchive archive = getBundleB();
        BundleContext syscontext = framework.getBundleContext();
        Bundle bundle = syscontext.installBundle(archive.getName(), toInputStream(archive));
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        bundle.start();
        assertBundleState(Bundle.ACTIVE, bundle.getState());

        bundle.stop();
        assertBundleState(Bundle.RESOLVED, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());

        // Restart the Framework
        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        syscontext = framework.getBundleContext();
        bundle = syscontext.getBundle(bundle.getBundleId());
        Assert.assertNotNull("Bundle available", bundle);
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    @Test
    public void testFrameworkInit() throws Exception {
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(getFrameworkInitProperties(true));

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        JavaArchive archive = getBundleB();
        BundleContext syscontext = framework.getBundleContext();
        Bundle bundle = syscontext.installBundle(archive.getName(), toInputStream(archive));
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());

        // Create a new framework and init
        framework = factory.newFramework(getFrameworkInitProperties(false));
        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        syscontext = framework.getBundleContext();
        bundle = syscontext.getBundle(bundle.getBundleId());
        Assert.assertNotNull("Bundle available", bundle);
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    private static JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.addClasses(BeanA.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(BeanA.class);
                builder.addImportPackages(BeanB.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private static JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleB");
        archive.addClasses(BeanB.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(BeanB.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}