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

import java.util.List;

import junit.framework.Assert;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;

/**
 * Test the {@link XBundleRevision} API for module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jun-2012
 */
public class ModuleBundleRevisionTestCase extends AbstractModuleIntegrationTest {

    Module module;
    XBundleRevision brev;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        module = loadModule(getModuleA());
        brev = installResource(module);
    }

    @After
    public void tearDown() throws Exception {
        uninstallResource(brev);
        removeModule(brev, module);
    }

    @Test
    public void testBundleRevision() throws Exception {

        // getBundle
        Bundle bundle = brev.getBundle();
        Assert.assertNotNull("Bundle not null", bundle);

        // getSymbolicName, getVersion
        Assert.assertEquals("moduleA", brev.getSymbolicName());
        Assert.assertEquals(Version.emptyVersion, brev.getVersion());

        // getDeclaredCapabilities
        List<BundleCapability> dcaps = brev.getDeclaredCapabilities(null);
        Assert.assertEquals("Three capabilities", 3, dcaps.size());
        BundleCapability bcap1 = brev.getDeclaredCapabilities(IdentityNamespace.IDENTITY_NAMESPACE).get(0);
        Assert.assertEquals("moduleA", bcap1.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE));
        BundleCapability bcap2 = brev.getDeclaredCapabilities(BundleNamespace.BUNDLE_NAMESPACE).get(0);
        Assert.assertEquals("moduleA", bcap2.getAttributes().get(BundleNamespace.BUNDLE_NAMESPACE));
        BundleCapability bcap3 = brev.getDeclaredCapabilities(PackageNamespace.PACKAGE_NAMESPACE).get(0);
        Assert.assertEquals(ModuleServiceX.class.getPackage().getName(), bcap3.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));

        // getDeclaredRequirements
        List<BundleRequirement> dreqs = brev.getDeclaredRequirements(null);
        Assert.assertEquals("No requirements", 0, dreqs.size());

        // getTypes
        Assert.assertEquals("Type is 0", 0, brev.getTypes());

        // getWiring
        BundleWiring wiring = brev.getWiring();
        Assert.assertNotNull("BundleWiring not null", wiring);

        // getCapabilities
        List<Capability> caps = brev.getCapabilities(null);
        Assert.assertEquals("Three capabilities", 3, caps.size());
        Capability cap1 = brev.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE).get(0);
        Assert.assertEquals("moduleA", cap1.getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE));
        Capability cap2 = brev.getCapabilities(BundleNamespace.BUNDLE_NAMESPACE).get(0);
        Assert.assertEquals("moduleA", cap2.getAttributes().get(BundleNamespace.BUNDLE_NAMESPACE));
        Capability cap3 = brev.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE).get(0);
        Assert.assertEquals(ModuleServiceX.class.getPackage().getName(), cap3.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));

        // getRequirements
        List<Requirement> reqs = brev.getRequirements(null);
        Assert.assertEquals("No requirements", 0, reqs.size());
    }

    @Test
    public void testExtendedBundleRevision() throws Exception {

        // getBundle
        XBundle bundle = brev.getBundle();
        Assert.assertNotNull("Bundle not null", bundle);

        // getModuleIdentifier, getModuleClassLoader
        Assert.assertEquals(ModuleIdentifier.create("moduleA"), brev.getModuleIdentifier());
        Assert.assertNotNull("ModuleClassLoader not null", brev.getModuleClassLoader());

        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        String resname = ModuleServiceX.class.getName().replace('.', '/').concat(".class");
        Assert.assertNull(brev.getResource(resname));
        Assert.assertNull(brev.getResources(resname));
        Assert.assertNull(brev.findEntries(resname, null, true));
        Assert.assertNull(brev.getEntry(resname));
        Assert.assertNull(brev.getEntryPaths(resname));
    }

    private JavaArchive getModuleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }
}
