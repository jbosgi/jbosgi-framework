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
package org.jboss.test.osgi.container.bundle;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.jboss.osgi.container.launch.FrameworkFactoryImpl;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;

/**
 * Test framework bootstrap options.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Apr-2010
 */
public class FrameworkLaunchTestCase extends OSGiFrameworkTest
{
   @BeforeClass
   public static void beforeClass()
   {
      // prevent framework creation
   }
   
   @Test
   public void frameworkStartStop() throws Exception
   {
      Framework framework = createFramework();
      assertNotNull("Framework not null", framework);
      
      assertBundleState(Bundle.INSTALLED, framework.getState());
      
      framework.start();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      framework.stop();
      assertBundleState(Bundle.ACTIVE, framework.getState());
      
      framework.waitForStop(2000);
      assertBundleState(Bundle.RESOLVED, framework.getState());
   }

   @Test
   public void testFrameworkAllLaunch() throws Exception
   {
      // Get the aggregated jboss-osgi-framework-all.jar
      File[] files = new File("../core/target").listFiles(new FilenameFilter()
      {
         public boolean accept(File dir, String name)
         {
            return name.startsWith("jbosgi-container-") && name.endsWith("-all.jar");
         }
      });
      
      System.out.println("jbosgi-container-all.jar => " + Arrays.asList(files));
      
      // Assume that the jboss-osgi-framework-all.jar exists
      Assume.assumeTrue(files.length == 1);
      
      // Run the java command
      String alljar = files[0].getAbsolutePath();
      String cmd = "java -cp " + alljar + " " + FrameworkFactoryImpl.class.getName();
      Process proc = Runtime.getRuntime().exec(cmd);
      int exitValue = proc.waitFor();
      
      // Delete/move the jboss-osgi-framework.log
      File logfile = new File("./generated/jbosgi-container.log");
      if (logfile.exists())
      {
         File logdir = logfile.getParentFile();
         File targetdir = new File("./target");
         if (targetdir.exists())
            logfile.renameTo(new File("./target/jbosgi316.log"));
         else
            logfile.delete();
         
         logdir.delete();
      }
      
      // Generate the error message and fail
      if (exitValue != 0)
      {
         StringBuffer failmsg = new StringBuffer("Error running command: " + cmd + "\n");
         BufferedReader errReader = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
         String line = errReader.readLine();
         while(line != null)
         {
            failmsg.append("\n" + line);
            line = errReader.readLine();
         }
         
         fail(failmsg.toString());
      }
   }
}