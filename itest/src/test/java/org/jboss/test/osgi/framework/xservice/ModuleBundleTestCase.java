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

import junit.framework.Assert;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Test the {@link Bundle} API for module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jun-2012
 */
public class ModuleBundleTestCase extends AbstractModuleIntegrationTest {

    Module module;
    XBundle bundle;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        module = loadModule(getModuleA());
        bundle = installResource(module).getBundle();
    }

    @After
    public void tearDown() throws Exception {
        XBundleRevision brev = bundle.getBundleRevision();
        uninstallResource(brev);
        removeModule(brev, module);
    }

    @Test
    public void testBundle() throws Exception {

        // getBundleContext
        BundleContext context = bundle.getBundleContext();
        Assert.assertEquals(getSystemContext(), context);

        // getBundleId, getLastModified, getSymbolicName, getVersion, getLocation
        Assert.assertTrue("Bundle id > 0", bundle.getBundleId() > 0);
        Assert.assertTrue("Last modified > 0", bundle.getLastModified() > 0);
        Assert.assertEquals(module.getIdentifier().getName(), bundle.getSymbolicName());
        Assert.assertEquals(Version.emptyVersion, bundle.getVersion());
        Assert.assertEquals(module.getIdentifier().getName(), bundle.getLocation());

        // getState
        Assert.assertEquals(Bundle.RESOLVED, bundle.getState());

        // [TODO] Add support for manifest header related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-567
        Assert.assertTrue(bundle.getHeaders().isEmpty());
        Assert.assertTrue(bundle.getHeaders(null).isEmpty());

        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        String resname = ModuleServiceX.class.getName().replace('.', '/').concat(".class");
        Assert.assertNull(bundle.getResource(resname));
        Assert.assertNull(bundle.getResources(resname));
        Assert.assertNull(bundle.findEntries(resname, null, true));
        Assert.assertNull(bundle.getEntry(resname));
        Assert.assertNull(bundle.getEntryPaths(resname));

        // getRegisteredServices, getServicesInUse
        Assert.assertNull(bundle.getRegisteredServices());
        Assert.assertNull(bundle.getServicesInUse());

        // loadClass
        OSGiTestHelper.assertLoadClass(bundle, ModuleServiceX.class.getName());

        bundle.start();
        Assert.assertEquals(Bundle.ACTIVE, bundle.getState());

        bundle.stop();
        Assert.assertEquals(Bundle.RESOLVED, bundle.getState());

        try {
            bundle.update();
            Assert.fail("BundleException expected");
        } catch (BundleException ex) {
            // expected
        }

        // uninstall
        bundle.uninstall();
        Assert.assertEquals(Bundle.UNINSTALLED, bundle.getState());
    }

    @Test
    public void testExtendedBundle() throws Exception {

    }

    private JavaArchive getModuleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }
}
