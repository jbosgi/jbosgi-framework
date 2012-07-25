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

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.TypeAdaptor;
import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.testing.OSGiTest;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

/**
 * Abstract framework launch test
 *
 * @author thomas.diesler@jboss.com
 * @since 14-Jul-2012
 */
public abstract class FrameworkLaunchTest extends OSGiTest {

    private Framework framework;

    public Map<String, String> getFrameworkInitProperties(boolean cleanOnFirstInit) {
        Map<String, String> props = new HashMap<String, String>();
        props.put(Constants.FRAMEWORK_STORAGE, getBundleStorageDir().getAbsolutePath());
        if (cleanOnFirstInit == true) {
            props.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        }
        return props;
    }

    public Framework newFramework(Map<String, String> initprops) {
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        return framework = factory.newFramework(initprops);
    }

    public Framework getFramework() {
        Assert.assertNotNull("Framework not available", framework);
        return framework;
    }

    public BundleContext getBundleContext() {
        return getFramework().getBundleContext();
    }

    public void assertServiceState(State state, ServiceName serviceName) {
        ServiceController<?> controller = getRequiredService(serviceName);
        Assert.assertEquals(state, controller.getState());
    }

    public ServiceController<?> getRequiredService(ServiceName serviceName) {
        ServiceContainer serviceContainer = getBundleManager().getServiceContainer();
        return serviceContainer.getRequiredService(serviceName);
    }

    public BundleManager getBundleManager() {
        Bundle sysbundle = getBundleContext().getBundle();
        return ((TypeAdaptor)sysbundle).adapt(BundleManager.class);
    }

    public PackageAdmin getPackageAdmin() throws BundleException {
        BundleContext context = getBundleContext();
        ServiceReference sref = context.getServiceReference(PackageAdmin.class.getName());
        return (PackageAdmin) context.getService(sref);
    }

    public StartLevel getStartLevel() throws BundleException {
        BundleContext context = getBundleContext();
        ServiceReference sref = context.getServiceReference(StartLevel.class.getName());
        return (StartLevel) context.getService(sref);
    }

    public static File exportBundleArchive(JavaArchive archive) {
        String basedir = System.getProperty("test.archive.directory");
        File targetFile = new File(basedir + File.separator + archive.getName() + ".jar");
        archive.as(ZipExporter.class).exportTo(targetFile, true);
        return targetFile;
    }

    public File getBundleStorageDir() {
        String archivesdir = System.getProperty("test.archive.directory", "test-libs");
        File targetdir = new File(archivesdir).getParentFile().getAbsoluteFile();
        return new File(targetdir + File.separator + "test-osgi-store").getAbsoluteFile();
    }

    public Bundle installBundle(JavaArchive archive) throws BundleException {
        InputStream input = archive.as(ZipExporter.class).exportAsInputStream();
        return getBundleContext().installBundle(archive.getName(), input);
    }
}