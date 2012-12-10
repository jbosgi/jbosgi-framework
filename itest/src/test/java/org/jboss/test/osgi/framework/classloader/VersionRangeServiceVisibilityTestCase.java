/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Inc., and individual contributors as indicated
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
package org.jboss.test.osgi.framework.classloader;

import java.io.InputStream;

import junit.framework.Assert;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.classloader.support.ConfigurationAdminActivator;
import org.jboss.test.osgi.framework.classloader.support.DeclarativeServicesActivator;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Test imports with version range
 *
 * enterprise
 *  Export-Package: org.osgi.service.cm;version=1.5
 *
 * felix-configadmin
 *  Export-Package: org.osgi.service.cm;version=1.4
 *  Import-Package: org.osgi.service.cm;version=[1.4,1.5)
 *
 * felix-scr
 *  Import-Package: org.osgi.service.cm;version=[1.2,2.0);resolution:=optional
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Oct-2012
 */
public class VersionRangeServiceVisibilityTestCase extends OSGiFrameworkTest {

    static final String BUNDLE_A = "enterprise";
    static final String BUNDLE_B = "felix-configadmin";
    static final String BUNDLE_C = "felix-scr";

    @Test
    public void testBundleWiring() throws Exception {
        Bundle bundleA = installBundle(getBundleA());
        Bundle bundleB = installBundle(getBundleB());
        Bundle bundleC = installBundle(getBundleC());
        try {
            bundleA.start();
            bundleB.start();
            bundleC.start();

            // Verify that 'org.osgi.service.cm' wires to enterprise
            BundleWiring wiring = ((XBundle)bundleC).getBundleRevision().getWiring();
            for (BundleWire wire : wiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE)) {
                XPackageRequirement preq = (XPackageRequirement) wire.getRequirement();
                if (preq.getPackageName().equals("org.osgi.service.cm")) {
                    Bundle provider = wire.getProvider().getBundle();
                    Assert.assertSame(bundleA, provider);
                }
            }

            // Verify that felix-scr does not see the service provided by felix-configadmin
            BundleContext contextC = bundleC.getBundleContext();
            ServiceReference<?> sref = contextC.getServiceReference(ConfigurationAdmin.class.getName());
            Assert.assertNull("ServiceReference null", sref);

        } finally {
            bundleC.uninstall();
            bundleB.uninstall();
            bundleA.uninstall();
        }
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_A);
        archive.addClasses(ConfigurationAdmin.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addExportPackages("org.osgi.service.cm;version=1.5");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_B);
        archive.addClasses(ConfigurationAdmin.class, ConfigurationAdminActivator.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(ConfigurationAdminActivator.class);
                builder.addImportPackages("org.osgi.service.cm;version='[1.4,1.5)'");
                builder.addExportPackages("org.osgi.service.cm;version=1.4");
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getBundleC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, BUNDLE_C);
        archive.addClasses(DeclarativeServicesActivator.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addBundleActivator(DeclarativeServicesActivator.class);
                builder.addImportPackages("org.osgi.service.cm;version='[1.2,2.0)';resolution:=optional");
                builder.addImportPackages(BundleActivator.class);
                return builder.openStream();
            }
        });
        return archive;
    }
}
