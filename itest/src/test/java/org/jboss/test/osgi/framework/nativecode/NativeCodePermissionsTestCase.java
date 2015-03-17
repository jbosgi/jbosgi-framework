package org.jboss.test.osgi.framework.nativecode;
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * Test native code permissions.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Apr-2010
 */
public class NativeCodePermissionsTestCase extends OSGiFrameworkTest {

    @BeforeClass
    public static void beforeClass() {
        // prevent framework creation
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
            props.put("org.osgi.framework.command.execpermission", "cp '${abspath}' '" + tempFileName + "'");

            FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
            Framework framework = factory.newFramework(props);
            framework.start();

            assertFalse("Precondition", tempFile.exists());
            Bundle bundle = framework.getBundleContext().installBundle(getTestArchivePath("simple-nativecode.jar"));
            bundle.start();
            assertTrue(tempFile.toString() + " does not exist", tempFile.exists());

            framework.stop();
            framework.waitForStop(2000);
        } finally {
            tempFile.delete();
        }
    }
}