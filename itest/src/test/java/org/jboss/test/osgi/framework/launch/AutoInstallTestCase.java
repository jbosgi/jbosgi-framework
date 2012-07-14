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
package org.jboss.test.osgi.framework.launch;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleA.BeanA;
import org.jboss.test.osgi.framework.simple.bundleB.BeanB;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Test autoinstall bundles
 *
 * @author thomas.diesler@jboss.com
 * @since 14-Jul-2012
 */
public class AutoInstallTestCase extends OSGiFrameworkTest {

    static File fileA, fileB;
    static File storageDir = new File("target/test-osgi-store").getAbsoluteFile();

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
        
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(initprops);
        framework.start();
        
        PackageAdmin pa = getPackageAdmin(framework.getBundleContext());
        Bundle bundleB = pa.getBundles("bundleB", null)[0];
        Assert.assertEquals(fileB.toURI().toString(), bundleB.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());
    }

    @Test
    public void testValidAutoStart() throws Exception {
       
        Assert.assertTrue("File exists: " + fileA, fileA.exists());
        Assert.assertTrue("File exists: " + fileB, fileB.exists());
        
        Map<String, String> initprops = getFrameworkInitProperties(true);
        initprops.put(Constants.PROPERTY_AUTO_START_URLS, fileA.toURI() + "," + fileB.toURI());
        
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(initprops);
        framework.start();
        
        PackageAdmin pa = getPackageAdmin(framework.getBundleContext());
        Bundle bundleA = pa.getBundles("bundleA", null)[0];
        Assert.assertEquals(fileA.toURI().toString(), bundleA.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleA.getState());
        
        Bundle bundleB = pa.getBundles("bundleB", null)[0];
        Assert.assertEquals(fileB.toURI().toString(), bundleB.getLocation());
        assertBundleState(Bundle.ACTIVE, bundleB.getState());
    }

    @Test
    public void testInvalidAutoStart() throws Exception {
        
        Assert.assertTrue("File exists: " + fileA, fileA.exists());
       
        Map<String, String> initprops = getFrameworkInitProperties(true);
        initprops.put(Constants.PROPERTY_AUTO_START_URLS, fileA.toURI().toString());
        
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(initprops);
        framework.start();
        
        PackageAdmin pa = getPackageAdmin(framework.getBundleContext());
        Bundle bundleA = pa.getBundles("bundleA", null)[0];
        Assert.assertEquals(fileA.toURI().toString(), bundleA.getLocation());
        assertBundleState(Bundle.INSTALLED, bundleA.getState());
    }

    private Map<String, String> getFrameworkInitProperties(boolean cleanOnFirstInit) {
        Map<String, String> props = new HashMap<String, String>();
        props.put(Constants.FRAMEWORK_STORAGE, storageDir.getAbsolutePath());
        if (cleanOnFirstInit == true) {
            props.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        }
        return props;
    }

    private PackageAdmin getPackageAdmin(BundleContext systemContext) throws BundleException {
        ServiceReference sref = systemContext.getServiceReference(PackageAdmin.class.getName());
        return (PackageAdmin) systemContext.getService(sref);
    }
    
    private static File exportBundleArchive(JavaArchive archive) {
        String basedir = System.getProperty("test.archive.directory");
        File targetFile = new File(basedir + File.separator + archive.getName() + ".jar");
        archive.as(ZipExporter.class).exportTo(targetFile, true);
        return targetFile;
    }
    
    private static JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.addClasses(BeanA.class);
        archive.setManifest(new Asset() {
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