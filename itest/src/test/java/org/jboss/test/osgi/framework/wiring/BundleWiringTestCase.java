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

import org.junit.Assert;

import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ClassAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA;
import org.jboss.test.osgi.framework.bundle.support.a.ObjectA2;
import org.jboss.test.osgi.framework.bundle.support.b.ObjectB;
import org.jboss.test.osgi.framework.bundle.support.c.ObjectC;
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
        Bundle hostB = installBundle(getHostB());
        assertEquals("hostB", hostB.getSymbolicName());
        assertEquals(Version.parseVersion("1.0.0"), hostB.getVersion());
        assertBundleState(Bundle.INSTALLED, hostB.getState());

        BundleWiring wiringB = hostB.adapt(BundleWiring.class);
        Assert.assertNull("BundleWiring null", wiringB);

        hostB.start();
        assertBundleState(Bundle.ACTIVE, hostB.getState());

        wiringB = hostB.adapt(BundleWiring.class);
        Assert.assertNotNull("BundleWiring not null", wiringB);

        hostB.stop();
        assertBundleState(Bundle.RESOLVED, hostB.getState());

        wiringB = hostB.adapt(BundleWiring.class);
        Assert.assertNotNull("BundleWiring not null", wiringB);

        hostB.uninstall();
        assertBundleState(Bundle.UNINSTALLED, hostB.getState());

        wiringB = hostB.adapt(BundleWiring.class);
        Assert.assertNull("BundleWiring null", wiringB);
    }

    @Test
    public void testExportedPackageFromFragment() throws Exception {
        XBundle hostB = (XBundle) installBundle(getHostB());
        XBundle fragB = (XBundle) installBundle(getFragB());

        hostB.start();
        assertBundleState(Bundle.ACTIVE, hostB.getState());
        assertBundleState(Bundle.RESOLVED, fragB.getState());

        BundleWiring wiringB = hostB.getBundleRevision().getWiring();
        List<BundleCapability> caps = wiringB.getCapabilities(PACKAGE_NAMESPACE);
        assertEquals("One package capability", 1, caps.size());
        assertEquals("org.jboss.osgi.fragment", caps.get(0).getAttributes().get(PACKAGE_NAMESPACE));

        Assert.assertEquals(wiringB.getProvidedWires(null), wiringB.getProvidedResourceWires(null));
        Assert.assertEquals(wiringB.getRequiredWires(null), wiringB.getRequiredResourceWires(null));
        Assert.assertEquals(wiringB.getCapabilities(null), wiringB.getResourceCapabilities(null));
        Assert.assertEquals(wiringB.getRequirements(null), wiringB.getResourceRequirements(null));

        fragB.uninstall();
        hostB.uninstall();
    }

    @Test
    public void testListResources() throws Exception {
        Bundle hostA = installBundle(getHostA());
        resolveBundles(Collections.singleton(hostA));
        BundleWiring wiringA = hostA.adapt(BundleWiring.class);
        try {
            wiringA.listResources(null, "ObjectA.class", 0);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        String objectpath = ObjectA.class.getPackage().getName().replace('.', '/');
        Collection<String> resources = wiringA.listResources(objectpath, "ObjectA.class", 0);
        Assert.assertEquals(1, resources.size());
        String expected = ObjectA.class.getName().replace('.', '/') + ".class";
        Assert.assertEquals(expected, resources.iterator().next());

        resources = wiringA.listResources(objectpath, "*.class", 0);
        Assert.assertEquals(1, resources.size());
        Assert.assertEquals(expected, resources.iterator().next());

        resources = wiringA.listResources("/org/jboss", "*.class", 0);
        Assert.assertEquals(0, resources.size());

        hostA.uninstall();
    }

    @Test
    public void testListResourcesRecursive() throws Exception {
        Bundle hostA = installBundle(getHostA());
        resolveBundles(Collections.singleton(hostA));
        BundleWiring wiringA = hostA.adapt(BundleWiring.class);
        Collection<String> resources = wiringA.listResources("/", "ObjectA.class", BundleWiring.LISTRESOURCES_RECURSE);
        Assert.assertEquals(1, resources.size());
        String expectedA = ObjectA.class.getName().replace('.', '/') + ".class";
        String expectedB = ObjectB.class.getName().replace('.', '/') + ".class";
        Assert.assertEquals(expectedA, resources.iterator().next());

        resources = wiringA.listResources("/", "*.class", BundleWiring.LISTRESOURCES_RECURSE);
        Assert.assertEquals(2, resources.size());
        Assert.assertTrue(resources.contains(expectedA));
        Assert.assertTrue(resources.contains(expectedB));

        hostA.uninstall();
    }

    @Test
    public void testListResourcesWired() throws Exception {
        Bundle hostA = installBundle(getHostA());
        Bundle hostC = installBundle(getHostC());
        resolveBundles(Arrays.asList(hostA, hostC));

        BundleWiring wiringC = hostC.adapt(BundleWiring.class);
        String objectpath = ObjectA.class.getPackage().getName().replace('.', '/');
        Collection<String> resources = wiringC.listResources(objectpath, "ObjectA.class", 0);
        Assert.assertEquals(1, resources.size());
        String expected = ObjectA.class.getName().replace('.', '/') + ".class";
        Assert.assertEquals(expected, resources.iterator().next());

        resources = wiringC.listResources("/", "ObjectA.class", BundleWiring.LISTRESOURCES_RECURSE);
        Assert.assertEquals(1, resources.size());
        Assert.assertEquals(expected, resources.iterator().next());

        resources = wiringC.listResources("/", "ObjectB.class", BundleWiring.LISTRESOURCES_RECURSE);
        Assert.assertEquals(0, resources.size());

        resources = wiringC.listResources("/", "*.class", BundleWiring.LISTRESOURCES_RECURSE | BundleWiring.LISTRESOURCES_LOCAL);
        Assert.assertEquals(0, resources.size());

        hostA.uninstall();
        hostC.uninstall();
    }

    @Test
    public void testListResourcesWithBundleClasspath() throws Exception {
        Bundle hostD = installBundle(getHostD());
        assertLoadClass(hostD, ObjectA.class.getName());
        BundleWiring wiringD = hostD.adapt(BundleWiring.class);

        String objectpath = ObjectA.class.getPackage().getName().replace('.', '/');
        Collection<String> resources = wiringD.listResources(objectpath, "ObjectA.class", 0);
        Assert.assertEquals(1, resources.size());
        String expected = ObjectA.class.getName().replace('.', '/') + ".class";
        Assert.assertEquals(expected, resources.iterator().next());

        resources = wiringD.listResources("/", "ObjectA.class", BundleWiring.LISTRESOURCES_RECURSE);
        Assert.assertEquals(1, resources.size());
        Assert.assertEquals(expected, resources.iterator().next());

        hostD.uninstall();
    }

    @Test
    public void testListResourcesWithRequiredBundle() throws Exception {
        Bundle hostA = installBundle(getHostA());
        assertLoadClass(hostA, ObjectA.class.getName());
        Bundle hostE = installBundle(getHostE());
        Bundle hostF = installBundle(getHostF());
        resolveBundles(Arrays.asList(hostE, hostF));
        assertLoadClassFail(hostF, ObjectA2.class.getName());
        assertLoadClass(hostF, ObjectA.class.getName());

        BundleWiring wiringF = hostF.adapt(BundleWiring.class);
        Collection<String> resources = wiringF.listResources("/", "*.class", BundleWiring.LISTRESOURCES_RECURSE);
        Assert.assertEquals(2, resources.size());
        String expectedA = ObjectA.class.getName().replace('.', '/') + ".class";
        String expectedB = ObjectB.class.getName().replace('.', '/') + ".class";
        Assert.assertTrue(resources.contains(expectedA));
        Assert.assertTrue(resources.contains(expectedB));

        hostA.uninstall();
        hostE.uninstall();
        hostF.uninstall();
    }

    @Test
    public void testListResourcesLocalWithPackageSubstitute() throws Exception {
        Bundle hostA = installBundle(getHostA());
        Bundle hostE = installBundle(getHostE());
        resolveBundles(Arrays.asList(hostA, hostE));
        assertLoadClassFail(hostE, ObjectA2.class.getName());
        assertLoadClass(hostE, ObjectA.class.getName());

        BundleWiring wiringE = hostE.adapt(BundleWiring.class);
        Collection<String> resources = wiringE.listResources("/", "*.class", BundleWiring.LISTRESOURCES_RECURSE | BundleWiring.LISTRESOURCES_LOCAL);
        Assert.assertEquals(0, resources.size());

        hostA.uninstall();
        hostE.uninstall();
    }

    @Test
    public void testListResourcesLocalAttachedFrament() throws Exception {
        Bundle hostA = installBundle(getHostA());
        Bundle fragA = installBundle(getFragA());
        resolveBundles(Arrays.asList(hostA, fragA));
        assertLoadClass(hostA, ObjectA.class.getName());
        assertLoadClass(hostA, ObjectC.class.getName());

        BundleWiring wiringA = hostA.adapt(BundleWiring.class);
        Collection<String> resources = wiringA.listResources("/", "*.class", BundleWiring.LISTRESOURCES_RECURSE | BundleWiring.LISTRESOURCES_LOCAL);
        Assert.assertEquals(3, resources.size());
        String expectedA = ObjectA.class.getName().replace('.', '/') + ".class";
        String expectedB = ObjectB.class.getName().replace('.', '/') + ".class";
        String expectedC = ObjectC.class.getName().replace('.', '/') + ".class";
        Assert.assertTrue(resources.contains(expectedA));
        Assert.assertTrue(resources.contains(expectedB));
        Assert.assertTrue(resources.contains(expectedC));

        hostA.uninstall();
        fragA.uninstall();
    }

    private JavaArchive getHostA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostA");
        archive.addClasses(ObjectA.class, ObjectB.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addExportPackages(ObjectA.class, ObjectB.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "fragA");
        archive.addClasses(ObjectC.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addFragmentHost("hostA");
                builder.addExportPackages(ObjectC.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostB");
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

    private JavaArchive getFragB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "fragB");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addFragmentHost("hostB");
                builder.addExportPackages("org.jboss.osgi.fragment");
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

    private JavaArchive getHostD() {
        final String classpath = "rootStuff";
        final String objectpath = ObjectA.class.getPackage().getName().replace('.', '/');
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostD");
        archive.add(new ClassAsset(ObjectA.class), classpath + "/" + objectpath, "ObjectA.class");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(ObjectA.class);
                builder.addBundleClasspath(classpath);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostE() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostE");
        archive.addClasses(ObjectA2.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addImportPackages(ObjectA.class);
                builder.addExportPackages(ObjectA.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostF() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "hostF");
        archive.addClasses(ObjectB.class);
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                final OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleVersion("1.0.0");
                builder.addRequireBundle("hostE");
                return builder.openStream();
            }
        });
        return archive;
    }
}
