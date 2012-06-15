/*
 * #%L
 * JBossOSGi Framework iTest
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
package org.jboss.test.osgi.framework.xservice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.bundleA.BundleActivatorA;
import org.jboss.test.osgi.framework.xservice.bundleA.BundleServiceA;
import org.jboss.test.osgi.framework.xservice.bundleB.BundleActivatorB;
import org.jboss.test.osgi.framework.xservice.bundleB.BundleServiceB;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleActivatorX;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.jboss.test.osgi.framework.xservice.moduleY.ModuleActivatorY;
import org.jboss.test.osgi.framework.xservice.moduleY.ModuleServiceY;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * Test that an MSC module can have a dependency on an OSGi bundle and vice versa.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jul-2010
 */
public class ModuleServiceTestCase extends OSGiFrameworkTest {

    @Test
    public void testModuleService() throws Exception {
        Bundle bundleXS = installBundle(getModuleXS());
        try {
            assertNotNull("Bundle not null", bundleXS);
            assertEquals("moduleXS", bundleXS.getSymbolicName());
            assertEquals(Version.parseVersion("1.0"), bundleXS.getVersion());

            bundleXS.start();
            assertBundleState(Bundle.ACTIVE, bundleXS.getState());

            BundleContext context = bundleXS.getBundleContext();
            assertNotNull("Context not null", context);

            ServiceReference sref = context.getServiceReference(ModuleServiceX.class.getName());
            assertNotNull("Service ref not null", sref);

            Object service = context.getService(sref);
            assertNotNull("Service not null", service);

            String was = invokeService(service, "hello");
            assertEquals("hello:moduleXS", was);

            bundleXS.stop();
            assertBundleState(Bundle.RESOLVED, bundleXS.getState());
        } finally {
            bundleXS.uninstall();
        }
    }

    @Test
    public void testModuleDependsOnBundle() throws Exception {
        Bundle bundleYS = installBundle(getModuleYS());
        try {
            // Install the dependent bundle
            Bundle bundleB = installBundle(getBundleB());
            try {
                bundleB.start();
                assertBundleState(Bundle.ACTIVE, bundleB.getState());

                bundleYS.start();
                assertBundleState(Bundle.ACTIVE, bundleYS.getState());

                BundleContext context = bundleYS.getBundleContext();
                ServiceReference sref = context.getServiceReference(ModuleServiceY.class.getName());
                String was = invokeService(context.getService(sref), "hello");
                assertEquals("hello:moduleYS:bundleB:1.0.0", was);
            } finally {
                bundleB.uninstall();
            }

            bundleYS.stop();
            assertBundleState(Bundle.RESOLVED, bundleYS.getState());
        } finally {
            bundleYS.uninstall();
        }
    }

    @Test
    public void testBundleDependsOnModule() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        try {
            // Install the dependent module
            Bundle bundleXS = installBundle(getModuleXS());
            try {
                bundleXS.start();
                assertBundleState(Bundle.ACTIVE, bundleXS.getState());

                bundleA.start();
                assertBundleState(Bundle.ACTIVE, bundleA.getState());

                BundleContext context = bundleA.getBundleContext();
                ServiceReference sref = context.getServiceReference(BundleServiceA.class.getName());
                String was = invokeService(context.getService(sref), "hello");
                assertEquals("hello:bundleA:1.0.0:moduleXS", was);
            } finally {
                bundleXS.uninstall();
            }

            bundleA.stop();
            assertBundleState(Bundle.RESOLVED, bundleA.getState());
        } finally {
            bundleA.uninstall();
        }
    }

    private String invokeService(Object service, String exp) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = service.getClass().getMethod("echo", new Class<?>[] { String.class });
        String was = (String) method.invoke(service, exp);
        return was;
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.addClasses(BundleActivatorA.class, BundleServiceA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(BundleActivatorA.class);
                builder.addRequireBundle("moduleXS;bundle-version:=1.0.0");
                builder.addImportPackages("org.osgi.framework");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleB");
        archive.addClasses(BundleActivatorB.class, BundleServiceB.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(BundleActivatorB.class);
                builder.addExportPackages(BundleServiceB.class);
                builder.addImportPackages("org.osgi.framework");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getModuleXS() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleXS");
        archive.addClasses(ModuleActivatorX.class, ModuleServiceX.class);
        archive.addAsManifestResource(getResourceFile("xservice/moduleXS/META-INF/jbosgi-xservice.properties"));
        return archive;
    }

    private JavaArchive getModuleYS() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleYS");
        archive.addClasses(ModuleActivatorY.class, ModuleServiceY.class);
        archive.addAsManifestResource(getResourceFile("xservice/moduleYS/META-INF/jbosgi-xservice.properties"));
        return archive;
    }
}
