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

import junit.framework.Assert;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.moduleA.ModuleServiceA;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;

/**
 * Test the {@link Bundle} API for module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jun-2012
 */
public class ModuleBundleTestCase extends ModuleIntegrationTestCase {

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
        uninstallResource(bundle.getBundleRevision());
        removeModule(module);
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
        Assert.assertEquals(module.getIdentifier().toString(), bundle.getLocation());
        
        // getState
        Assert.assertEquals(Bundle.RESOLVED, bundle.getState());
        
        // [TODO] Add support for manifest header related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-567
        Assert.assertTrue(bundle.getHeaders().isEmpty());
        Assert.assertTrue(bundle.getHeaders(null).isEmpty());
        
        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        String resname = ModuleServiceA.class.getName().replace('.', '/').concat(".class");
        Assert.assertNull(bundle.getResource(resname));
        Assert.assertNull(bundle.getResources(resname));
        Assert.assertNull(bundle.findEntries(resname, null, true));
        Assert.assertNull(bundle.getEntry(resname));
        Assert.assertNull(bundle.getEntryPaths(resname));
        
        // getRegisteredServices, getServicesInUse
        Assert.assertNull(bundle.getRegisteredServices());
        Assert.assertNull(bundle.getServicesInUse());

        // loadClass
        OSGiTestHelper.assertLoadClass(bundle, ModuleServiceA.class.getName());
        
        // start, stop, uninstall, update
        try {
            bundle.start();
            Assert.fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            // expected
        }
        try {
            bundle.stop();
            Assert.fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            // expected
        }
        try {
            bundle.uninstall();
            Assert.fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            // expected
        }
        try {
            bundle.update();
            Assert.fail("UnsupportedOperationException expected");
        } catch (UnsupportedOperationException ex) {
            // expected
        }
    }

    @Test
    public void testExtendedBundle() throws Exception {
        
    }
    
    private JavaArchive getModuleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(ModuleServiceA.class);
        return archive;
    }
}