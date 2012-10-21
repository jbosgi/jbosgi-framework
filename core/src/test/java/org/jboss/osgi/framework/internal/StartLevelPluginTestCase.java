package org.jboss.osgi.framework.internal;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.InputStream;

import org.jboss.osgi.framework.spi.StartLevelPlugin;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.startlevel.StartLevel;

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
            enableImmediateExecution(startLevel);

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
            enableImmediateExecution(sls);

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

    private void enableImmediateExecution(StartLevel sls) throws Exception {
        StartLevelPlugin plugin = (StartLevelPlugin) sls;
        plugin.enableImmediateExecution(true);
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
}
