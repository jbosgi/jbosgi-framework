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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.startlevel.StartLevel;

/**
 * Test framework bootstrap options.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Apr-2010
 */
public class FrameworkLaunchTestCase extends OSGiFrameworkTest {

    @BeforeClass
    public static void beforeClass() {
        // prevent framework creation
    }

    @Test
    public void testFrameworkStartStop() throws Exception {
        Map<String, String> props = new HashMap<String, String>();
        props.put("org.osgi.framework.storage", "target/osgi-store");
        props.put("org.osgi.framework.storage.clean", "onFirstInit");

        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(props);

        assertNotNull("Framework not null", framework);

        assertBundleState(Bundle.INSTALLED, framework.getState());

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        framework.stop();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    @Test
    public void testFrameworkInit() throws Exception {
        
        Framework framework = createFramework();
        assertBundleState(Bundle.INSTALLED, framework.getState());

        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        StartLevel startLevel = getStartLevel();
        assertEquals("Framework should be at Start Level 0 on init()", 0, startLevel.getStartLevel());

        PackageAdmin packageAdmin = getPackageAdmin();
        assertNotNull("The Package Admin service should be available", packageAdmin);

        // It should be possible to install a bundle into this framework, even though it's only inited...
        JavaArchive archive = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
        Bundle bundle = installBundle("simple-bundle1", toInputStream(archive));
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        shutdownFramework();
    }

    @Test
    public void testNativeCodeExecPermission() throws Exception {
        String tempFileName = System.getProperty("java.io.tmpdir") + "/osgi_native" + System.currentTimeMillis() + ".test";
        File tempFile = new File(tempFileName);

        try {
            Map<String, String> props = new HashMap<String, String>();
            props.put("org.osgi.framework.storage", "target/osgi-store");
            props.put("org.osgi.framework.storage.clean", "onFirstInit");

            // Execute this command for every native library found in the bundle
            props.put("org.osgi.framework.command.execpermission", "cp ${abspath} " + tempFileName);

            FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
            Framework framework = factory.newFramework(props);
            framework.start();

            assertFalse("Precondition", tempFile.exists());
            Bundle bundle = framework.getBundleContext().installBundle(getTestArchivePath("simple-nativecode.jar"));
            bundle.start();
            assertTrue(tempFile.exists());

            framework.stop();
            framework.waitForStop(2000);
        } finally {
            tempFile.delete();
        }
    }
}