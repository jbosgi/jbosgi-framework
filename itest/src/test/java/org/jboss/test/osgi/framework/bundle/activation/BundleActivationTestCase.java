/*
* JBoss, Home of Professional Open Source
* Copyright 2009, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
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
package org.jboss.test.osgi.framework.bundle.activation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jboss.arquillian.api.ArchiveProvider;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.osgi.OSGiContainer;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.osgi.testing.OSGiTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleActivator;
import org.jboss.test.osgi.framework.simple.bundleA.SimpleService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.SynchronousBundleListener;

/**
 * BundleContextTest.
 *
 * @author thomas.diesler@jboss.com
 */
@RunWith(Arquillian.class)
public class BundleActivationTestCase extends OSGiTest
{
   @Inject
   public OSGiContainer container;
   
   @Inject
   public BundleContext context;
   
   @Test
   public void testLazyActivation() throws Exception
   {
      final List<BundleEvent> events = new ArrayList<BundleEvent>();
      BundleListener listener = new SynchronousBundleListener(){

         @Override
         public void bundleChanged(BundleEvent event)
         {
            events.add(event);
         }
      };
      context.addBundleListener(listener);
      
      Archive<?> bundleArchive = container.getTestArchive("activation-lazy.jar");
      Bundle bundle = container.installBundle(bundleArchive);
      try
      {
         assertBundleState(Bundle.INSTALLED, bundle.getState());
         
         bundle.start();
         assertBundleState(Bundle.STARTING, bundle.getState());
         
         assertEquals(3, events.size());
         assertEquals(BundleEvent.INSTALLED, events.remove(0).getType());
         assertEquals(BundleEvent.RESOLVED, events.remove(0).getType());
         assertEquals(BundleEvent.LAZY_ACTIVATION, events.remove(0).getType());
         
         Class<?> serviceClass = bundle.loadClass("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
         assertNotNull("Service class not null", serviceClass);
         
         assertBundleState(Bundle.ACTIVE, bundle.getState());
         
         ServiceReference sref = context.getServiceReference("org.jboss.test.osgi.framework.simple.bundleA.SimpleService");
         assertNotNull("Service not null", sref);
         
         assertEquals(2, events.size());
         assertEquals(BundleEvent.STARTING, events.remove(0).getType());
         assertEquals(BundleEvent.STARTED, events.remove(0).getType());
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @ArchiveProvider
   public static JavaArchive bundleArchive(final String archiveName)
   {
      final JavaArchive archive = ShrinkWrap.create(JavaArchive.class, archiveName);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleSymbolicName(archiveName);
            builder.addBundleManifestVersion(2);
            builder.addBundleActivator(SimpleActivator.class.getName());
            builder.addBundleActivationPolicy(Constants.ACTIVATION_LAZY);
            builder.addExportPackages(SimpleService.class);
            return builder.openStream();
         }
      });
      archive.addClasses(SimpleActivator.class, SimpleService.class);
      return archive;
   }
}
