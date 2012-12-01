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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

import org.jboss.osgi.framework.internal.FrameworkMain;
import org.junit.Test;

/**
 * Test aggregated framework bootstrap.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jul-2010
 */
public class AggregatedFrameworkLaunchTestCase {

    @Test
    public void testAggregatedFrameworkLaunch() throws Exception {
        // Get the aggregated jboss-osgi-framework-all.jar
        File[] files = new File("./target").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("jbosgi-framework-aggregated-") && name.endsWith("-all.jar");
            }
        });

        // Assert that the jbosgi-framework-aggregated exists
        assertEquals("Aggregated file exists: " + Arrays.asList(files), 1, files.length);
        assertTrue("File.length > 1M", files[0].length() > 1024 * 1014L);

        // Build the classpath
        String cp = files[0].getCanonicalPath();
        String[] names = new String[] { "jboss-logmanager.jar" };
        for (String name : names) {
            File file = new File("target/test-libs/" + name);
            assertTrue("File exists: " + file, file.exists());
            cp += File.pathSeparator + file.getCanonicalPath();
        }

        File logConfig = new File("target/test-classes/logging.properties");
        assertTrue("File exists: " + logConfig, logConfig.exists());

        // Run the java command
        String logopts = "-Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dlogging.configuration=" + logConfig.toURI();
        String javaopts = logopts + " -Dorg.osgi.framework.storage=target/osgi-store";
        //javaopts += " -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=y";
        String cmd = "java " + javaopts + " -cp " + cp + " " + FrameworkMain.class.getName();
        Process proc = Runtime.getRuntime().exec(cmd);
        Thread.sleep(3000);
        proc.destroy();

        File logfile = new File("./target/test.log");
        assertTrue("Logfile exists: " + logfile, logfile.exists());
    }
}