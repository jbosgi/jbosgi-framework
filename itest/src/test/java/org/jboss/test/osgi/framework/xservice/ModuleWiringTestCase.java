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
package org.jboss.test.osgi.framework.xservice;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.XResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.xservice.moduleX.ModuleServiceX;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.VersionRange;
import org.osgi.framework.namespace.IdentityNamespace;

/**
 * Test Module integration.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 12-Jun-2012
 */
public class ModuleWiringTestCase extends AbstractModuleIntegrationTest {

    Module module;
    XBundleRevision brev;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        module = loadModule(getModuleA());
        brev = installResource(module);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        uninstallResource(brev);
        removeModule(module);
    }

    @Test
    public void testGetBundle() throws Exception {
        Class<?> clazz = brev.getBundle().loadClass(ModuleServiceX.class.getName());
        Assert.assertEquals(brev.getModuleClassLoader(), clazz.getClassLoader());
    }

    @Test
    public void testGetBundles() throws Exception {
        Set<XBundle> bundles = getBundleManager().getBundles(null, null);
        Assert.assertEquals(2, bundles.size());
        Assert.assertTrue(bundles.contains(getSystemContext().getBundle()));
        Assert.assertTrue(bundles.contains(brev.getBundle()));

        bundles = getBundleManager().getBundles("moduleA", null);
        Assert.assertEquals(1, bundles.size());
        Assert.assertTrue(bundles.contains(brev.getBundle()));

        bundles = getBundleManager().getBundles("moduleA", new VersionRange("[1.0,2.0)"));
        Assert.assertTrue(bundles.isEmpty());
    }

    @Test
    public void testGetBundleType() throws Exception {
        int types = brev.getTypes();
        Assert.assertEquals(0, types);

        XCapability cap = (XCapability) brev.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE).get(0);
        Assert.assertEquals(XResource.TYPE_MODULE, cap.adapt(XIdentityCapability.class).getType());
    }

    @Test
    public void testRefreshResolve() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);
        FrameworkListener listener = new FrameworkListener() {
            @Override
            public void frameworkEvent(FrameworkEvent event) {
                if (event.getType() == FrameworkEvent.PACKAGES_REFRESHED) {
                    latch.countDown();
                }
            }
        };
        getSystemContext().addFrameworkListener(listener);

        try {
            // This should be a noop
            refreshBundles(Arrays.asList((Bundle)brev.getBundle()), 0, null);
            Assert.assertTrue("FrameworkEvent.PACKAGES_REFRESHED", latch.await(10, TimeUnit.SECONDS));
        } finally {
            getSystemContext().removeFrameworkListener(listener);
        }

        // This should be a noop
        resolveBundles(Arrays.asList((Bundle)brev.getBundle()));
    }

    private JavaArchive getModuleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "moduleA");
        archive.addClasses(ModuleServiceX.class);
        return archive;
    }
}
