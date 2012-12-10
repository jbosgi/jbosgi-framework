package org.jboss.test.osgi.framework.xservice;
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

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.BundleException;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Requirement;
import org.osgi.service.resolver.ResolutionException;

/**
 * Test Module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 14-Mar-2012
 */
public class ModuleInstallTestCase extends AbstractModuleIntegrationTest {

    @Test
    public void testInstallModule() throws Exception {

        // Try to start the bundle and verify the expected ResolutionException
        XBundle bundleA = (XBundle) installBundle(getBundleA());
        try {
            bundleA.start();
            Assert.fail("BundleException expected");
        } catch (BundleException ex) {
            ResolutionException cause = (ResolutionException) ex.getCause();
            Collection<Requirement> reqs = cause.getUnresolvedRequirements();
            Assert.assertEquals(1, reqs.size());
            Requirement req = reqs.iterator().next();
            String namespace = req.getNamespace();
            Assert.assertEquals(PackageNamespace.PACKAGE_NAMESPACE, namespace);
        }

        Module moduleB = loadModule(getModuleB());
        XBundleRevision brevB = installResource(moduleB);
        try {
            XBundle bundleB = brevB.getBundle();
            Assert.assertEquals(bundleA.getBundleId() + 1, bundleB.getBundleId());

            bundleA.start();
            assertLoadClass(bundleA, ModuleServiceX.class.getName(), bundleB);

            // verify wiring for A
            XBundleRevision brevA = bundleA.getBundleRevision();
            Assert.assertSame(bundleA, brevA.getBundle());
            BundleWiring wiringA = brevA.getWiring();
            List<BundleWire> requiredA = wiringA.getRequiredWires(null);
            Assert.assertEquals(1, requiredA.size());
            BundleWire wireA = requiredA.get(0);
            Assert.assertSame(brevA, wireA.getRequirer());
            Assert.assertSame(bundleB, wireA.getProvider().getBundle());
            List<BundleWire> providedA = wiringA.getProvidedWires(null);
            Assert.assertEquals(0, providedA.size());

            // verify wiring for B
            Assert.assertSame(bundleB, brevB.getBundle());
            BundleWiring wiringB = brevB.getWiring();
            List<BundleWire> requiredB = wiringB.getRequiredWires(null);
            Assert.assertEquals(0, requiredB.size());
            List<BundleWire> providedB = wiringB.getProvidedWires(null);
            Assert.assertEquals(1, providedB.size());
            BundleWire wireB = providedB.get(0);
            Assert.assertSame(brevA, wireB.getRequirer());
            Assert.assertSame(bundleB, wireB.getProvider().getBundle());
        } finally {
            removeModule(moduleB);
        }
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages(ModuleServiceX.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getModuleB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleB");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }
}
