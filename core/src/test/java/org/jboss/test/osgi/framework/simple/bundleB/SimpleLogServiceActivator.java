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
package org.jboss.test.osgi.framework.simple.bundleB;


import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

/**
 * A Service Activator
 * 
 * @author thomas.diesler@jboss.com
 * @since 24-Apr-2009
 */
public class SimpleLogServiceActivator implements BundleActivator
{
   public void start(BundleContext context)
   {
      final String symName = context.getBundle().getSymbolicName();
      addMessage(symName, "startBundleActivator");

      ServiceReference sref = context.getServiceReference(LogService.class.getName());
      if (sref != null)
      {
         LogService service = (LogService)context.getService(sref);
         String message = "getService: " + service.getClass().getName();
         addMessage(symName, message);
      }

      ServiceTracker tracker = new ServiceTracker(context, LogService.class.getName(), null)
      {
         @Override
         public Object addingService(ServiceReference reference)
         {
            LogService service = (LogService)super.addingService(reference);
            String message = "addingService: " + service.getClass().getName();
            addMessage(symName, message);
            return service;
         }
      };
      tracker.open();
   }

   public void stop(BundleContext context)
   {
      String symName = context.getBundle().getSymbolicName();
      addMessage(symName, "stopBundleActivator");
   }

   private void addMessage(String propName, String message)
   {
      String previous = System.getProperty(propName, ":");
      System.setProperty(propName, previous + message + ":");
      //System.out.println(message);
   }
}