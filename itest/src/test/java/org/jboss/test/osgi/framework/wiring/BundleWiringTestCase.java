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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import junit.framework.Assert;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Test {@link BundleWiring} API
 *
 * @author thomas.diesler@jboss.com
 * @since 28-Jun-2012
 */
public class BundleWiringTestCase extends OSGiFrameworkTest {

    @Test
    public void testSimpleBundleWiring() throws Exception {
        Bundle hostA = installBundle(getHostA());
        assertEquals("hostA", hostA.getSymbolicName());
        assertEquals(Version.parseVersion("1.0.0"), hostA.getVersion());
        assertBundleState(Bundle.INSTALLED, hostA.getState());

        BundleWiring wiring = hostA.adapt(BundleWiring.class);
        Assert.assertNull("BundleWiring null", wiring);

        hostA.start();
        assertBundleState(Bundle.ACTIVE, hostA.getState());

        wiring = hostA.adapt(BundleWiring.class);
        Assert.assertNotNull("BundleWiring not null", wiring);

        hostA.stop();
        assertBundleState(Bundle.RESOLVED, hostA.getState());

        wiring = hostA.adapt(BundleWiring.class);
        Assert.assertNotNull("BundleWiring not null", wiring);

        hostA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, hostA.getState());

        wiring = hostA.adapt(BundleWiring.class);
        Assert.assertNull("BundleWiring null", wiring);
    }

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

    @Test
    public void testListResources() throws Exception {
        Bundle hostB = installBundle(getHostB());
        resolveBundles(Collections.singleton(hostB));
        BundleWiring wiring = hostB.adapt(BundleWiring.class);
        try {
            wiring.listResources(null, "ObjectA.class", 0);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        Collection<String> resources = wiring.listResources("/", "ObjectA.class", BundleWiring.LISTRESOURCES_LOCAL);
        Assert.assertEquals(1, resources.size());
        String expected = "/" + ObjectA.class.getName().replace('.', '/') + ".class";
        Assert.assertEquals(expected, resources.iterator().next());
    }

    @Test
    public void testListResourcesRecursive() throws Exception {
        Bundle hostB = installBundle(getHostB());
        Bundle hostC = installBundle(getHostC());
        resolveBundles(Arrays.asList(hostB, hostC));

        BundleWiring wiring = hostC.adapt(BundleWiring.class);
        Collection<String> resources = wiring.listResources("/", "ObjectA.class", BundleWiring.LISTRESOURCES_RECURSE);
        Assert.assertEquals(1, resources.size());
        String expected = "/" + ObjectA.class.getName().replace('.', '/') + ".class";
        Assert.assertEquals(expected, resources.iterator().next());
    }

    private JavaArchive getHostA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostA");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostB");
        archive.addClasses(ObjectA.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addExportPackages(ObjectA.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostC");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addImportPackages(ObjectA.class);
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
