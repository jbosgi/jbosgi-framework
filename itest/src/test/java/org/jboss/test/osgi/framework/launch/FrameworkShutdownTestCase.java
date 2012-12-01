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

import static org.junit.Assert.assertEquals;
import java.io.InputStream;
import java.util.Map;

import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.osgi.framework.spi.FrameworkBuilderFactory;
import org.jboss.osgi.framework.spi.FrameworkBuilder;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

/**
 * Test the {@link Framework#waitForStop(long)}
 *
 * @author thomas.diesler@jboss.com
 * @since 27-Sep-2012
 */
public class FrameworkShutdownTestCase extends AbstractFrameworkLaunchTest {

    @Test
    public void testFrameworkStopWithTimeout() throws Exception {
        Map<String, String> props = getFrameworkInitProperties(true);
        FrameworkBuilder builder = FrameworkBuilderFactory.create(props, Mode.ACTIVE);
        Framework framework = newFramework(builder);
        assertBundleState(Bundle.INSTALLED, framework.getState());

        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        Bundle bundle = installBundle(getBundleA());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        framework.stop();
        FrameworkEvent stopEvent = framework.waitForStop(2000);
        assertEquals(FrameworkEvent.STOPPED, stopEvent.getType());
    }

    @Test
    public void testFrameworkStopNoTimeout() throws Exception {
        Map<String, String> props = getFrameworkInitProperties(true);
        FrameworkBuilder builder = FrameworkBuilderFactory.create(props, Mode.ACTIVE);
        Framework framework = newFramework(builder);
        assertBundleState(Bundle.INSTALLED, framework.getState());

        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        Bundle bundle = installBundle(getBundleA());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        framework.stop();
        FrameworkEvent stopEvent = framework.waitForStop(0);
        assertEquals(FrameworkEvent.STOPPED, stopEvent.getType());
    }

    @Test
    public void testSystemBundleStopWithTimeout() throws Exception {
        Map<String, String> props = getFrameworkInitProperties(true);
        FrameworkBuilder builder = FrameworkBuilderFactory.create(props, Mode.ACTIVE);
        Framework framework = newFramework(builder);
        assertBundleState(Bundle.INSTALLED, framework.getState());

        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        Bundle bundle = installBundle(getBundleA());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        Bundle sysbundle = framework.getBundleContext().getBundle();
        sysbundle.stop();

        FrameworkEvent stopEvent = framework.waitForStop(2000);
        assertEquals(FrameworkEvent.STOPPED, stopEvent.getType());
    }

    @Test
    public void testSystemBundleStopNoTimeout() throws Exception {
        Map<String, String> props = getFrameworkInitProperties(true);
        FrameworkBuilder builder = FrameworkBuilderFactory.create(props, Mode.ACTIVE);
        Framework framework = newFramework(builder);
        assertBundleState(Bundle.INSTALLED, framework.getState());

        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        Bundle bundle = installBundle(getBundleA());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        Bundle sysbundle = framework.getBundleContext().getBundle();
        sysbundle.stop();

        FrameworkEvent stopEvent = framework.waitForStop(0);
        assertEquals(FrameworkEvent.STOPPED, stopEvent.getType());
    }

    private static JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
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