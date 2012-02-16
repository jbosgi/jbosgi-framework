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
package org.jboss.osgi.framework.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.FutureServiceValue;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XResolver;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * Test the {@link BundleManager#installBundle(ServiceTarget, Module)} API.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 28-Sep-2010
 */
public class InstallFromModuleTestCase extends AbstractFrameworkTest {

    @Test
    public void testInstallModule() throws Exception {
        assertFrameworkState();

        ModuleIdentifier identifier = ModuleIdentifier.create("javax.inject.api");
        Module module = Module.getBootModuleLoader().loadModule(identifier);
        ServiceContainer serviceContainer = getBundleManager().getServiceContainer();
        ServiceTarget serviceTarget = serviceContainer.subTarget();
        ServiceName serviceName = getBundleManager().registerModule(serviceTarget, module, null);

        Bundle bundle = getBundleFromService(serviceContainer, serviceName);
        assertBundleState(Bundle.INSTALLED, bundle.getState());

        assertLoadClass(bundle, Inject.class.getName());
        assertBundleState(Bundle.RESOLVED, bundle.getState());

        bundle.start();
        assertBundleState(Bundle.ACTIVE, bundle.getState());

        bundle.stop();
        assertBundleState(Bundle.RESOLVED, bundle.getState());

        bundle.uninstall();
        assertNull("Bundle null", getSystemContext().getBundle(bundle.getBundleId()));
    }

    private void assertFrameworkState() throws BundleException {
        BundleContext context = getSystemContext();
        assertNotNull("Framework active", context);

        Bundle[] bundles = context.getBundles();
        assertEquals("System bundle available", 1, bundles.length);
        assertEquals("System bundle id", 0, bundles[0].getBundleId());

        LegacyResolverPlugin resolverPlugin = getFrameworkState().getLegacyResolverPlugin();
        XResolver resolver = resolverPlugin.getResolver();
        Set<XModule> modules = resolver.getModules();
        assertEquals("System module available", 1, modules.size());
    }

    @SuppressWarnings("unchecked")
    private Bundle getBundleFromService(ServiceContainer serviceContainer, ServiceName serviceName) throws ExecutionException, TimeoutException {
        ServiceController<UserBundleState> controller = (ServiceController<UserBundleState>) serviceContainer.getService(serviceName);
        FutureServiceValue<UserBundleState> future = new FutureServiceValue<UserBundleState>(controller);
        UserBundleState userBundle = future.get(5, TimeUnit.SECONDS);
        return userBundle;
    }
}