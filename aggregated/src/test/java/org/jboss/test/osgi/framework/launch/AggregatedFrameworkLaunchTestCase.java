package org.jboss.test.osgi.framework.launch;
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

import org.jboss.osgi.framework.FrameworkMain;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test aggregated framework bootstrap.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jul-2010
 */
public class AggregatedFrameworkLaunchTestCase extends OSGiFrameworkTest {

    @BeforeClass
    public static void beforeClass() {
        // prevent framework creation
    }

    @Test
    public void testFrameworkStartStop() throws Exception {
        Framework framework = createFramework();
        assertNotNull("Framework not null", framework);

        assertBundleState(Bundle.INSTALLED, framework.getState());

        framework.start();
        assertBundleState(Bundle.ACTIVE, framework.getState());

        framework.stop();
        framework.waitForStop(2000);
        assertBundleState(Bundle.RESOLVED, framework.getState());
    }

    @Test
    public void testAggregatedFrameworkLaunch() throws Exception {
        // Get the aggregated jboss-osgi-framework-all.jar
        File[] files = new File("./target").listFiles(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return name.startsWith("jbosgi-framework-aggregated-") && name.endsWith("-all.jar");
            }
        });

        // Assert that the jbosgi-framework-aggregated exists
        assertEquals("Aggregated file exists: " + Arrays.asList(files), 1, files.length);
        assertTrue("File.length > 1M", files[0].length() > 1024 * 1014L);

        // Run the java command
        String alljar = files[0].getAbsolutePath();
        String javaopts = "-Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dorg.osgi.framework.storage=target/osgi-store";
        String cmd = "java " + javaopts + " -cp " + alljar + " " + FrameworkMain.class.getName();
        Process proc = Runtime.getRuntime().exec(cmd);
        Thread.sleep(3000);
        proc.destroy();

        File logfile = new File("./generated/jboss-osgi.log");
        assertTrue("Logfile exists", logfile.exists());

        // Delete/move the jboss-osgi-framework.log
        File logdir = logfile.getParentFile();
        File targetdir = new File("./target");
        if (targetdir.exists()) {
            logfile.renameTo(new File("./target/jboss-osgi.log"));
        } else {
            logfile.delete();
        }
        logdir.delete();
    }
}