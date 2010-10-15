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
package org.jboss.osgi.framework.plugin.internal;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.ServiceReferenceComparator;
import org.jboss.osgi.framework.bundle.ServiceState;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.framework.plugin.PackageAdminPlugin;
import org.jboss.osgi.framework.plugin.ServiceManagerPlugin;
import org.jboss.osgi.framework.util.NoFilter;
import org.jboss.osgi.framework.util.RemoveOnlyCollection;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.FindHook;
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

      // Get/Create the {@link ServiceContainer}
      ServiceContainer value = (ServiceContainer)bundleManager.getProperty(ServiceContainer.class.getName());
      serviceContainer = value != null ? value : ServiceContainer.Factory.create();
   }

   @Override
   public void initPlugin()
   {
      eventsPlugin = getPlugin(FrameworkEventsPlugin.class);
      packageAdmin = getPlugin(PackageAdminPlugin.class);

      // Register the {@link ServiceContainer} as OSGi service
      BundleContext context = getBundleManager().getSystemContext();
      context.registerService(ServiceContainer.class.getName(), serviceContainer, null);
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
   public ServiceState registerService(AbstractBundle bundleState, String[] clazzes, Object serviceValue, Dictionary properties)
   {
      if (clazzes == null || clazzes.length == 0)
         throw new IllegalArgumentException("Null service classes");

      // Immediately after registration of a {@link ListenerHook}, the ListenerHook.added() method will be called
      // to provide the current collection of service listeners which had been added prior to the hook being registered.
      Collection<ListenerInfo> listenerInfos = null;
      if (serviceValue instanceof ListenerHook)
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

         String prefix = (i == 0 ? "jbosgi-service" : "jbosgi-alias");
         String shortName = clazzes[i].substring(clazzes[i].lastIndexOf(".") + 1);
         serviceNames[i] = ServiceName.of(prefix, bundleState.getSymbolicName(), shortName, new Long(serviceId).toString());
      }

      final ServiceState serviceState = new ServiceState(bundleState, serviceId, serviceNames, clazzes, serviceValue, properties);
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

      log.debugf("Register service: %s", serviceState);

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
         log.errorf(ex, "Cannot register services: %s", Arrays.asList(serviceNames));
      }

      // Call the newly added ListenerHook.added() method
      if (serviceValue instanceof ListenerHook)
      {
         ListenerHook listenerHook = (ListenerHook)serviceValue;
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
      List<ServiceState> result = getServiceReferencesInternal(bundleState, clazz, null, checkAssignable);
      result = processFindHooks(bundleState, clazz, null, true, result);
      if (result.isEmpty())
         return null;

      int lastIndex = result.size() - 1;
      return result.get(lastIndex);
   }

   @Override
   public List<ServiceState> getServiceReferences(AbstractBundle bundleState, String clazz, String filterStr, boolean checkAssignable)
         throws InvalidSyntaxException
   {
      Filter filter = null;
      if (filterStr != null)
         filter = FrameworkUtil.createFilter(filterStr);

      List<ServiceState> result = getServiceReferencesInternal(bundleState, clazz, filter, checkAssignable);
      result = processFindHooks(bundleState, clazz, filterStr, checkAssignable, result);
      return result;
   }

   public List<ServiceState> getServiceReferencesInternal(AbstractBundle bundleState, String clazz, Filter filter, boolean checkAssignable)
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");

      List<ServiceName> serviceNames;
      if (clazz != null)
      {
         serviceNames = serviceNameMap.get(clazz);
         if (serviceNames == null)
            serviceNames = new ArrayList<ServiceName>();

         // Add potentially registered xservcie
         ServiceName xserviceName = ServiceName.of(Constants.JBOSGI_PREFIX, clazz);
         ServiceController<?> xservice = serviceContainer.getService(xserviceName);
         if (xservice != null)
            serviceNames.add(xserviceName);
      }
      else
      {
         // [MSC-9] Add ability to query the ServiceContainer
         Set<ServiceName> allServiceNames = new HashSet<ServiceName>();
         for (List<ServiceName> auxList : serviceNameMap.values())
         {
            for (ServiceName auxName : auxList)
            {
               if (auxName.getCanonicalName().startsWith("jbosgi-service"))
                  allServiceNames.add(auxName);
            }
         }
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
         if (value instanceof ServiceState == false && serviceName.toString().contains(Constants.JBOSGI_PREFIX))
         {
            long serviceId = getNextServiceId();
            Bundle bundle = packageAdmin.getBundle(value.getClass());
            AbstractBundle owner = AbstractBundle.assertBundleState(bundle);
            value = new ServiceState(owner, serviceId, new ServiceName[] { serviceName }, new String[] { clazz }, value, null);
         }

         ServiceState serviceState = (ServiceState)value;
         if (filter.match(serviceState) == false)
            continue;

         Object rawValue = serviceState.getRawValue();

         checkAssignable &= (clazz != null);
         checkAssignable &= (bundleState.getBundleId() != 0);
         checkAssignable &= !(rawValue instanceof ServiceFactory);
         if (checkAssignable == false || serviceState.isAssignableTo(bundleState, clazz))
         {
            result.add(serviceState);
         }
      }

      // Sort the result
      Collections.sort(result, ServiceReferenceComparator.getInstance());
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

   private boolean unregisterNameAssociation(String className, ServiceName serviceName)
   {
      boolean removed = false;
      List<ServiceName> names = serviceNameMap.get(className);
      if (names != null)
      {
         removed = names.remove(serviceName);
         if (names.isEmpty())
            serviceNameMap.remove(className);
      }
      return removed;
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
      log.debugf("Unregister service: %s", serviceNames);

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
      String[] clazzes = (String[])serviceState.getProperty(Constants.OBJECTCLASS);
      for (ServiceName serviceName : serviceNames)
      {
         for (String clazz : clazzes)
         {
            unregisterNameAssociation(clazz, serviceName);
         }
      }

      // Remove from controller
      ServiceName rootServiceName = serviceNames.get(0);
      try
      {
         ServiceController<?> controller = serviceContainer.getService(rootServiceName);
         if (controller != null)
            controller.setMode(Mode.REMOVE);
      }
      catch (RuntimeException ex)
      {
         log.errorf(ex, "Cannot remove service: %s", rootServiceName);
      }
   }

   /*
    * The FindHook is called when a target bundle searches the service registry
    * with the getServiceReference or getServiceReferences methods. A registered
    * FindHook service gets a chance to inspect the returned set of service
    * references and can optionally shrink the set of returned services. The order
    * in which the find hooks are called is the reverse compareTo ordering of
    * their Service References.
    */
   private List<ServiceState> processFindHooks(AbstractBundle bundle, String clazz, String filterStr, boolean checkAssignable, List<ServiceState> serviceStates)
   {
      BundleContext context = bundle.getBundleContext();
      List<ServiceState> hookRefs = getServiceReferencesInternal(bundle, FindHook.class.getName(), null, true);
      if (hookRefs.isEmpty())
         return serviceStates;

      // Event and Find Hooks can not be used to hide the services from the framework.
      if (clazz != null && clazz.startsWith(FindHook.class.getPackage().getName()))
         return serviceStates;

      // The order in which the find hooks are called is the reverse compareTo ordering of
      // their ServiceReferences. That is, the service with the highest ranking number must be called first.
      List<ServiceReference> sortedHookRefs = new ArrayList<ServiceReference>(hookRefs);
      Collections.reverse(sortedHookRefs);

      List<FindHook> hooks = new ArrayList<FindHook>();
      for (ServiceReference hookRef : sortedHookRefs)
         hooks.add((FindHook)context.getService(hookRef));

      Collection<ServiceReference> hookParam = new ArrayList<ServiceReference>();
      for (ServiceState aux : serviceStates)
         hookParam.add(aux.getReference());

      hookParam = new RemoveOnlyCollection<ServiceReference>(hookParam);
      for (FindHook hook : hooks)
      {
         try
         {
            hook.find(context, clazz, filterStr, !checkAssignable, hookParam);
         }
         catch (Exception ex)
         {
            log.warnf(ex, "Error while calling FindHook: %s", hook);
         }
      }

      List<ServiceState> result = new ArrayList<ServiceState>();
      for (ServiceReference aux : hookParam)
      {
         ServiceState serviceState = ServiceState.assertServiceState(aux);
         result.add(serviceState);
      }

      return result;
   }
}