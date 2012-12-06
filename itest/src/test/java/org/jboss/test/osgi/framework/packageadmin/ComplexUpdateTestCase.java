package org.jboss.test.osgi.framework.packageadmin;
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
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.framework.packageadmin.exportA.ExportA;
import org.jboss.test.osgi.framework.packageadmin.exportB.ExportB;
import org.jboss.test.osgi.framework.packageadmin.exported.Exported;
import org.jboss.test.osgi.framework.packageadmin.importA.ImportingA;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;

/**
 * Test complex update.
 *
 * @author thomas.diesler@jboss.com
 */
public class ComplexUpdateTestCase extends OSGiFrameworkTest {

    @Test
    @Ignore("Intermittent failures")
    public void testComplexUpdateScenario() throws Exception {

        Bundle bundleE = installBundle(assembleArchive("ExportA", "/bundles/package-admin/exportA", ExportA.class));
        Bundle bundleU = installBundle(assembleArchive("ExportU", "/bundles/package-admin/exporter", Exported.class));

        Archive<?> assemblyE = assembleArchive("ExportA2", "/bundles/package-admin/exportB", ExportB.class);
        try {
            bundleE.start();
            bundleU.start();

            bundleE.update(toInputStream(assemblyE));

            // This bundle imports the old version of bundleE, should still be available!
            Bundle bundleI = installBundle(assembleArchive("ImportA", "/bundles/package-admin/importA", ImportingA.class));
            try {
                bundleI.start();

                refreshBundles(Arrays.asList(bundleE, bundleU, bundleI));

                assertEquals(Bundle.ACTIVE, bundleE.getState());
                assertEquals(Bundle.ACTIVE, bundleU.getState());
                assertTrue(Bundle.ACTIVE != bundleI.getState());

            } finally {
                bundleI.uninstall();
            }
        } finally {
            bundleU.uninstall();
            bundleE.uninstall();
        }
    }
}
