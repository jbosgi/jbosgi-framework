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
package org.jboss.test.osgi.container.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.test.osgi.container.service.support.BrokenServiceFactory;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * GetUnGetServiceTest.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @author <a href="ales.justin@jboss.org">Ales Justin</a>
 * @author Thomas.Diesler@jboss.com
 * @version $Revision: 1.1 $
 */
public class GetUnGetServiceTestCase extends OSGiFrameworkTest
{
   static String OBJCLASS = BundleContext.class.getName();

   @Test
   public void testGetUnServiceErrors() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);

         context.registerService(OBJCLASS, context, null);

         try
         {
            context.getService(null);
            fail("Should not be here!");
         }
         catch (IllegalArgumentException t)
         {
            // expected
         }

         try
         {
            context.ungetService(null);
            fail("Should not be here!");
         }
         catch (IllegalArgumentException t)
         {
            // expected
         }
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testGetService() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);

         ServiceRegistration sreg = context.registerService(OBJCLASS, context, null);
         ServiceReference sref = sreg.getReference();

         Object actual = context.getService(sref);
         assertEquals(context, actual);

         sreg.unregister();
         actual = context.getService(sref);
         assertNull("" + actual, actual);
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testGetServiceAfterStop() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);

         ServiceRegistration sreg = context.registerService(OBJCLASS, context, null);
         ServiceReference sref = sreg.getReference();

         Object actual = context.getService(sref);
         assertEquals(context, actual);

         bundle.stop();
         try
         {
            context.getService(sref);
            fail("Should not be here!");
         }
         catch (IllegalStateException t)
         {
            // expected
         }
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testErrorInGetService() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);

         context.addFrameworkListener(this);

         ServiceRegistration sreg = context.registerService(OBJCLASS, new BrokenServiceFactory(context, true), null);
         ServiceReference sref = sreg.getReference();
         Object actual = context.getService(sref);
         assertNull("" + actual, actual);

         assertFrameworkEvent(FrameworkEvent.ERROR, bundle, ServiceException.class);
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testErrorInUnGetService() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);

         context.addFrameworkListener(this);

         ServiceRegistration sreg = context.registerService(OBJCLASS, new BrokenServiceFactory(context, false), null);
         ServiceReference sref = sreg.getReference();
         Object actual = context.getService(sref);
         assertEquals(context, actual);
         assertNoFrameworkEvent();

         sreg.unregister();

         // [TODO] verify that this is expected
         assertFrameworkEvent(FrameworkEvent.WARNING, bundle, ServiceException.class);
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testUnGetServiceResult() throws Exception
   {
      Archive<?> assembly1 = assembleArchive("simple-bundle1", "/bundles/simple/simple-bundle1");
      Bundle bundle1 = installBundle(assembly1);
      try
      {
         bundle1.start();
         BundleContext context1 = bundle1.getBundleContext();
         assertNotNull(context1);

         ServiceRegistration sreg = context1.registerService(OBJCLASS, context1, null);
         ServiceReference sref = sreg.getReference();
         Object actual = context1.getService(sref);
         assertEquals(context1, actual);
         assertTrue(context1.ungetService(sref));
         assertFalse(context1.ungetService(sref));

         context1.getService(sref);
         context1.getService(sref);
         assertTrue(context1.ungetService(sref));
         assertTrue(context1.ungetService(sref));
         assertFalse(context1.ungetService(sref));

         Archive<?> assembly2 = assembleArchive("simple-bundle2", "/bundles/simple/simple-bundle2");
         Bundle bundle2 = installBundle(assembly2);
         try
         {
            bundle2.start();
            BundleContext context2 = bundle2.getBundleContext();
            assertNotNull(context2);

            context2.getService(sref);
            context1.getService(sref);
            assertTrue(context1.ungetService(sref));
            assertFalse(context1.ungetService(sref));
            assertTrue(context2.ungetService(sref));
            assertFalse(context2.ungetService(sref));
         }
         finally
         {
            bundle2.uninstall();
         }
      }
      finally
      {
         bundle1.uninstall();
      }
   }
}
