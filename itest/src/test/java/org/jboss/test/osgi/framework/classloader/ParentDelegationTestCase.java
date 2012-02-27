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
package org.jboss.test.osgi.framework.classloader;

import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTest;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import javax.security.auth.x500.X500Principal;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

/**
 * Test bundle parent delegation
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Jan-2011
 */
public class ParentDelegationTestCase extends OSGiTest {

    @Test
    public void testNoBundleParent() throws Exception {
        doTestParentClassLoader(null, Bundle.class.getName(), false);
    }

    @Test
    public void testBundleParentBoot() throws Exception {
        doTestParentClassLoader(Constants.FRAMEWORK_BUNDLE_PARENT_BOOT, Bundle.class.getName(), false);
    }

    @Test
    public void testBundleParentExt() throws Exception {
        doTestParentClassLoader(Constants.FRAMEWORK_BUNDLE_PARENT_EXT, Bundle.class.getName(), false);
    }

    @Test
    public void testBundleParentApp() throws Exception {
        // here we assume the framework jar was placed on the app class loader.
        doTestParentClassLoader(Constants.FRAMEWORK_BUNDLE_PARENT_APP, Bundle.class.getName(), true);
    }

    @Test
    public void testBundleParentFramework() throws Exception {
        // here we assume the framework jar was placed on the app class loader.
        doTestParentClassLoader(Constants.FRAMEWORK_BUNDLE_PARENT_FRAMEWORK, Bundle.class.getName(), true);
    }

    private void doTestParentClassLoader(String parentType, String className, boolean pass) throws Exception {
        Map<String, String> configuration = new HashMap<String, String>();
        configuration.put("org.osgi.framework.storage", "target/osgi-store");
        configuration.put("org.osgi.framework.storage.clean", "onFirstInit");
        configuration.put(Constants.FRAMEWORK_BOOTDELEGATION, "*");
        if (parentType != null)
            configuration.put(Constants.FRAMEWORK_BUNDLE_PARENT, parentType);

        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(configuration);
        framework.start();
        try {
            BundleContext sysContext = framework.getBundleContext();
            InputStream inputStream = OSGiTestHelper.toInputStream(getTestArchive());
            Bundle testBundle = sysContext.installBundle("http://parentdelegation", inputStream);
            try {
                testBundle.loadClass(className);
                if (!pass)
                    fail("Should not be able to load class: " + className);
            } catch (ClassNotFoundException e) {
                if (pass)
                    fail("Unexpected ClassNotFoundException");
            }
            finally
            {
                testBundle.uninstall();
            }
        } finally {
            framework.stop();
            framework.waitForStop(10000);
        }
    }

    private JavaArchive getTestArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "parentdelegation");
        archive.addClasses(X500Principal.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                return builder.openStream();
            }
        });
        return archive;
    }
}
