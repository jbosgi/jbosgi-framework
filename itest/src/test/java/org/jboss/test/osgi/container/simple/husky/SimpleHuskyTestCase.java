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
package org.jboss.test.osgi.container.simple.husky;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeNotNull;

import org.jboss.osgi.husky.BridgeFactory;
import org.jboss.osgi.husky.HuskyCapability;
import org.jboss.osgi.husky.RuntimeContext;
import org.jboss.osgi.testing.OSGiBundle;
import org.jboss.osgi.testing.OSGiRuntime;
import org.jboss.osgi.testing.OSGiRuntimeTest;
import org.jboss.test.osgi.container.simple.bundleA.SimpleService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;

/**
 * A test that deployes a bundle and accesses 
 * a service from within the test case
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Feb-2009
 */
public class SimpleHuskyTestCase extends OSGiRuntimeTest
{
   @RuntimeContext
   public BundleContext context;
   
   private OSGiRuntime runtime;
   private OSGiBundle bundle;

   @Before
   public void setUp() throws Exception
   {
      // Only do this if we are not within the OSGi Runtime
      if (context == null)
      {
         // Get the default runtime
         runtime = getDefaultRuntime();
         runtime.addCapability(new HuskyCapability());
         
         // Install the bundle
         bundle = runtime.installBundle("simple-husky.jar");
         
         // Start the bundle
         bundle.start();
         assertBundleState(Bundle.ACTIVE, bundle.getState());
      }
   }
   
   @After
   public void tearDown() throws BundleException
   {
      // Only do this if we are not within the OSGi Runtime
      if (context == null)
      {
         // Shutdown the runtime 
         runtime.shutdown();
      }
   }
   
   @Test
   public void testSimpleBundle() throws Exception
   {
      // Tell Husky to run this test method within the OSGi Runtime
      if (context == null)
         BridgeFactory.getBridge().run();
      
      // Stop here if the context is not injected
      assumeNotNull(context);
      
      // Get the SimpleService reference
      ServiceReference sref = context.getServiceReference(SimpleService.class.getName());
      assertNotNull("SimpleService Not Null", sref);
      
      // Access the SimpleService 
      SimpleService service = (SimpleService)context.getService(sref);
      assertEquals("hello", service.echo("hello"));
   }
}