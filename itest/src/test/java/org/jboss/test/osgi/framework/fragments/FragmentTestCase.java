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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;
import java.security.ProtectionDomain;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.fragments.fragA.FragBeanA;
import org.jboss.test.osgi.framework.fragments.fragB.FragBeanB;
import org.jboss.test.osgi.framework.fragments.fragC.FragBeanC;
import org.jboss.test.osgi.framework.fragments.fragD.FragDClass;
import org.jboss.test.osgi.framework.fragments.fragE.FragEClass;
import org.jboss.test.osgi.framework.fragments.hostA.HostAActivator;
import org.jboss.test.osgi.framework.fragments.hostB.HostBActivator;
import org.jboss.test.osgi.framework.fragments.hostC.HostCActivator;
import org.jboss.test.osgi.framework.fragments.hostD.HostDInterface;
import org.jboss.test.osgi.framework.fragments.hostE.HostEInterface;
import org.jboss.test.osgi.framework.fragments.hostF.HostFInterface;
import org.jboss.test.osgi.framework.fragments.subA.SubBeanA;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Test Fragment functionality
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 07-Jan-2010
 */
public class FragmentTestCase extends OSGiFrameworkTest
{
   @Test
   public void testHostOnly() throws Exception
   {
      Bundle hostA = installBundle(getHostA());
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());

      URL entryURL = hostA.getEntry("resource.txt");
      assertNull("Entry URL null", entryURL);

      URL resourceURL = hostA.getResource("resource.txt");
      assertNull("Resource URL null", resourceURL);

      // Load a private class
      assertLoadClass(hostA, SubBeanA.class.getName());

      hostA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostA.getState());
   }

   @Test
   public void testFragmentOnly() throws Exception
   {
      Bundle fragA = installBundle(getFragmentA());
      assertBundleState(Bundle.INSTALLED, fragA.getState());

      URL entryURL = fragA.getEntry("resource.txt");
      assertNotNull("Entry URL not null", entryURL);

      URL resourceURL = fragA.getResource("resource.txt");
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
      Bundle hostA = installBundle(getHostA());
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      Bundle fragA = installBundle(getFragmentA());
      assertBundleState(Bundle.INSTALLED, fragA.getState());

      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());
      assertBundleState(Bundle.RESOLVED, fragA.getState());

      URL entryURL = hostA.getEntry("resource.txt");
      assertNull("Entry URL null", entryURL);

      URL resourceURL = hostA.getResource("resource.txt");
      assertNotNull("Resource URL not null", resourceURL);

      // Load class provided by the fragment
      assertLoadClass(hostA, FragBeanA.class.getName());

      // Load a private class from host
      assertLoadClass(hostA, SubBeanA.class.getName());

      // PackageAdmin.getBundleType
      PackageAdmin pa = getPackageAdmin();
      assertEquals("Bundle type", 0, pa.getBundleType(hostA));
      assertEquals("Bundle type", PackageAdmin.BUNDLE_TYPE_FRAGMENT, pa.getBundleType(fragA));
      
      // PackageAdmin.getHosts
      Bundle[] hosts = pa.getHosts(hostA);
      assertNull("Not a fragment", hosts);
      
      hosts = pa.getHosts(fragA);
      assertNotNull("Hosts not null", hosts);
      assertEquals("Hosts length", 1, hosts.length);
      assertEquals("Hosts equals", hostA, hosts[0]);
      
      // PackageAdmin.getFragments
      Bundle[] fragments = pa.getFragments(fragA);
      assertNull("Not a host", fragments);
      
      fragments = pa.getFragments(hostA);
      assertNotNull("Fragments not null", fragments);
      assertEquals("Fragments length", 1, fragments.length);
      assertEquals("Fragments equals", fragA, fragments[0]);
      
      hostA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostA.getState());
      assertBundleState(Bundle.RESOLVED, fragA.getState());

      fragA.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragA.getState());
   }

   @Test
   public void testClassLoaderEquality() throws Exception
   {
      Bundle hostA = installBundle(getHostA());
      Bundle fragA = installBundle(getFragmentA());

      hostA.start();
      
      // Load class provided by the fragment
      assertLoadClass(hostA, FragBeanA.class.getName());
      Class<?> fragBeanClass = hostA.loadClass(FragBeanA.class.getName());
      ClassLoader fragClassLoader = fragBeanClass.getClassLoader();

      // Load a private class from host
      assertLoadClass(hostA, SubBeanA.class.getName());
      Class<?> hostBeanClass = hostA.loadClass(SubBeanA.class.getName());
      ClassLoader hostClassLoader = hostBeanClass.getClassLoader();
      
      // Assert ClassLoader
      assertSame(hostClassLoader, fragClassLoader);

      hostA.uninstall();
      fragA.uninstall();
   }
   
   @Test
   @Ignore("[JBOSGI-432] Fragments do not have a seperate ProtectionDomain")
   public void testProtectionDomainEquality() throws Exception
   {
      Bundle hostA = installBundle(getHostA());
      Bundle fragA = installBundle(getFragmentA());

      hostA.start();
      
      // Load class provided by the fragment
      assertLoadClass(hostA, FragBeanA.class.getName());
      Class<?> fragBeanClass = hostA.loadClass(FragBeanA.class.getName());
      ProtectionDomain fragDomain = fragBeanClass.getProtectionDomain();

      // Load a private class from host
      assertLoadClass(hostA, SubBeanA.class.getName());
      Class<?> hostBeanClass = hostA.loadClass(SubBeanA.class.getName());
      ProtectionDomain hostDomain = hostBeanClass.getProtectionDomain();
      
      // Assert ProtectionDomain
      assertNotSame(hostDomain, fragDomain);

      hostA.uninstall();
      fragA.uninstall();
   }
   
   @Test
   public void testFragmentHidesPrivatePackage() throws Exception
   {
      Bundle hostA = installBundle(getHostA());
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      Bundle hostB = installBundle(getHostB());
      assertBundleState(Bundle.INSTALLED, hostB.getState());

      Bundle fragB = installBundle(getFragmentB());
      assertBundleState(Bundle.INSTALLED, fragB.getState());

      hostA.start();
      assertBundleState(Bundle.ACTIVE, hostA.getState());
      assertBundleState(Bundle.RESOLVED, fragB.getState());

      // The fragment containsan import for a package that is also available locally 
      // SubBeanA is expected to come from HostB, which exports that package
      assertLoadClass(hostA, SubBeanA.class.getName(), hostB);

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
      Bundle hostA = installBundle(getHostA());
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      Bundle hostC = installBundle(getHostC());
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

      Bundle fragA = installBundle(getFragmentA());
      assertBundleState(Bundle.INSTALLED, fragA.getState());

      try
      {
         // FragA does not attach to the already resolved HostA
         // HostA does not export the package needed by HostC
         hostC.start();
         fail("Unresolved constraint expected");
      }
      catch (BundleException ex)
      {
         assertBundleState(Bundle.INSTALLED, hostC.getState());
      }

      // Refreshing HostA causes the FragA to get attached
      refreshPackages(new Bundle[] { hostA });

      // Load class provided by the fragment
      assertLoadClass(hostA, FragBeanA.class.getName());
      assertLoadClass(hostC, FragBeanA.class.getName(), hostA);
      
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
      Bundle hostA = installBundle(getHostA());
      assertBundleState(Bundle.INSTALLED, hostA.getState());

      Bundle fragC = installBundle(getFragmentC());
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

      Bundle hostB = installBundle(getHostB());

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

   @Test
   public void testFragmentHostCircularDeps() throws Exception
   {
      Bundle hostD = installBundle(getHostD());
      assertBundleState(Bundle.INSTALLED, hostD.getState());

      Bundle fragD = installBundle(getFragmentD());
      assertBundleState(Bundle.INSTALLED, fragD.getState());

      hostD.start();
      assertBundleState(Bundle.ACTIVE, hostD.getState());
      assertBundleState(Bundle.RESOLVED, fragD.getState());

      assertLoadClass(hostD, HostDInterface.class.getName());
      assertLoadClass(hostD, FragDClass.class.getName());

      hostD.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostD.getState());
      assertBundleState(Bundle.RESOLVED, fragD.getState());

      fragD.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragD.getState());
   }

   @Test
   public void testFragmentAddsExportToHostWithWires() throws Exception
   {
      Bundle hostF = installBundle(getHostF());
      assertBundleState(Bundle.INSTALLED, hostF.getState());

      Bundle hostE = installBundle(getHostE());
      assertBundleState(Bundle.INSTALLED, hostE.getState());

      Bundle fragE = installBundle(getFragmentE());
      assertBundleState(Bundle.INSTALLED, fragE.getState());

      hostE.start();
      assertBundleState(Bundle.ACTIVE, hostE.getState());
      assertBundleState(Bundle.RESOLVED, fragE.getState());
      assertLoadClass(hostE, HostEInterface.class.getName());
      assertLoadClass(hostE, FragEClass.class.getName());

      hostE.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostE.getState());

      hostF.uninstall();
      assertBundleState(Bundle.UNINSTALLED, hostF.getState());

      fragE.uninstall();
      assertBundleState(Bundle.UNINSTALLED, fragE.getState());
   }

   private JavaArchive getHostA()
   {
      // Bundle-SymbolicName: simple-hostA
      // Bundle-Activator: org.jboss.test.osgi.framework.fragments.hostA.HostAActivator
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostA");
      archive.addClasses(HostAActivator.class, SubBeanA.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleActivator(HostAActivator.class);
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getHostB()
   {
      // Bundle-SymbolicName: simple-hostB
      // Bundle-Activator: org.jboss.test.osgi.framework.fragments.hostB.HostBActivator
      // Export-Package: org.jboss.test.osgi.framework.fragments.subA
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostB");
      archive.addClasses(HostBActivator.class, SubBeanA.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleActivator(HostBActivator.class);
            builder.addExportPackages(SubBeanA.class);
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getHostC()
   {
      // Bundle-SymbolicName: simple-hostC
      // Bundle-Activator: org.jboss.test.osgi.framework.fragments.hostC.HostCActivator
      // Import-Package: org.osgi.framework, org.jboss.test.osgi.framework.fragments.fragA
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostC");
      archive.addClasses(HostCActivator.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addBundleActivator(HostCActivator.class);
            builder.addImportPackages(FragBeanA.class);
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getHostD()
   {
      // Bundle-SymbolicName: simple-hostD
      // Export-Package: org.jboss.test.osgi.framework.fragments.hostD
      // Import-Package: org.jboss.test.osgi.framework.fragments.fragD
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostD");
      archive.addClasses(HostDInterface.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(HostDInterface.class);
            builder.addImportPackages(FragDClass.class);
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getHostE()
   {
      // Bundle-SymbolicName: simple-hostE
      // Export-Package: org.jboss.test.osgi.framework.fragments.hostE
      // Import-Package: org.jboss.test.osgi.framework.fragments.hostF
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostE");
      archive.addClasses(HostEInterface.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(HostEInterface.class);
            builder.addImportPackages(HostFInterface.class);
            return builder.openStream();
         }
      });
      return archive;
   }
   
   private JavaArchive getHostF()
   {
      // Bundle-SymbolicName: simple-hostF
      // Export-Package: org.jboss.test.osgi.framework.fragments.hostF
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostF");
      archive.addClasses(HostFInterface.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(HostFInterface.class);
            return builder.openStream();
         }
      });
      return archive;      
   }

   private JavaArchive getFragmentA()
   {
      // Bundle-SymbolicName: simple-fragA
      // Export-Package: org.jboss.test.osgi.fragments.fragA
      // Fragment-Host: simple-hostA
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragA");
      archive.addClasses(FragBeanA.class);
      archive.addResource(getResourceFile("fragments/resource.txt"));
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(FragBeanA.class);
            builder.addFragmentHost("simple-hostA");
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getFragmentB()
   {
      // Bundle-SymbolicName: simple-fragB
      // Export-Package: org.jboss.test.osgi.framework.fragments.fragB
      // Import-Package: org.jboss.test.osgi.framework.fragments.subA
      // Fragment-Host: simple-hostA
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragB");
      archive.addClasses(FragBeanB.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(FragBeanB.class);
            builder.addImportPackages(SubBeanA.class);
            builder.addFragmentHost("simple-hostA");
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getFragmentC()
   {
      // Bundle-SymbolicName: simple-fragC
      // Export-Package: org.jboss.test.osgi.framework.fragments.fragC
      // Require-Bundle: simple-hostB
      // Fragment-Host: simple-hostA
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragC");
      archive.addClasses(FragBeanC.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(FragBeanC.class);
            builder.addRequireBundle("simple-hostB");
            builder.addFragmentHost("simple-hostA");
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getFragmentD()
   {
      // Bundle-SymbolicName: simple-fragD
      // Export-Package: org.jboss.test.osgi.framework.fragments.fragD
      // Import-Package: org.jboss.test.osgi.framework.fragments.hostD
      // Fragment-Host: simple-hostD
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragD");
      archive.addClasses(FragDClass.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(FragDClass.class);
            builder.addImportPackages(HostDInterface.class);
            builder.addFragmentHost("simple-hostD");
            return builder.openStream();
         }
      });
      return archive;
   }

   private JavaArchive getFragmentE()
   {
      // Bundle-SymbolicName: simple-fragE
      // Export-Package: org.jboss.test.osgi.framework.fragments.fragE
      // Fragment-Host: simple-hostD
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragE");
      archive.addClasses(FragEClass.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(FragEClass.class);
            builder.addFragmentHost("simple-hostE");
            return builder.openStream();
         }
      });
      return archive;
   }

}