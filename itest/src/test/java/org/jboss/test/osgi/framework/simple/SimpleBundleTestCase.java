package org.jboss.test.osgi.framework.simple;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;

import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleC.SimpleActivator;
import org.jboss.test.osgi.framework.simple.bundleC.SimpleService;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * A test that deployes a bundle and verifies its state
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class SimpleBundleTestCase extends OSGiFrameworkTest {

    @Test
    public void testBundleLifecycle() throws Exception {
        Bundle bundle = installBundle(getBundleArchive());
        assertEquals("simple-bundle", bundle.getSymbolicName());
        assertEquals(Version.parseVersion("1.0.0"), bundle.getVersion());
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        bundle.start();
        assertBundleState(Bundle.ACTIVE, bundle.getState());

        BundleContext context = bundle.getBundleContext();
        assertNotNull("BundleContext not null", context);

        ServiceReference sref = context.getServiceReference(SimpleService.class.getName());
        assertNotNull("ServiceReference not null", sref);

        Object service = context.getService(sref);
        assertEquals(SimpleService.class.getName(), service.getClass().getName());

        bundle.stop();
        assertBundleState(Bundle.RESOLVED, bundle.getState());

        sref = getSystemContext().getServiceReference(SimpleService.class.getName());
        assertNull("ServiceReference null", sref);

        bundle.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundle.getState());
    }

    private JavaArchive getBundleArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
        archive.addClasses(SimpleService.class, SimpleActivator.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(SimpleActivator.class);
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}