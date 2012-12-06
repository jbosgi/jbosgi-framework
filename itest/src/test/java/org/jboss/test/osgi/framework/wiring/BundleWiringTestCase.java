package org.jboss.test.osgi.framework.wiring;
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
import static org.osgi.framework.namespace.PackageNamespace.PACKAGE_NAMESPACE;

import java.io.InputStream;
import java.util.List;

import junit.framework.Assert;

import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;

/**
 * Test {@link BundleWiring} API
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Jun-2012
 */
public class BundleWiringTestCase extends OSGiFrameworkTest {

    @Test
    public void testExportedPackageFromFragment() throws Exception {
        XBundle hostA = (XBundle) installBundle(getHostA());
        XBundle fragmentA = (XBundle) installBundle(getFragmentA());

        hostA.start();
        assertBundleState(Bundle.ACTIVE, hostA.getState());
        assertBundleState(Bundle.RESOLVED, fragmentA.getState());

        BundleWiring wiring = hostA.getBundleRevision().getWiring();
        List<BundleCapability> caps = wiring.getCapabilities(PACKAGE_NAMESPACE);
        assertEquals("One package capability", 1, caps.size());
        assertEquals("org.jboss.osgi.fragment", caps.get(0).getAttributes().get(PACKAGE_NAMESPACE));

        Assert.assertEquals(wiring.getProvidedWires(null), wiring.getProvidedResourceWires(null));
        Assert.assertEquals(wiring.getRequiredWires(null), wiring.getRequiredResourceWires(null));
        Assert.assertEquals(wiring.getCapabilities(null), wiring.getResourceCapabilities(null));
        Assert.assertEquals(wiring.getRequirements(null), wiring.getResourceRequirements(null));
    }

    private JavaArchive getHostA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostA");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "fragmentA");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addFragmentHost("hostA");
                builder.addExportPackages("org.jboss.osgi.fragment");
                return builder.openStream();
            }
        });
        return archive;
    }
}
