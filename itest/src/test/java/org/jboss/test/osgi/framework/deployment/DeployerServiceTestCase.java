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
package org.jboss.test.osgi.framework.deployment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.jboss.osgi.deployment.deployer.DeployerService;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.vfs.VirtualFile;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleC.SimpleService;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * A test that deployes a bundle and verifies its state
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Sep-2010
 */
public class DeployerServiceTestCase extends OSGiFrameworkTest
{
   @Test
   public void testNoMetaData() throws Exception
   {
      JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
      archive.addClasses(SimpleService.class);

      Deployment dep = createDeployment(archive, Version.emptyVersion);
      try
      {
         getDeployerService().deploy(dep);
         fail("BundleException expected");
      }
      catch (BundleException ex)
      {
         // expected
      }
   }

   @Test
   public void testAttachedOSGiMetaData() throws Exception
   {
      JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
      archive.addClasses(SimpleService.class);

      Deployment dep = createDeployment(archive, Version.emptyVersion);
      OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder(archive.getName());
      dep.addAttachment(OSGiMetaData.class, builder.getOSGiMetaData());

      Bundle bundle = getDeployerService().deploy(dep);
      assertEquals("simple-bundle", bundle.getSymbolicName());
      assertBundleState(Bundle.INSTALLED, bundle.getState());

      bundle.start();
      assertBundleState(Bundle.ACTIVE, bundle.getState());

      BundleContext context = bundle.getBundleContext();
      assertNotNull("BundleContext not null", context);

      bundle.stop();
      assertBundleState(Bundle.RESOLVED, bundle.getState());

      bundle.uninstall();
      assertBundleState(Bundle.UNINSTALLED, bundle.getState());
   }

   @Test
   public void testAttachedXModule() throws Exception
   {
      JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "simple-bundle");
      archive.addClasses(SimpleService.class);

      Deployment dep = createDeployment(archive, Version.emptyVersion);
      XModuleBuilder builder = XResolverFactory.loadModuleBuilder(null);
      builder.createModule(XModuleIdentity.create(archive.getName(), "0.0.0", null));
      builder.addBundleCapability(archive.getName(), Version.emptyVersion);
      dep.addAttachment(XModule.class, builder.getModule());

      Bundle bundle = getDeployerService().deploy(dep);
      assertEquals("simple-bundle", bundle.getSymbolicName());
      assertBundleState(Bundle.INSTALLED, bundle.getState());

      bundle.start();
      assertBundleState(Bundle.ACTIVE, bundle.getState());

      BundleContext context = bundle.getBundleContext();
      assertNotNull("BundleContext not null", context);

      bundle.stop();
      assertBundleState(Bundle.RESOLVED, bundle.getState());

      bundle.uninstall();
      assertBundleState(Bundle.UNINSTALLED, bundle.getState());
   }

   private Deployment createDeployment(JavaArchive archive, Version version) throws Exception
   {
      String symbolicName = archive.getName();
      VirtualFile virtualFile = toVirtualFile(archive);
      String location = virtualFile.toURL().toExternalForm();
      return DeploymentFactory.createDeployment(virtualFile, location, symbolicName, version);
   }

   private DeployerService getDeployerService() throws BundleException
   {
      BundleContext systemContext = getSystemContext();
      ServiceReference sref = systemContext.getServiceReference(DeployerService.class.getName());
      DeployerService deployer = (DeployerService)systemContext.getService(sref);
      return deployer;
   }
}