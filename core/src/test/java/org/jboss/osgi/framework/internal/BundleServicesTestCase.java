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

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.State;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Verify bundle services.
 *
 * @author thomas.diesler@jboss.com
 * @since 23-May-2011
 */
public class BundleServicesTestCase extends AbstractFrameworkTest {

    @Test
    public void testBundleLifecycle() throws Exception {

        Bundle bundle = installBundle(getTestArchive());
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);

        ServiceContainer serviceContainer = getBundleManager().getServiceContainer();
        ServiceController<?> controller = serviceContainer.getService(bundleState.getServiceName(Bundle.INSTALLED));
        assertServiceState(controller, State.UP);
        
        controller = serviceContainer.getService(bundleState.getServiceName(Bundle.RESOLVED));
        assertServiceState(controller, State.DOWN);
        
        controller = serviceContainer.getService(bundleState.getServiceName(Bundle.ACTIVE));
        assertServiceState(controller, State.DOWN);
        
        URL url = bundle.getResource(JarFile.MANIFEST_NAME);
        assertNotNull("URL not null", url);

        controller = serviceContainer.getService(bundleState.getServiceName(Bundle.RESOLVED));
        assertServiceState(controller, State.UP);
        
        bundle.start();
        controller = serviceContainer.getService(bundleState.getServiceName(Bundle.ACTIVE));
        assertServiceState(controller, State.UP);
        
        bundle.stop();
        controller = serviceContainer.getService(bundleState.getServiceName(Bundle.ACTIVE));
        assertServiceState(controller, State.DOWN);
        
        bundle.uninstall();
        assertBundleState(Bundle.UNINSTALLED, bundle.getState());
    }

    private void assertServiceState(final ServiceController<?> controller, final State expState) throws Exception {
        
        final CountDownLatch latch = new CountDownLatch(1);
        controller.addListener(new AbstractServiceListener<Object>() {

            @Override
            public void listenerAdded(ServiceController<? extends Object> controller) {
                checkState(controller);
            }

            public final void transition(final ServiceController<? extends Object> controller, final ServiceController.Transition transition) {
                switch (transition) {
                    case STARTING_to_UP:
                    case STARTING_to_START_FAILED:
                    case STOPPING_to_DOWN:
                        checkState(controller);
                        break;
                }
            }
            
            private void checkState(ServiceController<? extends Object> controller) {
                if (controller.getState().equals(expState)) {
                    latch.countDown();
                }
            }
        });
        
        if (latch.await(2, TimeUnit.SECONDS) == false) {
            fail("Timeout waiting for " + controller.getName() + " to be " + expState);
        }
    }

    private JavaArchive getTestArchive() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
        archive.setManifest(new Asset() {
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