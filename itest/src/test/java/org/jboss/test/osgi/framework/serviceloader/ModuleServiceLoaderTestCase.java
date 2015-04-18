/*
 * Copyright (C) 2015 Computer Science Corporation
 * All rights reserved.
 *
 */
package org.jboss.test.osgi.framework.serviceloader;

import static org.junit.Assert.assertNotNull;

import java.util.ServiceLoader;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.testing.OSGiTestHelper;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.serviceloader.bundleA.FooServiceImpl;
import org.jboss.test.osgi.framework.serviceloader.bundleB.ObjectB;
import org.jboss.test.osgi.framework.xservice.AbstractModuleIntegrationTest;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * @author arcivanov
 */
public class ModuleServiceLoaderTestCase extends AbstractModuleIntegrationTest
{
    @Test
    public void testModuleServiceTestCase() throws Exception
    {
        JavaArchive moduleA = getModuleA();
        Module module = loadModule(moduleA);
        try {
            XBundleRevision brev = installResource(module);
            try {
                Archive<?> archiveC = assembleArchive("bundlec", "/serviceloader/bundleC", ObjectB.class);
                Bundle bundleC = installBundle(archiveC);
                try {
                    assertBundleState(Bundle.INSTALLED, bundleC.getState());
                    assertLoadClass(bundleC, FooService.class.getName());

                    Class<?> serviceClass = bundleC.loadClass(FooService.class.getName());
                    Class<?> consumerClass = bundleC.loadClass(ObjectB.class.getName());
                    ServiceLoader<?> loader = ServiceLoader.load(serviceClass, consumerClass.getClassLoader());
                    Object service = loader.iterator().next();
                    assertNotNull("Service not null", service);
                }
                finally {
                    bundleC.uninstall();
                }
            }
            finally {
                uninstallResource(brev);
            }
        }
        finally {
            removeModule(module);
        }
    }

    private JavaArchive getModuleA() throws Exception
    {
        JavaArchive archiveA =
                OSGiTestHelper.assembleArchive("moduleA", "/serviceloader/moduleA", FooService.class, FooServiceImpl.class);
        return archiveA;
    }
}
