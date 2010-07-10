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
package org.jboss.osgi.container.plugin.internal;

//$Id$

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.BatchServiceBuilder;
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
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.ServiceState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.ServiceManagerPlugin;
import org.jboss.osgi.container.util.NoFilter;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceFactory;

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
   public List<ServiceState> getRegisteredServices(AbstractBundle bundleState)
   {
      throw new NotImplementedException();
   }

   @Override
   public Object getService(AbstractBundle bundleState, ServiceState serviceState)
   {
      Object value = serviceState.getValue();
      if (value instanceof ServiceFactory)
         value = serviceState.getServiceFactoryValue(bundleState);

      return value;
   }

   @Override
   public ServiceState getServiceReference(AbstractBundle bundleState, String clazz)
   {
      List<ServiceState> srefs = getServiceReferencesInternal(bundleState, clazz, null, true);
      if (srefs == null || srefs.isEmpty())
         return null;

      return srefs.get(0);
   }

   @Override
   public List<ServiceState> getServiceReferences(AbstractBundle bundleState, String clazz, String filterStr, boolean checkAssignable)
         throws InvalidSyntaxException
   {
      Filter filter = null;
      if (filterStr != null)
         filter = FrameworkUtil.createFilter(filterStr);

      return getServiceReferencesInternal(bundleState, clazz, filter, true);
   }

   public List<ServiceState> getServiceReferencesInternal(AbstractBundle bundleState, String clazz, Filter filter, boolean checkAssignable)
   {
      List<ServiceState> result = new ArrayList<ServiceState>();
      List<ServiceName> names = serviceNameMap.get(clazz);
      if (names == null)
         return Collections.emptyList();

      if (filter == null)
         filter = NoFilter.INSTANCE;

      for (ServiceName name : names)
      {
         ServiceController<?> controller = serviceContainer.getService(name);
         if (controller == null)
            throw new IllegalStateException("Cannot obtain service for: " + name);

         ServiceState serviceState = (ServiceState)controller.getValue();
         if (filter.match(serviceState))
            result.add(serviceState);
      }
      return Collections.unmodifiableList(result);
   }

   @Override
   public List<ServiceState> getServicesInUse(AbstractBundle bundleState)
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
      Map<ServiceName, String> associations = new HashMap<ServiceName, String>();

      final ServiceState serviceState = new ServiceState(bundleState, clazzes, value, properties);
      BatchBuilder batchBuilder = serviceContainer.batchBuilder();
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

      List<ServiceName> serviceNames = serviceState.getServiceNames();
      log.debug("Register service: " + serviceNames);

      BatchServiceBuilder serviceBuilder;
      ServiceName rootServiceName = serviceNames.get(0);
      try
      {
         serviceBuilder = batchBuilder.addService(rootServiceName, service);
         associations.put(rootServiceName, clazzes[0]);
      }
      catch (DuplicateServiceException ex)
      {
         throw new IllegalStateException("Cannot register service: " + rootServiceName, ex);
      }

      // Set the startup mode
      serviceBuilder.setInitialMode(Mode.AUTOMATIC);

      // Add the service aliases
      for (int i = 1; i < serviceNames.size(); i++)
      {
         ServiceName alias = serviceNames.get(i);
         associations.put(alias, clazzes[1]);
         serviceBuilder.addAliases(alias);
      }

      try
      {
         batchBuilder.install();

         // Register the name association. We do this here 
         // in case anything went wrong during the install
         for (Entry<ServiceName, String> aux : associations.entrySet())
         {
            bundleState.addOwnedService(serviceState);
            registerNameAssociation(aux.getValue(), aux.getKey());
         }
      }
      catch (ServiceRegistryException ex)
      {
         log.error("Cannot register services: " + serviceNames, ex);
      }

      return serviceState;
   }

   @Override
   public void unregisterService(ServiceState serviceState)
   {
      List<ServiceName> serviceNames = serviceState.getServiceNames();
      log.debug("Unregister service: " + serviceNames);

      // Unregister name associations
      for (ServiceName name : serviceNames)
      {
         String[] clazzes = (String[])serviceState.getProperty(Constants.OBJECTCLASS);
         for (String clazz : clazzes)
            unregisterNameAssociation(clazz, name);
      }

      // Remove from owner bundle
      AbstractBundle serviceOwner = serviceState.getServiceOwner();
      serviceOwner.removeOwnedService(serviceState);

      // Remove from controller
      ServiceName rootServiceName = serviceNames.get(0);
      try
      {
         // A service is brought DOWN by setting it's mode to NEVER
         // Adding a {@link RemovingServiceListener} does this and will
         // also synchronoulsy remove the service from the registry 
         ServiceController<?> controller = serviceContainer.getService(rootServiceName);
         controller.addListener(new RemovingServiceListener());
      }
      catch (RuntimeException ex)
      {
         log.error("Cannot remove service: " + rootServiceName, ex);
      }
   }

   private void registerNameAssociation(String className, ServiceName serviceName)
   {
      synchronized (serviceNameMap)
      {
         List<ServiceName> names = serviceNameMap.get(className);
         if (names == null)
         {
            names = new CopyOnWriteArrayList<ServiceName>();
            serviceNameMap.put(className, names);
         }
         names.add(serviceName);
      }
   }

   private void unregisterNameAssociation(String className, ServiceName serviceName)
   {
      List<ServiceName> names = serviceNameMap.get(className);
      if (names == null)
         throw new IllegalStateException("Cannot obtain service names for: " + className);

      if (names.remove(serviceName) == false)
         throw new IllegalStateException("Cannot remove [" + serviceName + "] from: " + names);
   }

   @Override
   public boolean ungetService(AbstractBundle bundleState, ServiceState reference)
   {
      // [TODO] ungetService
      return true;
   }
}