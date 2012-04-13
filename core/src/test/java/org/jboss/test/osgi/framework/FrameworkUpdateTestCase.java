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
package org.jboss.test.osgi.framework;

import org.jboss.osgi.spi.util.ServiceLoader;
import org.jboss.osgi.testing.OSGiTest;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Test framework init/start/stop/update.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class FrameworkUpdateTestCase extends OSGiTest {

    @Test
    public void testUpdate() throws Exception {

        Framework framework = createFramework();
        try
        {
            // update before first init (in INSTALLED state)
            updateFramework(framework);

            // update after init (in STARTING state)
            initFramework(framework);
            updateFramework(framework);
            stopFramework(framework);

            // update after start (in ACTIVE state)
            startFramework(framework);
            updateFramework(framework);
            stopFramework(framework);

            // update after stop (in RESOLVED state)
            updateFramework(framework);
        }
        finally
        {
            framework.stop();
            framework.waitForStop(2000);
        }
    }

    @Test
    public void testWaitForStop() throws Exception {

        Framework framework = createFramework();
        try
        {
            startFramework(framework);
            FrameworkEvent event = framework.waitForStop(1000);
            assertNotNull("Wait for stop event not null", event);
            assertEquals(FrameworkEvent.WAIT_TIMEDOUT, event.getType());
        }
        finally
        {
            framework.stop();
            framework.waitForStop(2000);
        }
    }

    private Framework createFramework() {
        Map<String, String> props = new HashMap<String, String>();
        props.put("org.osgi.framework.storage", "target/osgi-store");
        props.put("org.osgi.framework.storage.clean", "onFirstInit");

        FrameworkFactory factory = ServiceLoader.loadService(FrameworkFactory.class);
        Framework framework = factory.newFramework(props);
        return framework;
    }

    private void initFramework(Framework framework) throws BundleException {
        framework.init();
        assertNotNull("BundleContext not null after init", framework.getBundleContext());
        assertEquals("Framework state after init", Bundle.STARTING, framework.getState());
    }

    private void startFramework(Framework framework) throws BundleException {
        framework.start();
        assertNotNull("BundleContext not null after start", framework.getBundleContext());
        assertEquals("Framework state after start", Bundle.ACTIVE, framework.getState());

    }

    private void stopFramework(Framework framework) throws BundleException, InterruptedException {
        int previousState = framework.getState();
        framework.stop();
        FrameworkEvent event = framework.waitForStop(10000);
        assertNotNull("FrameworkEvent not null", event);
        assertEquals("Stop event type", FrameworkEvent.STOPPED, event.getType());
        assertNull("BundleContext null after stop", framework.getBundleContext());
        int expectedState = (previousState & (Bundle.STARTING | Bundle.ACTIVE | Bundle.STOPPING)) != 0 ? Bundle.RESOLVED : previousState;
        assertEquals("Framework state after stop", expectedState, framework.getState());
    }

    private void updateFramework(Framework framework) throws Exception {
        int previousState = framework.getState();
        FrameworkEvent[] result = new FrameworkEvent[1];
        Exception[] failureException = new Exception[1];
        Thread waitForStop = waitForStopThread(framework, 10000, result, failureException);
        waitForStop.start();
        try {
            Thread.sleep(500);
            framework.update();
        } catch (BundleException e) {
            fail("Failed to update the framework: " + e);
        }
        waitForStop.join();
        if (failureException[0] != null)
            fail("Error occurred while waiting " + failureException[0]);

        assertNotNull("Wait for stop event not null", result[0]);

        // If the framework was not STARTING or ACTIVE then we assume the waitForStop returned immediately with a FrameworkEvent.STOPPED
        int expectedFrameworkEvent = (previousState & (Bundle.STARTING | Bundle.ACTIVE)) != 0 ? FrameworkEvent.STOPPED_UPDATE : FrameworkEvent.STOPPED;
        assertEquals("Wait for stop event type is wrong", expectedFrameworkEvent, result[0].getType());

        // Hack, not sure how to listen for when a framework is done starting back up.
        for (int i = 0; i < 20; i++) {
            if (framework.getState() != previousState) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // nothing
                }
            } else {
                break;
            }
        }
        assertEquals("Back at previous state after update", previousState, framework.getState());
    }

    private Thread waitForStopThread(final Framework framework, final long timeout, final FrameworkEvent[] success, final Exception[] failure) {
        return new Thread(new Runnable() {
            public void run() {
                try {
                    success[0] = framework.waitForStop(10000);
                } catch (InterruptedException e) {
                    failure[0] = e;
                }
            }
        }, "waitForStop thread");
    }
}