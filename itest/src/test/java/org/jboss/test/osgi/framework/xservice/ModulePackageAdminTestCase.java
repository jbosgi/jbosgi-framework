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
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Test Module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jun-2012
 */
public class ModulePackageAdminTestCase extends AbstractModuleIntegrationTest {

    Module module;
    XBundleRevision brev;
    PackageAdmin packageAdmin;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        module = loadModule(getModuleA());
        brev = installResource(module);
        packageAdmin = getPackageAdmin();
    }

    @After
    public void tearDown() throws Exception {
        uninstallResource(brev);
        removeModule(brev, module);
    }

    @Test
    public void testGetBundle() throws Exception {
        XBundle bundle = brev.getBundle();
        Class<?> clazz = bundle.loadClass(ModuleServiceX.class.getName());
        Assert.assertEquals(bundle, packageAdmin.getBundle(clazz));
    }

    @Test
    public void testGetBundles() throws Exception {
        Bundle[] bundles = packageAdmin.getBundles(null, null);
        Assert.assertEquals(2, bundles.length);
        Assert.assertEquals(getSystemContext().getBundle(), bundles[0]);
        Assert.assertEquals(brev.getBundle(), bundles[1]);

        bundles = packageAdmin.getBundles("moduleA", null);
        Assert.assertEquals(1, bundles.length);
        Assert.assertEquals(brev.getBundle(), bundles[0]);

        bundles = packageAdmin.getBundles("moduleA", "[1.0,2.0)");
        Assert.assertNull(bundles);
    }

    @Test
    public void testGetBundleType() throws Exception {
        int types = packageAdmin.getBundleType(brev.getBundle());
        Assert.assertEquals(0, types);

        types = brev.getTypes();
        Assert.assertEquals(0, types);

        XCapability cap = (XCapability) brev.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE).get(0);
        Assert.assertEquals(IdentityNamespace.TYPE_UNKNOWN, cap.adapt(XIdentityCapability.class).getType());
    }

    @Test
    public void testGetExportedPackages() throws Exception {
        ExportedPackage[] exported = packageAdmin.getExportedPackages(brev.getBundle());
        Assert.assertEquals(1, exported.length);
        ExportedPackage exp = exported[0];
        Assert.assertEquals(ModuleServiceX.class.getPackage().getName(), exp.getName());
        Assert.assertEquals(brev.getBundle(), exp.getExportingBundle());
        Assert.assertEquals(0, exp.getImportingBundles().length);
        Assert.assertEquals(Version.emptyVersion, exp.getVersion());
        Assert.assertFalse(exp.isRemovalPending());
    }

    @Test
    public void testRefreshResolve() throws Exception {
        // These should be noops
        packageAdmin.refreshPackages(new Bundle[] {brev.getBundle()});
        packageAdmin.resolveBundles(new Bundle[] {brev.getBundle()});
    }

    private JavaArchive getModuleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }
}
