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
package org.jboss.test.osgi.framework.simple;

//$Id$

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * A test that deployes a bundle and verifies its state
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
@Ignore
public class SimpleLogServiceTestCase extends OSGiFrameworkTest
{
   @After
   public void tearDown() throws Exception
   {
      shutdownFramework();
      super.tearDown();
   }

   @Test
   public void testNoLogService() throws Exception
   {
      Bundle bundle = installBundle(getTestArchivePath("simple-logservice-bundle.jar"));
      try
      {
         bundle.start();
         fail("Unresolved package contstraint on [org.osgi.service.log] expected");
      }
      catch (BundleException ex)
      {
         // expected
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testLogServiceFromThirdParty() throws Exception
   {
      Bundle logBundle = installBundle(getTestArchivePath("bundles/org.apache.felix.log.jar"));
      try
      {
         logBundle.start();

         Bundle bundle = installBundle(getTestArchivePath("simple-logservice-bundle.jar"));
         try
         {
            try
            {
               bundle.start();
               fail("Expected UNRESOLVED OSGiPackageRequirement{org.osgi.util.tracker [0.0.0,?)}");
            }
            catch (BundleException ex)
            {
               // expected
            }
         }
         finally
         {
            bundle.uninstall();
         }
      }
      finally
      {
         logBundle.uninstall();
      }
   }

   @Test
   public void testLogServiceFromCompendium() throws Exception
   {
      Bundle cmpnBundle = installBundle(getTestArchivePath("bundles/org.osgi.compendium.jar"));
      try
      {
         Bundle bundle = installBundle(getTestArchivePath("simple-logservice-bundle.jar"));
         try
         {
            bundle.start();

            // The bundle activator is expected to set this property
            String result = System.getProperty(bundle.getSymbolicName());
            assertNotNull("Result property not null", result);

            assertTrue("BundleActivator start", result.indexOf("startBundleActivator") > 0);
            assertFalse("getService", result.indexOf("getService") > 0);
            assertFalse("addingService", result.indexOf("addingService") > 0);
         }
         finally
         {
            bundle.uninstall();
         }
      }
      finally
      {
         cmpnBundle.uninstall();
      }
   }

   @Test
   public void testLogServiceFromTwoExporters() throws Exception
   {
      Bundle cmpnBundle = installBundle(getTestArchivePath("bundles/org.osgi.compendium.jar"));
      try
      {
         Bundle logBundle = installBundle(getTestArchivePath("bundles/org.apache.felix.log.jar"));
         try
         {
            logBundle.start();

            Bundle bundle = installBundle(getTestArchivePath("simple-logservice-bundle.jar"));
            try
            {
               bundle.start();

               // The bundle activator is expected to set this property
               String result = System.getProperty(bundle.getSymbolicName());
               assertNotNull("Result property not null", result);

               assertTrue("BundleActivator start", result.indexOf("startBundleActivator") > 0);
               assertTrue("getService", result.indexOf("getService") > 0);
               assertTrue("addingService", result.indexOf("addingService") > 0);
            }
            finally
            {
               bundle.uninstall();
            }
         }
         finally
         {
            logBundle.uninstall();
         }
      }
      finally
      {
         cmpnBundle.uninstall();
      }
   }
}