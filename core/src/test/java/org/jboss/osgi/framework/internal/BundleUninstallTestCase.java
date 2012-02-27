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
package org.jboss.osgi.framework.internal;

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.subA.SimpleActivator;
import org.jboss.test.osgi.framework.subA.SimpleService;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.osgi.framework.Bundle.INSTALLED;
import static org.osgi.framework.Bundle.RESOLVED;
import static org.osgi.framework.Bundle.UNINSTALLED;

/**
 * Verify bundle services on install/uninstall.
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Mar-2011
 */
public class BundleUninstallTestCase extends AbstractFrameworkTest {

    @Test
    public void testBundleLifecycle() throws Exception {

        List<ServiceName> initialNames = getServiceNameDelta(null);

        Bundle bundle = installBundle(getTestArchive());
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);

        List<ServiceName> additionalNames = getServiceNameDelta(initialNames);
        assertTrue("Contains INSTALLED", additionalNames.contains(bundleState.getServiceName(INSTALLED)));
        assertTrue("Contains RESOLVED", additionalNames.contains(bundleState.getServiceName(RESOLVED)));
        assertTrue("Contains ACTIVE", additionalNames.contains(bundleState.getServiceName(Bundle.ACTIVE)));

        bundle.uninstall();
        assertBundleState(UNINSTALLED, bundle.getState());

        additionalNames = getServiceNameDelta(initialNames);
        assertEquals("No additional services: " + additionalNames, 0, additionalNames.size());
    }

    private List<ServiceName> getServiceNameDelta(List<ServiceName> initialNames) throws Exception {
        ServiceContainer serviceContainer = getBundleManager().getServiceContainer();
        Thread.sleep(200); // wait a little for the services

        if (initialNames == null)
            return serviceContainer.getServiceNames();

        List<ServiceName> deltaNames = new ArrayList<ServiceName>();
        for (ServiceName serviceName : serviceContainer.getServiceNames()) {
            if (initialNames.contains(serviceName) == false)
                deltaNames.add(serviceName);
        }
        return deltaNames;
    }

    private JavaArchive getTestArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
        archive.addClasses(SimpleService.class, SimpleActivator.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addBundleActivator(SimpleActivator.class);
                builder.addImportPackages("org.osgi.framework");
               return builder.openStream();
            }
        });
        return archive;
    }
}