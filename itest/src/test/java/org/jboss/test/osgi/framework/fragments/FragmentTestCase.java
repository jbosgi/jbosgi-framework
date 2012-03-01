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

import org.jboss.logging.Logger;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.spi.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.fragments.fragA.FragBeanA;
import org.jboss.test.osgi.framework.fragments.fragB.FragBeanB;
import org.jboss.test.osgi.framework.fragments.fragC.FragBeanC;
import org.jboss.test.osgi.framework.fragments.fragD.FragDClass;
import org.jboss.test.osgi.framework.fragments.fragE1.FragE1Class;
import org.jboss.test.osgi.framework.fragments.fragE2.FragE2Class;
import org.jboss.test.osgi.framework.fragments.hostA.HostAActivator;
import org.jboss.test.osgi.framework.fragments.hostB.HostBActivator;
import org.jboss.test.osgi.framework.fragments.hostC.HostCActivator;
import org.jboss.test.osgi.framework.fragments.hostD.HostDInterface;
import org.jboss.test.osgi.framework.fragments.hostE.HostEInterface;
import org.jboss.test.osgi.framework.fragments.hostF.HostFInterface;
import org.jboss.test.osgi.framework.fragments.subA.SubBeanA;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.service.packageadmin.PackageAdmin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.ProtectionDomain;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Test Fragment functionality
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 07-Jan-2010
 */
public class FragmentTestCase extends OSGiFrameworkTest {

    @Test
    public void testHostOnly() throws Exception {
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
    public void testFragmentOnly() throws Exception {
        Bundle fragA = installBundle(getFragmentA());
        assertBundleState(Bundle.INSTALLED, fragA.getState());

        URL entryURL = fragA.getEntry("resource.txt");
        assertEquals("bundle", entryURL.getProtocol());
        assertEquals("/resource.txt", entryURL.getPath());

        BufferedReader br = new BufferedReader(new InputStreamReader(entryURL.openStream()));
        assertEquals("fragA", br.readLine());

        URL resourceURL = fragA.getResource("resource.txt");
        assertNull("Resource URL null", resourceURL);

        try {
            fragA.start();
            fail("Fragment bundles can not be started");
        } catch (BundleException e) {
            assertBundleState(Bundle.INSTALLED, fragA.getState());
        }

        fragA.uninstall();
        assertBundleState(Bundle.UNINSTALLED, fragA.getState());
    }

    @Test
    public void testAttachedFragment() throws Exception {
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
        assertEquals("bundle", resourceURL.getProtocol());
        assertEquals("/resource.txt", resourceURL.getPath());

        BufferedReader br = new BufferedReader(new InputStreamReader(resourceURL.openStream()));
        assertEquals("fragA", br.readLine());

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
    public void testClassLoaderEquality() throws Exception {
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
    public void testProtectionDomainEquality() throws Exception {
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
    public void testFragmentHidesPrivatePackage() throws Exception {
        Bundle hostA = installBundle(getHostA());
        assertBundleState(Bundle.INSTALLED, hostA.getState());

        Bundle hostB = installBundle(getHostB());
        assertBundleState(Bundle.INSTALLED, hostB.getState());

        Bundle fragB = installBundle(getFragmentB());
        assertBundleState(Bundle.INSTALLED, fragB.getState());

        hostA.start();
        assertBundleState(Bundle.ACTIVE, hostA.getState());
        assertBundleState(Bundle.RESOLVED, fragB.getState());

        // The fragment contains an import for a package that is also available locally
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
    public void testFragmentExportsPackage() throws Exception {
        Bundle hostA = installBundle(getHostA());
        assertBundleState(Bundle.INSTALLED, hostA.getState());

        Bundle hostC = installBundle(getHostC());
        assertBundleState(Bundle.INSTALLED, hostA.getState());

        hostA.start();
        assertBundleState(Bundle.ACTIVE, hostA.getState());

        try {
            // HostA does not export the package needed by HostC
            hostC.start();
            fail("Unresolved constraint expected");
        } catch (BundleException ex) {
            assertBundleState(Bundle.INSTALLED, hostC.getState());
        }

        Bundle fragA = installBundle(getFragmentA());
        assertBundleState(Bundle.INSTALLED, fragA.getState());

        try {
            // FragA does not attach to the already resolved HostA
            // HostA does not export the package needed by HostC
            hostC.start();
            fail("Unresolved constraint expected");
        } catch (BundleException ex) {
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
    public void testFragmentRequireBundle() throws Exception {
        Bundle hostA = installBundle(getHostA());
        assertBundleState(Bundle.INSTALLED, hostA.getState());

        Bundle fragC = installBundle(getFragmentC());
        assertBundleState(Bundle.INSTALLED, fragC.getState());

        try {
            // The attached FragA requires bundle HostB, which is not yet installed
            hostA.start();

            // Clarify error behaviour when fragments fail to attach
            // https://www.osgi.org/members/bugzilla/show_bug.cgi?id=1524

            // Equinox: Resolves HostA but does not attach FragA
            if (hostA.getState() == Bundle.ACTIVE)
                assertBundleState(Bundle.INSTALLED, fragC.getState());
        } catch (BundleException ex) {
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
    public void testFragmentHostCircularDeps() throws Exception {
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
    public void testFragmentAddsExportToHostWithWires() throws Exception {
        Bundle hostF = installBundle(getHostF());
        assertBundleState(Bundle.INSTALLED, hostF.getState());

        Bundle hostE = installBundle(getHostE());
        assertBundleState(Bundle.INSTALLED, hostE.getState());

        Bundle fragE = installBundle(getFragmentE1());
        assertBundleState(Bundle.INSTALLED, fragE.getState());

        hostE.start();
        assertBundleState(Bundle.ACTIVE, hostE.getState());
        assertBundleState(Bundle.RESOLVED, fragE.getState());
        assertLoadClass(hostE, HostEInterface.class.getName());
        assertLoadClass(hostE, FragE1Class.class.getName());

        hostE.uninstall();
        assertBundleState(Bundle.UNINSTALLED, hostE.getState());

        hostF.uninstall();
        assertBundleState(Bundle.UNINSTALLED, hostF.getState());

        fragE.uninstall();
        assertBundleState(Bundle.UNINSTALLED, fragE.getState());
    }

    @Test
    public void testExtensionFragment() throws Exception {
        
        Bundle fragG1 = installBundle(getFragmentG1());
        assertBundleState(Bundle.INSTALLED, fragG1.getState());
        
        fragG1.uninstall();
        assertBundleState(Bundle.UNINSTALLED, fragG1.getState());
    }

    @Test
    public void testExtensionFragmentInvalidHost() throws Exception {
        
        try {
            installBundle(getFragmentG2());
            fail("BundleException expected");
        } catch (BundleException ex) {
            // expected
        }
    }

    @Test
    public void testExtensionFragmentBootclasspath() throws Exception {
        
        try {
            installBundle(getFragmentG3());
            fail("BundleException expected");
        } catch (BundleException ex) {
            Throwable cause = ex.getCause();
            assertNotNull("BundleException cause not null", cause);
            assertEquals("UnsupportedOperationException expected", UnsupportedOperationException.class, cause.getClass());
        }
    }

    @Test
    public void testExtensionFragmentFramework() throws Exception {
        
        try {
            installBundle(getFragmentG4());
            fail("BundleException expected");
        } catch (BundleException ex) {
            Throwable cause = ex.getCause();
            assertNotNull("BundleException cause not null", cause);
            assertEquals("UnsupportedOperationException expected", UnsupportedOperationException.class, cause.getClass());
        }
    }

    @Test
	public void testFragmentUpdate() throws Exception {
		
        Bundle hostA = installBundle(getHostA());
        assertBundleState(Bundle.INSTALLED, hostA.getState());

        Bundle fragA = installBundle(getFragmentA());
        assertBundleState(Bundle.INSTALLED, fragA.getState());

        hostA.start();
        
        // Load class provided by the fragment
        assertLoadClass(hostA, FragBeanA.class.getName());

        // Tests that when an attached fragment bundle is updated, the content of
        // the previous fragment remains attached to the host bundle. The new
   	    // content of the updated fragment must not be allowed to attach to the host
   	    // bundle until the Framework is restarted or the host bundle is refreshed.
        fragA.update(toInputStream(getFragmentB()));

        // Load class provided by the fragment
        assertLoadClass(hostA, FragBeanA.class.getName());
        
        refreshPackages(new Bundle[] { hostA });

        assertLoadClassFail(hostA, FragBeanA.class.getName());
        
        hostA.uninstall();
        fragA.uninstall();
	}
    
    @Test
	public void testFragmentAttachOrder() throws Exception {
		
        Bundle fragE1 = installBundle(getFragmentE1());
        Bundle fragE2 = installBundle(getFragmentE2());
        Bundle hostE = installBundle(getHostE());
        Bundle hostF = installBundle(getHostF());

        hostE.start();
        
        assertTrue(fragE1.getBundleId() < fragE2.getBundleId());
        
        // Load class provided by the fragment
        assertLoadClass(hostE, FragE1Class.class.getName());
        assertLoadClass(hostE, FragE2Class.class.getName());

        // Tests that if a classpath entry cannot be located in the bundle, then the
        // Framework attempts to locate the classpath entry in each attached
        // fragment bundle. The attached fragment bundles are searched in ascending
        // bundle id order.
        URL resourceURL = hostE.getResource("resource.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(resourceURL.openStream()));
        assertEquals("fragE1", br.readLine());
        
        hostF.uninstall();
        hostE.uninstall();
        fragE2.uninstall();
        fragE1.uninstall();
	}
    
    private JavaArchive getHostA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostA");
        archive.addClasses(HostAActivator.class, SubBeanA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(HostAActivator.class);
                builder.addImportPackages(BundleActivator.class, Logger.class, PackageAdmin.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostB");
        archive.addClasses(HostBActivator.class, SubBeanA.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(HostBActivator.class);
                builder.addExportPackages(SubBeanA.class);
                builder.addImportPackages(BundleActivator.class, PackageAdmin.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostC");
        archive.addClasses(HostCActivator.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addBundleActivator(HostCActivator.class);
                builder.addImportPackages(BundleActivator.class, FragBeanA.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getHostD() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostD");
        archive.addClasses(HostDInterface.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
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

    private JavaArchive getHostE() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostE");
        archive.addClasses(HostEInterface.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
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

    private JavaArchive getHostF() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-hostF");
        archive.addClasses(HostFInterface.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(HostFInterface.class);
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentA() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragA");
        archive.addClasses(FragBeanA.class);
        archive.addAsResource(getResourceFile("fragments/fragA.txt"), "resource.txt");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
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

    private JavaArchive getFragmentB() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragB");
        archive.addClasses(FragBeanB.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
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

    private JavaArchive getFragmentC() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragC");
        archive.addClasses(FragBeanC.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
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

    private JavaArchive getFragmentD() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragD");
        archive.addClasses(FragDClass.class);
        archive.setManifest(new Asset() {
            public InputStream openStream() {
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

    private JavaArchive getFragmentE1() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragE1");
        archive.addClasses(FragE1Class.class);
        archive.addAsResource(getResourceFile("fragments/fragE1.txt"), "resource.txt");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(FragE1Class.class);
                builder.addFragmentHost("simple-hostE");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentE2() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragE2");
        archive.addClasses(FragE2Class.class);
        archive.addAsResource(getResourceFile("fragments/fragE2.txt"), "resource.txt");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addExportPackages(FragE2Class.class);
                builder.addFragmentHost("simple-hostE");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentG1() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragG1");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addFragmentHost("system.bundle;extension:=foo");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentG2() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragG2");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addFragmentHost("simple-hostG;extension:=foo");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentG3() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragG3");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addFragmentHost("system.bundle;extension:=bootclasspath");
                return builder.openStream();
            }
        });
        return archive;
    }

    private JavaArchive getFragmentG4() {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-fragG4");
        archive.setManifest(new Asset() {
            public InputStream openStream() {
                OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
                builder.addBundleManifestVersion(2);
                builder.addBundleSymbolicName(archive.getName());
                builder.addFragmentHost("system.bundle;extension:=framework");
                return builder.openStream();
            }
        });
        return archive;
    }
}