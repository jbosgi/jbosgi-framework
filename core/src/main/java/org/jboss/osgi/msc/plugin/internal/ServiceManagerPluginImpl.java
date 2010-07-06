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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.RemovingServiceListener;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
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
import org.osgi.framework.Constants;
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

   // The ServiceId generator 
   private AtomicLong identityGenerator = new AtomicLong();
   // The ServiceContainer
   private ServiceContainer serviceContainer;
   // Maps the service interface to the list of registered service names
   private Map<String, List<ServiceName>> serviceNameMap = new ConcurrentHashMap<String, List<ServiceName>>();
   // Maps the owner bundleId to the list of it's registered services
   private Map<Long, List<ServiceName>> serviceOwnerMap = new ConcurrentHashMap<Long, List<ServiceName>>();

   public ServiceManagerPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      serviceContainer = ServiceContainer.Factory.create();
   }

   @Override
   public long getNextServiceId()
   {
      return identityGenerator.incrementAndGet();
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
      List<ServiceState> services = new ArrayList<ServiceState>();
      List<ServiceName> names = serviceNameMap.get(clazz);
      if (names == null)
         return null;

      for (ServiceName name : names)
      {
         ServiceController<?> controller = serviceContainer.getService(name);
         if (controller == null)
            throw new IllegalStateException("Cannot obtain service for: " + name);

         ServiceState serviceState = (ServiceState)controller.getValue();
         services.add(serviceState);
      }

      return services.toArray(new ServiceState[services.size()]);
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

      // A temporary association of the clazz and name
      class NameAssociation
      {
         String clazz;
         ServiceName name;

         NameAssociation(String clazz, ServiceName name)
         {
            this.clazz = clazz;
            this.name = name;
         }

      }
      ;
      List<NameAssociation> associations = new ArrayList<NameAssociation>();

      final ServiceState serviceState = new ServiceState(bundleState, clazzes, value, properties);
      BatchBuilder batchBuilder = serviceContainer.batchBuilder();
      for (String clazz : clazzes)
      {
         try
         {
            ServiceName name = createServiceName(clazz, serviceState.getServiceId());
            Service service = new Service()
            {
               @Override
               public Object getValue() throws IllegalStateException
               {
                  // [TODO] for injection to work this needs to be the Object value
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
            batchBuilder.addService(name, service).setInitialMode(Mode.AUTOMATIC);
            associations.add(new NameAssociation(clazz, name));
         }
         catch (DuplicateServiceException ex)
         {
            log.error("Cannot register service: " + clazz, ex);
         }
      }

      try
      {
         batchBuilder.install();

         // Register the name association. We do this here 
         // in case anything went wrong during the install
         long bundleId = bundleState.getBundleId();
         for (NameAssociation aux : associations)
         {
            registerServiceName(bundleId, aux.clazz, aux.name);
            serviceState.addServiceName(aux.name);
         }
      }
      catch (ServiceRegistryException ex)
      {
         log.error("Cannot register services: " + Arrays.asList(clazzes), ex);
      }

      return serviceState;
   }

   @Override
   public void unregisterService(ServiceState serviceState)
   {
      List<ServiceName> names = serviceState.getServiceNames();
      if (names != null)
      {
         for (ServiceName name : names)
         {
            unregisterService(name);
         }
      }
   }

   // Creates a unique ServiceName
   private ServiceName createServiceName(String className, long serviceId)
   {
      return ServiceName.of("jbosgi", "service", className, new Long(serviceId).toString());
   }

   private void registerServiceName(long bundleId, String clazz, ServiceName name)
   {
      List<ServiceName> names = serviceNameMap.get(clazz);
      if (names == null)
      {
         names = new ArrayList<ServiceName>();
         serviceNameMap.put(clazz, names);
      }
      names.add(name);

      names = serviceOwnerMap.get(bundleId);
      if (names == null)
      {
         names = new ArrayList<ServiceName>();
         serviceOwnerMap.put(bundleId, names);
      }
      names.add(name);
   }

   private void unregisterServiceName(String clazz, ServiceName name)
   {
      List<ServiceName> names = serviceNameMap.get(clazz);
      if (names == null)
         throw new IllegalStateException("Cannot obtain service names for: " + clazz);

      if (names.remove(name) == false)
         throw new IllegalStateException("Cannot name [" + name + "] from: " + names);

      if (names.isEmpty())
         serviceNameMap.remove(clazz);
   }

   @Override
   public boolean ungetService(AbstractBundle bundleState, ServiceState reference)
   {
      throw new NotImplementedException();
   }

   @Override
   public void unregisterServices(AbstractBundle bundleState)
   {
      long bundleId = bundleState.getBundleId();
      List<ServiceName> names = serviceOwnerMap.remove(bundleId);
      if (names != null)
      {
         for (ServiceName name : names)
         {
            unregisterService(name);
         }
      }
   }

   private void unregisterService(ServiceName name)
   {
      ServiceController<?> controller = serviceContainer.getService(name);
      
      // Unregister the service names
      ServiceState serviceState = (ServiceState)controller.getValue();
      String[] clazzes = (String[])serviceState.getProperty(Constants.OBJECTCLASS);
      for (String clazz : clazzes)
         unregisterServiceName(clazz, name);
      
      // A service is brought DOWN by setting it's mode to NEVER
      // Adding a {@link RemovingServiceListener} does this and will
      // also synchronoulsy remove the service from the registry 
      controller.addListener(new RemovingServiceListener());
   }
}