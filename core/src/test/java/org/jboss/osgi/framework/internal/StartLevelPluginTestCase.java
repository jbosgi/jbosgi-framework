/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.startlevel.StartLevel;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class StartLevelPluginTestCase extends OSGiFrameworkTest {

    @Test
    public void testStartLevel() throws Exception {
        StartLevel startLevel = getStartLevel();
        int orgStartLevel = startLevel.getStartLevel();
        int orgInitialStartlevel = startLevel.getInitialBundleStartLevel();
        try {
            setTestExecutor(startLevel);

            assertEquals(1, startLevel.getInitialBundleStartLevel());
            startLevel.setInitialBundleStartLevel(5);
            assertEquals(5, startLevel.getInitialBundleStartLevel());

            Bundle bundle = installBundle(createTestBundle("bundle1"));
            try {
                assertBundleState(Bundle.INSTALLED, bundle.getState());
                assertEquals(5, startLevel.getBundleStartLevel(bundle));
                bundle.start();
                assertBundleState(Bundle.INSTALLED, bundle.getState());

                startLevel.setStartLevel(5);
                assertBundleState(Bundle.ACTIVE, bundle.getState());

                startLevel.setStartLevel(4);
                assertBundleState(Bundle.RESOLVED, bundle.getState());

                startLevel.setInitialBundleStartLevel(7);
                assertEquals(7, startLevel.getInitialBundleStartLevel());

                startLevel.setStartLevel(10);
                assertBundleState(Bundle.ACTIVE, bundle.getState());

                Bundle bundle2 = installBundle(createTestBundle("bundle2"));
                try {
                    assertBundleState(Bundle.INSTALLED, bundle2.getState());
                    assertEquals(7, startLevel.getBundleStartLevel(bundle2));
                    bundle2.start();
                    assertBundleState(Bundle.ACTIVE, bundle2.getState());

                    startLevel.setBundleStartLevel(bundle2, 11);
                    assertBundleState(Bundle.RESOLVED, bundle2.getState());
                    startLevel.setBundleStartLevel(bundle2, 9);
                    assertBundleState(Bundle.ACTIVE, bundle2.getState());

                    startLevel.setStartLevel(1);
                    assertBundleState(Bundle.RESOLVED, bundle.getState());
                    assertBundleState(Bundle.RESOLVED, bundle2.getState());
                } finally {
                    bundle2.uninstall();
                }
            } finally {
                bundle.uninstall();
            }
        } finally {
            startLevel.setInitialBundleStartLevel(orgInitialStartlevel);
            startLevel.setStartLevel(orgStartLevel);
        }
    }

    @Test
    public void testStartLevelNonPersistent() throws Exception {
        BundleContext sc = getFramework().getBundleContext();
        ServiceReference sref = sc.getServiceReference(StartLevel.class.getName());
        StartLevel sls = (StartLevel) sc.getService(sref);
        int orgStartLevel = sls.getStartLevel();
        int orgInitialStartlevel = sls.getInitialBundleStartLevel();
        try {
            setTestExecutor(sls);

            assertEquals(1, sls.getInitialBundleStartLevel());
            sls.setInitialBundleStartLevel(5);
            assertEquals(5, sls.getInitialBundleStartLevel());

            Bundle bundle = installBundle(createTestBundle("bundle3"));
            try {
                assertBundleState(Bundle.INSTALLED, bundle.getState());
                assertEquals(5, sls.getBundleStartLevel(bundle));
                try {
                    bundle.start(Bundle.START_TRANSIENT);
                    fail("BundleException expected");
                } catch (BundleException ex) {
                    assertBundleState(Bundle.INSTALLED, bundle.getState());
                }

                sls.setStartLevel(5);
                // Increasing the start level should not have started the bundle since it wasn't started persistently
                assertBundleState(Bundle.INSTALLED, bundle.getState());
            } finally {
                bundle.uninstall();
            }
        } finally {
            sls.setInitialBundleStartLevel(orgInitialStartlevel);
            sls.setStartLevel(orgStartLevel);
        }
    }

    @Test
    public void getFrameworkStartLevel() throws Exception {
        BundleContext sc = getFramework().getBundleContext();
        ServiceReference sref = sc.getServiceReference(StartLevel.class.getName());
        StartLevel sls = (StartLevel) sc.getService(sref);

        assertEquals(0, sls.getBundleStartLevel(getFramework()));
    }

    private void setTestExecutor(StartLevel sls) throws Exception {
        StartLevelPlugin plugin = (StartLevelPlugin) sls;
        plugin.setExecutorService(new ImmediateExecutorService());
    }

    private JavaArchive createTestBundle(String name) {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, name);
        archive.setManifest(new Asset() {

            @Override
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleManifestVersion(2);
                builder.addImportPackages("org.osgi.framework");
                return builder.openStream();
            }
        });
        return archive;
    }

    private static class ImmediateExecutorService extends AbstractExecutorService {

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return null;
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return false;
        }
    }
}
