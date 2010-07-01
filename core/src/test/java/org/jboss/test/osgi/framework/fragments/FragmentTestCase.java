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
package org.jboss.test.osgi.framework.fragments;

//$Id$

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.net.URL;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.test.osgi.framework.fragments.fragA.FragBeanA;
import org.jboss.test.osgi.framework.fragments.subA.SubBeanA;
import org.junit.After;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Test Fragment functionality
 * 
 * @author thomas.diesler@jboss.com
 * @since 07-Jan-2010
 */
@Ignore
public class FragmentTestCase extends OSGiFrameworkTest
{
   @After
   public void tearDown() throws Exception
   {
      shutdownFramework();
      super.tearDown();
   }

   @Test
   public void testHostOnly() throws Exception
   {
      // Bundle-SymbolicName: simple-hostA
      // Private-Package: org.jboss.test.osgi.fragments.hostA, org.jboss.test.osgi.fragments.subA 
      Bundle hostA = installBundle(getTestArchivePath("fragments-simple-hostA.jar"));
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());

      URL entryURL = hostA.getEntry("resources/resource.txt");
      assertNull("Entry URL null", entryURL);

      URL resourceURL = hostA.getResource("resources/resource.txt");
      assertNull("Resource URL null", resourceURL);

      // Load a private class
      assertLoadClass(hostA, SubBeanA.class.getName());

      hostA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostA.getState());
   }

   @Test
   public void testFragmentOnly() throws Exception
   {
      // Bundle-SymbolicName: simple-fragA
      // Export-Package: org.jboss.test.osgi.fragments.fragA
      // Include-Resource: resources/resource.txt=resource.txt
      // Fragment-Host: simple-hostA
      Bundle fragA = installBundle(getTestArchivePath("fragments-simple-fragA.jar"));
      assertBundleState(Bundle.INSTALLED, fragA.getState());

      URL entryURL = fragA.getEntry("resources/resource.txt");
      assertNotNull("Entry URL not null", entryURL);

      URL resourceURL = fragA.getResource("resources/resource.txt");
      assertNull("Resource URL null", resourceURL);

      try
      {
         fragA.start();
         fail("Fragment bundles can not be started");
      }
      catch (BundleException e)
      {
         assertBundleState(Bundle.INSTALLED, fragA.getState());
      }

      fragA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragA.getState());
   }

   @Test
   public void testAttachedFragment() throws Exception
   {
      // Bundle-SymbolicName: simple-hostA
      // Private-Package: org.jboss.test.osgi.fragments.hostA, org.jboss.test.osgi.fragments.subA 
      Bundle hostA = installBundle(getTestArchivePath("fragments-simple-hostA.jar"));
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      // Bundle-SymbolicName: simple-fragA
      // Export-Package: org.jboss.test.osgi.fragments.fragA
      // Include-Resource: resources/resource.txt=resource.txt
      // Fragment-Host: simple-hostA
      Bundle fragA = installBundle(getTestArchivePath("fragments-simple-fragA.jar"));
      assertBundleState(Bundle.INSTALLED, fragA.getState());

      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());
      assertBundleState(Bundle.RESOLVED, fragA.getState());

      URL entryURL = hostA.getEntry("resources/resource.txt");
      assertNull("Entry URL null", entryURL);

      URL resourceURL = hostA.getResource("resources/resource.txt");
      assertNotNull("Resource URL not null", resourceURL);

      // Load class provided by the fragment
      assertLoadClass(hostA, FragBeanA.class.getName());

      // Load a private class
      assertLoadClass(hostA, SubBeanA.class.getName());

      hostA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostA.getState());
      assertBundleState(Bundle.RESOLVED, fragA.getState());

      fragA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragA.getState());
   }

   @Test
   public void testFragmentHidesPrivatePackage() throws Exception
   {
      // Bundle-SymbolicName: simple-hostA
      // Private-Package: org.jboss.test.osgi.fragments.hostA, org.jboss.test.osgi.fragments.subA 
      Bundle hostA = installBundle(getTestArchivePath("fragments-simple-hostA.jar"));
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      // Bundle-SymbolicName: simple-hostB
      // Export-Package: org.jboss.test.osgi.fragments.subA
      // Private-Package: org.jboss.test.osgi.fragments.hostB 
      Bundle hostB = installBundle(getTestArchivePath("fragments-simple-hostB.jar"));
      assertBundleState(Bundle.INSTALLED, hostB.getState());

      // Bundle-SymbolicName: simple-fragB
      // Import-Package: org.jboss.test.osgi.fragments.subA
      // Fragment-Host: simple-hostA
      Bundle fragB = installBundle(getTestArchivePath("fragments-simple-fragB.jar"));
      assertBundleState(Bundle.INSTALLED, fragB.getState());

      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());
      assertBundleState(Bundle.RESOLVED, fragB.getState());

      // The fragment contains an overwrites Private-Package with Import-Package
      // The SubBeanA is expected to come from HostB, which exports that package

      System.out.println("FIXME [JBOSGI-346] Attached fragment hides private package in host");
      //assertLoadClass(hostA, SubBeanA.class.getName(), hostB);

      hostA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostA.getState());
      assertBundleState(Bundle.RESOLVED, fragB.getState());

      hostB.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostB.getState());

      fragB.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragB.getState());
   }

   @Test
   public void testFragmentExportsPackage() throws Exception
   {
      // Bundle-SymbolicName: simple-hostA
      // Private-Package: org.jboss.test.osgi.fragments.hostA, org.jboss.test.osgi.fragments.subA 
      Bundle hostA = installBundle(getTestArchivePath("fragments-simple-hostA.jar"));
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      // Bundle-SymbolicName: simple-hostC
      // Import-Package: org.jboss.test.osgi.fragments.fragA
      // Private-Package: org.jboss.test.osgi.fragments.hostC 
      Bundle hostC = installBundle(getTestArchivePath("fragments-simple-hostC.jar"));
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());

      try
      {
         // HostA does not export the package needed by HostC
         hostC.start();
         fail("Unresolved constraint expected");
      }
      catch (BundleException ex)
      {
         assertBundleState(Bundle.INSTALLED, hostC.getState());
      }

      // Bundle-SymbolicName: simple-fragA
      // Export-Package: org.jboss.test.osgi.fragments.fragA
      // Include-Resource: resources/resource.txt=resource.txt
      // Fragment-Host: simple-hostA
      Bundle fragA = installBundle(getTestArchivePath("fragments-simple-fragA.jar"));
      assertBundleState(Bundle.INSTALLED, fragA.getState());

      try
      {
         // FragA does not attach to the aleady resolved HostA
         // HostA does not export the package needed by HostC
         hostC.start();
         fail("Unresolved constraint expected");
      }
      catch (BundleException ex)
      {
         assertBundleState(Bundle.INSTALLED, hostC.getState());
      }

      // Refreshing HostA causes the FragA to get attached
      BundleContext context = getSystemContext();
      ServiceReference sref = context.getServiceReference(PackageAdmin.class.getName());
      PackageAdmin packageAdmin = (PackageAdmin)context.getService(sref);
      packageAdmin.refreshPackages(new Bundle[] { hostA });

      System.out.println("FIXME [JBOSGI-336] Implement PackageAdmin.refreshPackages(Bundle[])");
      Assume.assumeTrue(false);

      // Wait for the fragment to get attached
      int timeout = 2000;
      while (timeout > 0 && fragA.getState() != Bundle.RESOLVED)
      {
         Thread.sleep(200);
         timeout -= 200;
      }

      // HostC should now resolve and start
      hostC.start();
      assertBundleState(Bundle.ACTIVE, hostC.getState());

      hostA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostA.getState());

      hostC.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostC.getState());

      fragA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragA.getState());
   }

   @Test
   public void testFragmentRequireBundle() throws Exception
   {
      // Bundle-SymbolicName: simple-hostA
      // Private-Package: org.jboss.test.osgi.fragments.hostA, org.jboss.test.osgi.fragments.subA 
      Bundle hostA = installBundle(getTestArchivePath("fragments-simple-hostA.jar"));
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      // Bundle-SymbolicName: simple-fragC
      // Export-Package: org.jboss.test.osgi.fragments.fragC
      // Require-Bundle: simple-hostB
      // Fragment-Host: simple-hostA
      Bundle fragC = installBundle(getTestArchivePath("fragments-simple-fragC.jar"));
      assertBundleState(Bundle.INSTALLED, fragC.getState());

      try
      {
         // The attached FragA requires bundle HostB, which is not yet installed  
         hostA.start();

         // Clarify error behaviour when fragments fail to attach
         // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1524

         // Equinox: Resolves HostA but does not attach FragA
         if (hostA.getState() == Bundle.ACTIVE)
            assertBundleState(Bundle.INSTALLED, fragC.getState());
      }
      catch (BundleException ex)
      {
         // Felix: Merges FragC's bundle requirement into HostA and fails to resolve
         assertBundleState(Bundle.INSTALLED, hostA.getState());
      }

      // Bundle-SymbolicName: simple-hostB
      // Export-Package: org.jboss.test.osgi.fragments.subA
      // Private-Package: org.jboss.test.osgi.fragments.hostB 
      Bundle hostB = installBundle(getTestArchivePath("fragments-simple-hostB.jar"));

      // HostA should resolve and start after HostB got installed
      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());

      hostA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostA.getState());

      fragC.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragC.getState());

      hostB.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostB.getState());
   }
}