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
package org.jboss.test.osgi.framework.launch;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.ValueService;
import org.jboss.msc.value.ImmediateValue;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.FrameworkBuilder;
import org.jboss.osgi.framework.spi.FrameworkBuilder.FrameworkPhase;
import org.jboss.osgi.framework.spi.FrameworkBuilderFactory;
import org.jboss.osgi.framework.spi.FutureServiceValue;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.FrameworkStartLevel;

/**
 * Test the {@link FrameworkBuilder}
 *
 * @author thomas.diesler@jboss.com
 * @since 14-Jul-2012
 */
public class FrameworkBuilderTestCase extends AbstractFrameworkLaunchTest {

    @Test
    public void testFrameworkInit() throws Exception {

        Map<String, String> props = getFrameworkInitProperties(true);
        FrameworkBuilder builder = FrameworkBuilderFactory.create(props, Mode.ACTIVE);
        Framework framework = newFramework(builder);
        assertBundleState(Bundle.INSTALLED, framework.getState());

        Assert.assertNull("ServiceContainer is null", getServiceContainer());

        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        assertServiceState(State.UP, IntegrationServices.BOOTSTRAP_BUNDLES_COMPLETE);
        assertServiceState(State.UP, IntegrationServices.PERSISTENT_BUNDLES_COMPLETE);
        assertServiceState(State.UP, Services.FRAMEWORK_INIT);
        Assert.assertNull(getService(Services.FRAMEWORK_ACTIVE));

        FrameworkStartLevel startLevel = framework.adapt(FrameworkStartLevel.class);
        Assert.assertEquals("Framework should be at Start Level 0 on init()", 0, startLevel.getStartLevel());

        // It should be possible to install a bundle into this framework, even though it's only inited...
        Bundle bundle = installBundle(getBundleA());
        assertBundleState(Bundle.INSTALLED, bundle.getState());
        Assert.assertNotNull("BundleContext not null", framework.getBundleContext());

        framework.stop();
        Assert.assertNull("BundleContext null", framework.getBundleContext());
        FrameworkEvent stopEvent = framework.waitForStop(2000);
        Assert.assertEquals(FrameworkEvent.STOPPED, stopEvent.getType());

        Assert.assertNull("ServiceContainer is null", getServiceContainer());
    }

    @Test
    public void testFrameworkStartStop() throws Exception {

        Map<String, String> props = getFrameworkInitProperties(true);
        FrameworkBuilder builder = FrameworkBuilderFactory.create(props, Mode.ACTIVE);
        Framework framework = newFramework(builder);

        Assert.assertNotNull("Framework not null", framework);
        assertBundleState(Bundle.INSTALLED, framework.getState());

        Assert.assertNull("ServiceContainer is null", getServiceContainer());

        framework.init();
        assertBundleState(Bundle.STARTING, framework.getState());

        assertServiceState(State.UP, IntegrationServices.BOOTSTRAP_BUNDLES_COMPLETE);
        assertServiceState(State.UP, IntegrationServices.PERSISTENT_BUNDLES_COMPLETE);
        assertServiceState(State.UP, Services.FRAMEWORK_INIT);
        Assert.assertNull(getService(Services.FRAMEWORK_ACTIVE));

        BundleContext systemContext = framework.getBundleContext();
        Assert.assertNotNull("BundleContext not null", systemContext);
        Bundle systemBundle = systemContext.getBundle();
        Assert.assertNotNull("Bundle not null", systemBundle);
        Assert.assertEquals("System bundle id", 0, systemBundle.getBundleId());
        Assert.assertEquals("System bundle name", Constants.FRAMEWORK_SYMBOLIC_NAME, systemBundle.getSymbolicName());
        Assert.assertEquals("System bundle location", Constants.FRAMEWORK_LOCATION, systemBundle.getLocation());

        Bundle[] bundles = systemContext.getBundles();
        Assert.assertEquals("System bundle available", 1, bundles.length);
        Assert.assertEquals("System bundle id", 0, bundles[0].getBundleId());
        Assert.assertEquals("System bundle name", Constants.FRAMEWORK_SYMBOLIC_NAME, bundles[0].getSymbolicName());
        Assert.assertEquals("System bundle location", Constants.FRAMEWORK_LOCATION, bundles[0].getLocation());

        FrameworkStartLevel startLevel = framework.adapt(FrameworkStartLevel.class);
        Assert.assertEquals("Framework start level", 0, startLevel.getStartLevel());

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());
        assertServiceState(State.UP, Services.FRAMEWORK_ACTIVE);

        framework.stop();
        FrameworkEvent stopEvent = framework.waitForStop(2000);
        Assert.assertEquals(FrameworkEvent.STOPPED, stopEvent.getType());
        assertBundleState(Bundle.RESOLVED, framework.getState());

        Assert.assertNull("ServiceContainer is null", getServiceContainer());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFrameworkServices() throws Exception {

        Map<String, String> props = getFrameworkInitProperties(true);
        FrameworkBuilder builder = FrameworkBuilderFactory.create(props, Mode.ACTIVE);
        ServiceContainer serviceContainer = builder.createServiceContainer();
        ServiceTarget serviceTarget = serviceContainer.subTarget();
        builder.createFrameworkServices(serviceContainer, true);
        builder.installServices(FrameworkPhase.CREATE, serviceTarget, Mockito.mock(ServiceListener.class));
        builder.installServices(FrameworkPhase.INIT, serviceTarget, Mockito.mock(ServiceListener.class));

        assertServiceState(serviceContainer, State.DOWN, Services.FRAMEWORK_INIT);
        assertServiceState(serviceContainer, State.DOWN, IntegrationServices.BOOTSTRAP_BUNDLES_INSTALL);
        assertServiceState(serviceContainer, State.DOWN, IntegrationServices.PERSISTENT_BUNDLES_INSTALL);
        Assert.assertNull(serviceContainer.getService(Services.FRAMEWORK_ACTIVE));

        // Register a service that has a dependency on {@link FrameworkActive}
        ServiceName serviceName = ServiceName.parse("someService");
        ValueService<Boolean> service = new ValueService<Boolean>(new ImmediateValue<Boolean>(true));
        ServiceBuilder<Boolean> serviceBuilder = serviceTarget.addService(serviceName, service);
        serviceBuilder.addDependencies(Services.FRAMEWORK_ACTIVE);
        ServiceController<Boolean> controller = serviceBuilder.install();

        builder.installServices(FrameworkPhase.ACTIVE, serviceTarget, Mockito.mock(ServiceListener.class));
        FutureServiceValue<Boolean> future = new FutureServiceValue<Boolean>(controller);
        Assert.assertTrue(future.get(2, TimeUnit.SECONDS));

        // Check that all the framework service are UP
        assertServiceState(serviceContainer, State.UP, Services.FRAMEWORK_INIT);
        assertServiceState(serviceContainer, State.UP, IntegrationServices.BOOTSTRAP_BUNDLES_COMPLETE);
        assertServiceState(serviceContainer, State.UP, IntegrationServices.PERSISTENT_BUNDLES_COMPLETE);
        assertServiceState(serviceContainer, State.UP, Services.FRAMEWORK_ACTIVE);

        // Remove the dependent service
        controller.setMode(Mode.REMOVE);
        future = new FutureServiceValue<Boolean>(controller, State.REMOVED);
        Assert.assertNull(future.get(2, TimeUnit.SECONDS));

        // Check that all the framework service are still UP
        assertServiceState(serviceContainer, State.UP, Services.FRAMEWORK_INIT);
        assertServiceState(serviceContainer, State.UP, IntegrationServices.BOOTSTRAP_BUNDLES_COMPLETE);
        assertServiceState(serviceContainer, State.UP, IntegrationServices.PERSISTENT_BUNDLES_COMPLETE);
        assertServiceState(serviceContainer, State.UP, Services.FRAMEWORK_ACTIVE);
    }

    private static JavaArchive getBundleA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "bundleA");
        archive.setManifest(new Asset() {
            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                return builder.openStream();
            }
        });
        return archive;
    }
}