/*
 * #%L
 * JBossOSGi Framework iTest
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
package org.jboss.test.osgi.framework.xservice;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.framework.AbstractBundleRevisionAdaptor;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XBundleRevisionBuilderFactory;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.XResourceBuilder;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleRevision;
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
public class ModuleInstallTestCase extends OSGiFrameworkTest {

    @Test
    public void testInstallModule() throws Exception {

        // Try to start the bundle and verify the expected ResolutionException
        Bundle bundleA = installBundle(getBundleA());
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
            Assert.assertEquals("javax.inject", req.getAttributes().get(namespace));
        }

        // Build the Module resource
        final BundleContext context = getSystemContext();
        final ModuleIdentifier identifier = ModuleIdentifier.create("javax.inject.api");
        final Module module = Module.getBootModuleLoader().loadModule(identifier);
        XBundleRevisionBuilderFactory factory = new XBundleRevisionBuilderFactory() {
            public XBundleRevision createResource() {
                return new AbstractBundleRevisionAdaptor(context, module);
            }
        };
        XResourceBuilder builder = XBundleRevisionBuilderFactory.create(factory);
        XResource res = builder.loadFrom(module).getResource();

        Assert.assertEquals(3, res.getCapabilities(null).size());
        Assert.assertEquals(1, res.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE).size());
        Assert.assertEquals(2, res.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE).size());
        Assert.assertEquals("javax.inject.api", res.getIdentityCapability().getSymbolicName());
        Assert.assertEquals(Version.emptyVersion, res.getIdentityCapability().getVersion());
        Assert.assertEquals(IdentityNamespace.TYPE_UNKNOWN, res.getIdentityCapability().getType());

        // Install the resource into the environment
        XBundle sysbundle = (XBundle) getSystemContext().getBundle();
        XEnvironment env = sysbundle.adapt(XEnvironment.class);
        env.installResources(res);

        bundleA.start();
        assertLoadClass(bundleA, Inject.class.getName());

        XBundleRevision brevA = ((XBundle)bundleA).getBundleRevision();
        BundleWiring wiring = brevA.getWiring();
        List<BundleWire> required = wiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE);
        Assert.assertEquals(1, required.size());
        BundleWire wire = required.get(0);
        Assert.assertSame(brevA, wire.getRequirer());
        Assert.assertSame(bundleA, brevA.getBundle());
        BundleRevision brevB = wire.getProvider();
        Assert.assertSame(res, brevB);
        Bundle bundleB = brevB.getBundle();
        Assert.assertEquals("javax.inject.api", bundleB.getSymbolicName());
        Assert.assertEquals("javax.inject.api:main", bundleB.getLocation());
        Assert.assertEquals(Version.emptyVersion, bundleB.getVersion());
        Assert.assertEquals(bundleA.getBundleId() + 1, bundleB.getBundleId());
    }

    private JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addImportPackages("javax.inject");
                return builder.openStream();
            }
        });
        return archive;
    }
}