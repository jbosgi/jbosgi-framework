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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.msc.service.BatchBuilder;
import org.jboss.msc.service.BatchServiceBuilder;
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
import org.jboss.osgi.container.bundle.ServiceReferenceComparator;
import org.jboss.osgi.container.bundle.ServiceState;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.container.plugin.PackageAdminPlugin;
import org.jboss.osgi.container.plugin.ServiceManagerPlugin;
import org.jboss.osgi.container.util.NoFilter;
import org.jboss.osgi.modules.ModuleContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.hooks.service.ListenerHook;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

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

   // Cache commonly used plugins
   private FrameworkEventsPlugin eventsPlugin;
   private PackageAdminPlugin packageAdmin;

   public ServiceManagerPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public void initPlugin()
   {
      serviceContainer = ServiceContainer.Factory.create();
      eventsPlugin = getPlugin(FrameworkEventsPlugin.class);
      packageAdmin = getPlugin(PackageAdminPlugin.class);
   }

   @Override
   public ServiceContainer getServiceContainer()
   {
      return serviceContainer;
   }

   @Override
   public long getNextServiceId()
   {
      return identityGenerator.incrementAndGet();
   }

   @Override
   @SuppressWarnings({ "rawtypes", "unchecked" })
   public ServiceState registerService(AbstractBundle bundleState, String[] clazzes, Object value, Dictionary properties)
   {
      if (clazzes == null || clazzes.length == 0)
         throw new IllegalArgumentException("Null service classes");

      // Immediately after registration of a {@link ListenerHook}, the ListenerHook.added() method will be called 
      // to provide the current collection of service listeners which had been added prior to the hook being registered.
      Collection<ListenerInfo> listenerInfos = null;
      if (value instanceof ListenerHook)
         listenerInfos = eventsPlugin.getServiceListenerInfos(null);

      // A temporary association of the clazz and name
      Map<ServiceName, String> associations = new HashMap<ServiceName, String>();

      // Generate the service names
      long serviceId = getNextServiceId();
      ServiceName[] serviceNames = new ServiceName[clazzes.length];
      for (int i = 0; i < clazzes.length; i++)
      {
         if (clazzes[i] == null)
            throw new IllegalArgumentException("Null service class at index: " + i);
         
         String shortName = clazzes[i].substring(clazzes[i].lastIndexOf(".") + 1);
         serviceNames[i] = ServiceName.of("jbosgi", bundleState.getSymbolicName(), shortName, new Long(serviceId).toString());
      }

      final ServiceState serviceState = new ServiceState(bundleState, serviceId, serviceNames, clazzes, value, properties);
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

      log.debug("Register service: " + serviceNames);

      ServiceName rootServiceName = serviceNames[0];
      BatchServiceBuilder serviceBuilder = batchBuilder.addService(rootServiceName, service);
      associations.put(rootServiceName, clazzes[0]);

      // Set the startup mode
      serviceBuilder.setInitialMode(Mode.AUTOMATIC);

      // Add the service aliases
      for (int i = 1; i < serviceNames.length; i++)
      {
         ServiceName alias = serviceNames[i];
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
            bundleState.addRegisteredService(serviceState);
            registerNameAssociation(aux.getValue(), aux.getKey());
         }
      }
      catch (ServiceRegistryException ex)
      {
         log.error("Cannot register services: " + serviceNames, ex);
      }

      // Call the newly added ListenerHook.added() method
      if (service instanceof ListenerHook)
      {
         ListenerHook listenerHook = (ListenerHook)service;
         listenerHook.added(listenerInfos);
      }

      // This event is synchronously delivered after the service has been registered with the Framework. 
      eventsPlugin.fireServiceEvent(bundleState, ServiceEvent.REGISTERED, serviceState);

      return serviceState;
   }

   @Override
   public List<ServiceState> getRegisteredServices(AbstractBundle bundleState)
   {
      return bundleState.getRegisteredServicesInternal();
   }

   @Override
   public ServiceState getServiceReference(AbstractBundle bundleState, String clazz)
   {
      if (clazz == null)
         throw new IllegalArgumentException("Null clazz");

      boolean checkAssignable = (bundleState.getBundleId() != 0);
      List<ServiceState> srefs = getServiceReferencesInternal(bundleState, clazz, null, checkAssignable);
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

      return getServiceReferencesInternal(bundleState, clazz, filter, checkAssignable);
   }

   public List<ServiceState> getServiceReferencesInternal(AbstractBundle bundleState, String clazzes, Filter filter, boolean checkAssignable)
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");

      List<ServiceName> serviceNames;
      if (clazzes != null)
      {
         serviceNames = serviceNameMap.get(clazzes);
         if (serviceNames == null)
            serviceNames = new ArrayList<ServiceName>();

         // Add potentially registered xservcie
         ServiceName xserviceName = ServiceName.of(ModuleContext.XSERVICE_PREFIX, clazzes);
         ServiceController<?> xservice = serviceContainer.getService(xserviceName);
         if (xservice != null)
            serviceNames.add(xserviceName);
      }
      else
      {
         // [MSC-9] Add ability to query the ServiceContainer
         Set<ServiceName> allServiceNames = new HashSet<ServiceName>();
         for (List<ServiceName> auxList : serviceNameMap.values())
            allServiceNames.addAll(auxList);

         serviceNames = new ArrayList<ServiceName>(allServiceNames);
      }

      if (serviceNames.isEmpty())
         return Collections.emptyList();

      if (filter == null)
         filter = NoFilter.INSTANCE;

      List<ServiceState> result = new ArrayList<ServiceState>();
      for (ServiceName serviceName : serviceNames)
      {
         ServiceController<?> controller = serviceContainer.getService(serviceName);
         if (controller == null)
            throw new IllegalStateException("Cannot obtain service for: " + serviceName);

         Object value = controller.getValue();
         
         // Create the ServiceState on demand for an XService instance
         // [TODO] This should be done eagerly to keep the serviceId constant
         // [TODO] service events for XService lifecycle changes
         // [MSC-17] Canonical ServiceName string representation
         if (value instanceof ServiceState == false && serviceName.toString().contains(ModuleContext.XSERVICE_PREFIX))
         {
            long serviceId = getNextServiceId();
            Bundle bundle = packageAdmin.getBundle(value.getClass());
            AbstractBundle owner = AbstractBundle.assertBundleState(bundle);
            value = new ServiceState(owner, serviceId, new ServiceName[] { serviceName }, new String[] { clazzes }, value, null);
         }

         ServiceState serviceState = (ServiceState)value;
         if (filter.match(serviceState) == false)
            continue;

         Object rawValue = serviceState.getRawValue();
         if (checkAssignable == true && rawValue instanceof ServiceFactory == false)
         {
            if (serviceState.isAssignableTo(bundleState, clazzes) == false)
               continue;
         }

         result.add(serviceState);
      }

      // Sort the result
      Collections.sort(result, ServiceReferenceComparator.getInstance());
      Collections.reverse(result);

      return Collections.unmodifiableList(result);
   }

   @Override
   public Object getService(AbstractBundle bundleState, ServiceState serviceState)
   {
      // If the service has been unregistered, null is returned.
      if (serviceState.isUnregistered())
         return null;

      // Add the given service ref to the list of used services
      bundleState.addServiceInUse(serviceState);
      serviceState.addUsingBundle(bundleState);

      Object value = serviceState.getScopedValue(bundleState);

      // If the factory returned an invalid value 
      // restore the service usage counts
      if (value == null)
      {
         bundleState.removeServiceInUse(serviceState);
         serviceState.removeUsingBundle(bundleState);
      }

      return value;
   }

   @Override
   public boolean ungetService(AbstractBundle bundleState, ServiceState serviceState)
   {
      serviceState.ungetScopedValue(bundleState);

      int useCount = bundleState.removeServiceInUse(serviceState);
      if (useCount == 0)
         serviceState.removeUsingBundle(bundleState);

      return useCount >= 0;
   }

   @Override
   public Set<ServiceState> getServicesInUse(AbstractBundle bundleState)
   {
      return bundleState.getServicesInUseInternal();
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
   public Set<AbstractBundle> getUsingBundles(ServiceState serviceState)
   {
      return serviceState.getUsingBundlesInternal();
   }

   @Override
   public void unregisterService(ServiceState serviceState)
   {
      List<ServiceName> serviceNames = serviceState.getServiceNames();
      log.debug("Unregister service: " + serviceNames);

      AbstractBundle serviceOwner = serviceState.getServiceOwner();

      // This event is synchronously delivered before the service has completed unregistering. 
      eventsPlugin.fireServiceEvent(serviceOwner, ServiceEvent.UNREGISTERING, serviceState);

      // Remove from using bundles
      for (AbstractBundle bundleState : serviceState.getUsingBundlesInternal())
      {
         while (ungetService(bundleState, serviceState))
            ;
      }

      // Remove from owner bundle
      serviceOwner.removeRegisteredService(serviceState);

      // Unregister name associations
      for (ServiceName serviceName : serviceNames)
      {
         String[] clazzes = (String[])serviceState.getProperty(Constants.OBJECTCLASS);
         for (String clazz : clazzes)
            unregisterNameAssociation(clazz, serviceName);
      }

      // Remove from controller
      ServiceName rootServiceName = serviceNames.get(0);
      try
      {
         ServiceController<?> controller = serviceContainer.getService(rootServiceName);
         controller.setMode(Mode.REMOVE);
      }
      catch (RuntimeException ex)
      {
         log.error("Cannot remove service: " + rootServiceName, ex);
      }
   }

}