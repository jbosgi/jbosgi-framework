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
package org.jboss.test.osgi.framework.launch;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.jboss.msc.service.ServiceController.State;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleA.BeanA;
import org.jboss.test.osgi.framework.simple.bundleB.BeanB;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

/**
 * Test autoinstall bundles
 *
 * @author thomas.diesler@jboss.com
 * @since 14-Jul-2012
 */
public class AutoInstallTestCase extends AbstractFrameworkLaunchTest {

    static File fileA, fileB;

    @BeforeClass
    public static void beforeClass() {
        fileA = exportBundleArchive(getBundleA());
        fileB = exportBundleArchive(getBundleB());
    }

    @Test
    public void testSimpleAutoStart() throws Exception {

        Assert.assertTrue("File exists: " + fileB, fileB.exists());

        Map<String, String> initprops = getFrameworkInitProperties(true);
        initprops.put(Constants.PROPERTY_AUTO_START_URLS, fileB.toURI().toString());
        initprops.put(Constants.PROPERTY_FRAMEWORK_BOOTSTRAP_THREADS, new Integer(1).toString());

        Framework framework = newFramework(initprops);
        framework.start();

        Bundle bundleB = getBundleManager().getBundles("bundleB", null).iterator().next();
        Assert.assertEquals(fileB.toURI().toString(), bundleB.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());
    }


    @Test
    public void testValidAutoStart() throws Exception {

        Assert.assertTrue("File exists: " + fileA, fileA.exists());
        Assert.assertTrue("File exists: " + fileB, fileB.exists());

        Map<String, String> initprops = getFrameworkInitProperties(true);
        initprops.put(Constants.PROPERTY_AUTO_START_URLS, fileA.toURI() + "," + fileB.toURI());

        Framework framework = newFramework(initprops);
        framework.start();

        Bundle bundleA = getBundleManager().getBundles("bundleA", null).iterator().next();
        Assert.assertEquals(fileA.toURI().toString(), bundleA.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleA.getState());

        Bundle bundleB = getBundleManager().getBundles("bundleB", null).iterator().next();
        Assert.assertEquals(fileB.toURI().toString(), bundleB.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());
    }

    @Test
    public void testInvalidAutoStart() throws Exception {

        Assert.assertTrue("File exists: " + fileA, fileA.exists());

        Map<String, String> initprops = getFrameworkInitProperties(true);
        initprops.put(Constants.PROPERTY_AUTO_START_URLS, fileA.toURI().toString());

        Framework framework = newFramework(initprops);
        try {
            framework.start();
            Assert.fail("BundleException expected");
        } catch (BundleException e) {
            // expected
        }
        assertBundleState(Bundle.INSTALLED, framework.getState());
    }

    @Test
    public void testSimpleUninstall() throws Exception {

        Assert.assertTrue("File exists: " + fileB, fileB.exists());

        Map<String, String> initprops = getFrameworkInitProperties(true);
        initprops.put(Constants.PROPERTY_AUTO_START_URLS, fileB.toURI().toString());
        initprops.put(Constants.PROPERTY_FRAMEWORK_BOOTSTRAP_THREADS, new Integer(1).toString());

        Framework framework = newFramework(initprops);
        framework.start();

        Bundle bundleB = getBundleManager().getBundles("bundleB", null).iterator().next();
        Assert.assertEquals(fileB.toURI().toString(), bundleB.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());

        bundleB.uninstall();

        // Verify that the framework is still active
        assertServiceState(State.UP, Services.FRAMEWORK_ACTIVE);
    }

    @Test
    public void testFrameworkRestart() throws Exception {

        Assert.assertTrue("File exists: " + fileB, fileB.exists());

        Map<String, String> initprops = getFrameworkInitProperties(true);
        initprops.put(Constants.PROPERTY_AUTO_START_URLS, fileB.toURI().toString());
        initprops.put(Constants.PROPERTY_FRAMEWORK_BOOTSTRAP_THREADS, new Integer(1).toString());

        Framework framework = newFramework(initprops);
        framework.start();

        Bundle bundleB = getBundleManager().getBundles("bundleB", null).iterator().next();
        Assert.assertEquals(fileB.toURI().toString(), bundleB.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());

        Bundle bundleC = installBundle(getBundleC());
        assertBundleState(Bundle.INSTALLED, bundleC.getState());

        Bundle bundleD = installBundle(getBundleD());
        assertBundleState(Bundle.INSTALLED, bundleD.getState());

        bundleD.start();
        assertBundleState(Bundle.INSTALLED, bundleC.getState());
        assertBundleState(Bundle.ACTIVE, bundleD.getState());

        bundleB.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundleB.getState());

        // Verify that the framework is still active
        assertServiceState(State.UP, Services.FRAMEWORK_ACTIVE);

        framework.stop();
        framework.waitForStop(2000);
        framework.start();

        bundleB = getBundleManager().getBundles("bundleB", null).iterator().next();
        bundleC = getBundleManager().getBundles("bundleC", null).iterator().next();
        bundleD = getBundleManager().getBundles("bundleD", null).iterator().next();

        assertBundleState(Bundle.ACTIVE, bundleB.getState());
        assertBundleState(Bundle.INSTALLED, bundleC.getState());
        assertBundleState(Bundle.ACTIVE, bundleD.getState());
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

    private static JavaArchive getBundleC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleC");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages(BeanB.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private static JavaArchive getBundleD() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleD");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages(BeanB.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}