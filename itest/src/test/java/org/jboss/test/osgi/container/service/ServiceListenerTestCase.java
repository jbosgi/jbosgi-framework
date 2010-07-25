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

// Id: $

import static org.junit.Assert.assertNotNull;

import java.util.Hashtable;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * Test {@link ServiceListener} registration.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Mar-2010
 */
public class ServiceListenerTestCase extends OSGiFrameworkTest
{
   @Test
   public void testServiceListener() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);

         assertNoServiceEvent();
         context.addServiceListener(this);

         ServiceRegistration sreg = context.registerService(BundleContext.class.getName(), context, null);
         ServiceReference sref = sreg.getReference();

         assertServiceEvent(ServiceEvent.REGISTERED, sref);

         sreg.unregister();
         assertServiceEvent(ServiceEvent.UNREGISTERING, sref);
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testObjectClassFilter() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);
         assertNoServiceEvent();

         String filter = "(" + Constants.OBJECTCLASS + "=" + BundleContext.class.getName() + ")";
         context.addServiceListener(this, filter);

         ServiceRegistration sreg = context.registerService(BundleContext.class.getName(), context, null);
         ServiceReference sref = sreg.getReference();

         assertServiceEvent(ServiceEvent.REGISTERED, sref);

         sreg.unregister();
         assertServiceEvent(ServiceEvent.UNREGISTERING, sref);
         
         filter = "(objectClass=dummy)";
         context.addServiceListener(this, filter);

         sreg = context.registerService(BundleContext.class.getName(), context, null);
         assertNoServiceEvent();

         sreg.unregister();
         assertNoServiceEvent();
      }
      finally
      {
         bundle.uninstall();
      }
   }

   @Test
   public void testModifyServiceProperties() throws Exception
   {
      Archive<?> assembly = assembleArchive("simple1", "/bundles/simple/simple-bundle1");
      Bundle bundle = installBundle(assembly);
      try
      {
         bundle.start();
         BundleContext context = bundle.getBundleContext();
         assertNotNull(context);
         assertNoServiceEvent();

         String filter = "(&(objectClass=org.osgi.framework.BundleContext)(foo=bar))";
         context.addServiceListener(this, filter);

         Hashtable<String, Object> props = new Hashtable<String, Object>();
         props.put("foo", "bar");
         ServiceRegistration sreg = context.registerService(BundleContext.class.getName(), context, props);
         ServiceReference sref = sreg.getReference();

         assertServiceEvent(ServiceEvent.REGISTERED, sref);

         props.put("xxx", "yyy");
         sreg.setProperties(props);
         assertServiceEvent(ServiceEvent.MODIFIED, sref);
         
         props.put("foo", "notbar");
         sreg.setProperties(props);
         assertServiceEvent(ServiceEvent.MODIFIED_ENDMATCH, sref);
         
         props.put("foo", "bar");
         sreg.setProperties(props);
         assertServiceEvent(ServiceEvent.MODIFIED, sref);
         
         sreg.unregister();
         assertServiceEvent(ServiceEvent.UNREGISTERING, sref);
      }
      finally
      {
         bundle.uninstall();
      }
   }
}
