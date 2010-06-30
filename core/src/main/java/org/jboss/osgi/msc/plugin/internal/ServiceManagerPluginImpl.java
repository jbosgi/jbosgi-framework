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
package org.jboss.osgi.msc.plugin.internal;

//$Id$

import java.util.Arrays;
import java.util.Dictionary;

import org.jboss.logging.Logger;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistryException;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.msc.bundle.AbstractBundle;
import org.jboss.osgi.msc.bundle.BundleManager;
import org.jboss.osgi.msc.bundle.ServiceState;
import org.jboss.osgi.msc.plugin.AbstractPlugin;
import org.jboss.osgi.msc.plugin.ServiceManagerPlugin;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.InvalidSyntaxException;

/**
 * A plugin that manages OSGi services
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class ServiceManagerPluginImpl extends AbstractPlugin implements ServiceManagerPlugin
{
   // Provide logging
   private final Logger log = Logger.getLogger(ServiceManagerPluginImpl.class);

   private ServiceContainer serviceContainer;

   public ServiceManagerPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      serviceContainer = ServiceContainer.Factory.create();
   }

   @Override
   public ServiceState[] getRegisteredServices(AbstractBundle bundleState)
   {
      throw new NotImplementedException();
   }

   @Override
   public Object getService(AbstractBundle bundleState, ServiceState sref)
   {
      return sref.getValue();
   }

   @Override
   public ServiceState getServiceReference(AbstractBundle bundleState, String clazz)
   {
      ServiceState[] srefs = getServiceReferencesInternal(bundleState, clazz, null, true);
      if (srefs == null || srefs.length == 0)
         return null;

      return srefs[0];
   }

   @Override
   public ServiceState[] getServiceReferences(AbstractBundle bundleState, String clazz, String filter, boolean checkAssignable) throws InvalidSyntaxException
   {
      ServiceState[] srefs = getServiceReferencesInternal(bundleState, clazz, null, true);
      return srefs;
   }

   public ServiceState[] getServiceReferencesInternal(AbstractBundle bundleState, String clazz, String filter, boolean checkAssignable)
   {
      ServiceName serviceName = ServiceName.of(clazz);
      ServiceController<?> controller = serviceContainer.getService(serviceName);
      if (controller == null)
         return null;

      ServiceState serviceState = (ServiceState)controller.getValue();
      return new ServiceState[] { serviceState };
   }

   @Override
   public ServiceState[] getServicesInUse(AbstractBundle bundleState)
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings({ "rawtypes", "unchecked" })
   public ServiceState registerService(AbstractBundle bundleState, String[] clazzes, Object value, Dictionary properties)
   {
      if (clazzes == null || clazzes.length == 0)
         throw new IllegalArgumentException("Null service classes");

      final ServiceState serviceState = new ServiceState(bundleState, clazzes, value, properties);
      BatchBuilder batchBuilder = serviceContainer.batchBuilder();
      for (String clazz : clazzes)
      {
         try
         {
            ServiceName name = ServiceName.of(clazz);
            Service service = new Service()
            {
               @Override
               public Object getValue() throws IllegalStateException
               {
                  return serviceState;
               }

               @Override
               public void start(StartContext context) throws StartException
               {
               }

               @Override
               public void stop(StopContext context)
               {
               }
            };
            batchBuilder.addService(name, service);
         }
         catch (DuplicateServiceException ex)
         {
            log.error("Cannot register service: " + clazz, ex);
         }
      }

      try
      {
         batchBuilder.install();
      }
      catch (ServiceRegistryException ex)
      {
         log.error("Cannot register services: " + Arrays.asList(clazzes), ex);
      }
      return serviceState;
   }

   @Override
   public boolean ungetService(AbstractBundle bundleState, ServiceState reference)
   {
      throw new NotImplementedException();
   }

   @Override
   public void unregisterServices(AbstractBundle bundleState)
   {
      throw new NotImplementedException();
   }
}