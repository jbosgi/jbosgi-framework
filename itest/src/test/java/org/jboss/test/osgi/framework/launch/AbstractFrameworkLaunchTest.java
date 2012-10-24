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
import java.util.HashMap;
import java.util.Map;

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.internal.FrameworkBuilder;
import org.jboss.osgi.resolver.Adaptable;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.testing.OSGiTest;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
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
public abstract class AbstractFrameworkLaunchTest extends OSGiTest {

    private Framework framework;

    protected Map<String, Object> getFrameworkInitProperties(boolean cleanOnFirstInit) {
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(Constants.FRAMEWORK_STORAGE, getBundleStorageDir().getAbsolutePath());
        if (cleanOnFirstInit == true) {
            props.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        }
        return props;
    }

    protected Framework newFramework(FrameworkBuilder builder) {
        return framework = builder.createFramework();
    }

    protected Framework newFramework(Map<String, Object> initprops) {
        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        return framework = factory.newFramework(initprops);
    }

    protected Framework getFramework() {
        Assert.assertNotNull("Framework not available", framework);
        return framework;
    }

    protected BundleContext getBundleContext() {
        return getFramework().getBundleContext();
    }

    protected void assertServiceState(State state, ServiceName serviceName) {
        ServiceController<?> controller = getRequiredService(serviceName);
        Assert.assertEquals(state, controller.getState());
    }

    protected void assertServiceState(ServiceContainer serviceContainer, State state, ServiceName serviceName) {
        ServiceController<?> controller = serviceContainer.getRequiredService(serviceName);
        Assert.assertEquals(state, controller.getState());
    }

    protected ServiceController<?> getRequiredService(ServiceName serviceName) {
        ServiceContainer serviceContainer = getServiceContainer();
        return serviceContainer.getRequiredService(serviceName);
    }

    protected ServiceController<?> getService(ServiceName serviceName) {
        ServiceContainer serviceContainer = getServiceContainer();
        return serviceContainer.getService(serviceName);
    }

    protected ServiceContainer getServiceContainer() {
        return ((Adaptable) framework).adapt(ServiceContainer.class);
    }

    protected BundleManager getBundleManager() {
        return ((Adaptable) framework).adapt(BundleManager.class);
    }

    protected PackageAdmin getPackageAdmin() throws BundleException {
        BundleContext context = getBundleContext();
        ServiceReference sref = context.getServiceReference(PackageAdmin.class.getName());
        return (PackageAdmin) context.getService(sref);
    }

    protected StartLevel getStartLevel() throws BundleException {
        BundleContext context = getBundleContext();
        ServiceReference sref = context.getServiceReference(StartLevel.class.getName());
        return (StartLevel) context.getService(sref);
    }

    protected File getBundleStorageDir() {
        String archivesdir = System.getProperty("test.archive.directory", "test-libs");
        File targetdir = new File(archivesdir).getParentFile().getAbsoluteFile();
        return new File(targetdir + File.separator + "test-osgi-store").getAbsoluteFile();
    }

    protected XBundle installBundle(JavaArchive archive) throws BundleException {
        InputStream input = archive.as(ZipExporter.class).exportAsInputStream();
        return (XBundle) getBundleContext().installBundle(archive.getName(), input);
    }

    public static File exportBundleArchive(JavaArchive archive) {
        String basedir = System.getProperty("test.archive.directory");
        File targetFile = new File(basedir + File.separator + archive.getName() + ".jar");
        archive.as(ZipExporter.class).exportTo(targetFile, true);
        return targetFile;
    }
}