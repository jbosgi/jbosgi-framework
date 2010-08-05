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
package org.jboss.osgi.container.bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.container.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.container.plugin.ServiceManagerPlugin;
import org.jboss.osgi.metadata.CaseInsensitiveDictionary;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceException;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * The service implementation.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
@SuppressWarnings("rawtypes")
public class ServiceState implements ServiceRegistration, ServiceReference
{
   // Provide logging
   private static final Logger log = Logger.getLogger(ServiceState.class);

   // The service id 
   private long serviceId;
   // The bundle that ownes this service
   private AbstractBundle ownerBundle;
   // The bundles that use this service
   private Set<AbstractBundle> usingBundles;
   // The list of service names associated with this service
   private List<ServiceName> serviceNames;
   // The service registration
   private ServiceRegistration registration;
   // The service reference
   private ServiceReference reference;
   // The {@link ServiceFactory} value registry
   private Map<Long, ServiceFactoryHolder> factoryValues;
   // The service object value
   private Object value;

   // The properties 
   private CaseInsensitiveDictionary prevProperties;
   private CaseInsensitiveDictionary currProperties;

   // Cache commonly used plugins
   private ServiceManagerPlugin serviceManager;
   private FrameworkEventsPlugin eventsPlugin;

   @SuppressWarnings("unchecked")
   public ServiceState(AbstractBundle owner, long serviceId, ServiceName[] serviceNames, String[] clazzes, Object value, Dictionary properties)
   {
      if (owner == null)
         throw new IllegalArgumentException("Null owner");
      if (serviceNames == null || serviceNames.length == 0)
         throw new IllegalArgumentException("Null names");
      if (clazzes == null || clazzes.length == 0)
         throw new IllegalArgumentException("Null clazzes");
      if (value == null)
         throw new IllegalArgumentException("Null value");

      this.serviceManager = owner.getServiceManagerPlugin();
      this.eventsPlugin = owner.getFrameworkEventsPlugin();

      this.serviceNames = Arrays.asList(serviceNames);
      this.serviceId = serviceId;
      this.ownerBundle = owner;
      this.value = value;

      if (checkValidClassNames(owner, clazzes, value) == false)
         throw new IllegalArgumentException("Invalid object class in: " + Arrays.asList(clazzes));

      if (properties == null)
         properties = new Hashtable();

      properties.put(Constants.SERVICE_ID, serviceId);
      properties.put(Constants.OBJECTCLASS, clazzes);
      this.currProperties = new CaseInsensitiveDictionary(properties);

      // Create the {@link ServiceRegistration} and {@link ServiceReference}
      this.registration = new ServiceRegistrationWrapper(this);
      this.reference = new ServiceReferenceWrapper(this);
   }

   /**
    * Assert that the given reference is an instance of ServiceState
    * @throws IllegalArgumentException if the given reference is not an instance of ServiceState
    */
   public static ServiceState assertServiceState(ServiceReference sref)
   {
      if (sref == null)
         throw new IllegalArgumentException("Null sref");

      if (sref instanceof ServiceReferenceWrapper)
         sref = ((ServiceReferenceWrapper)sref).getServiceState();

      return (ServiceState)sref;
   }

   public long getServiceId()
   {
      return serviceId;
   }

   public Object getRawValue()
   {
      return value;
   }

   public Object getScopedValue(AbstractBundle bundleState)
   {
      // For non-factory services, return the value
      if (value instanceof ServiceFactory == false)
         return value;

      // Get the ServiceFactory value
      Object result = null;
      try
      {
         if (factoryValues == null)
            factoryValues = new HashMap<Long, ServiceFactoryHolder>();

         ServiceFactoryHolder factoryHolder = factoryValues.get(bundleState.getBundleId());
         if (factoryHolder == null)
         {
            ServiceFactory factory = (ServiceFactory)value;
            factoryHolder = new ServiceFactoryHolder(bundleState, factory);
            factoryValues.put(bundleState.getBundleId(), factoryHolder);
         }

         result = factoryHolder.getService();

         // If the service object returned by the ServiceFactory object is not an instanceof all the classes named 
         // when the service was registered or the ServiceFactory object throws an exception, 
         // null is returned and a Framework event of type {@link FrameworkEvent#ERROR} 
         // containing a {@link ServiceException} describing the error is fired.
         if (result == null)
         {
            ServiceException sex = new ServiceException("Cannot get factory value", ServiceException.FACTORY_ERROR);
            eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.ERROR, sex);
         }
      }
      catch (RuntimeException rte)
      {
         ServiceException sex = new ServiceException("Cannot get factory value", ServiceException.FACTORY_EXCEPTION, rte);
         eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.ERROR, sex);
      }
      return result;
   }

   public void ungetScopedValue(AbstractBundle bundleState)
   {
      if (value instanceof ServiceFactory)
      {
         try
         {
            ServiceFactoryHolder factoryHolder = factoryValues.get(bundleState.getBundleId());
            factoryHolder.ungetService();
         }
         catch (RuntimeException rte)
         {
            ServiceException sex = new ServiceException("Cannot unget factory value", ServiceException.FACTORY_EXCEPTION, rte);
            eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.WARNING, sex);
         }
      }
   }

   public ServiceRegistration getRegistration()
   {
      return registration;
   }

   public List<ServiceName> getServiceNames()
   {
      return Collections.unmodifiableList(serviceNames);
   }

   @Override
   public ServiceReference getReference()
   {
      assertNotUnregistered();
      return reference;
   }

   @Override
   public void unregister()
   {
      assertNotUnregistered();
      serviceManager.unregisterService(this);
      usingBundles = null;
      registration = null;
   }

   @Override
   public Object getProperty(String key)
   {
      if (key == null)
         return null;
      return currProperties.get(key);
   }

   @Override
   public String[] getPropertyKeys()
   {
      List<String> result = new ArrayList<String>();
      if (currProperties != null)
      {
         Enumeration<String> keys = currProperties.keys();
         while (keys.hasMoreElements())
            result.add(keys.nextElement());
      }
      return result.toArray(new String[result.size()]);
   }

   @Override
   @SuppressWarnings({ "unchecked" })
   public void setProperties(Dictionary properties)
   {
      assertNotUnregistered();

      // Remember the previous properties for a potential
      // delivery of the MODIFIED_ENDMATCH event
      prevProperties = currProperties;

      if (properties == null)
         properties = new Hashtable();

      properties.put(Constants.SERVICE_ID, currProperties.get(Constants.SERVICE_ID));
      properties.put(Constants.OBJECTCLASS, currProperties.get(Constants.OBJECTCLASS));
      currProperties = new CaseInsensitiveDictionary(properties);

      // This event is synchronously delivered after the service properties have been modified. 
      eventsPlugin.fireServiceEvent(ownerBundle, ServiceEvent.MODIFIED, this);
   }

   public Dictionary getPreviousProperties()
   {
      return prevProperties;
   }

   public AbstractBundle getServiceOwner()
   {
      return ownerBundle;
   }

   @Override
   public Bundle getBundle()
   {
      if (isUnregistered())
         return null;
      
      return ownerBundle.getBundleWrapper();
   }

   public void addUsingBundle(AbstractBundle bundleState)
   {
      synchronized (this)
      {
         if (usingBundles == null)
            usingBundles = new HashSet<AbstractBundle>();

         usingBundles.add(bundleState);
      }
   }

   public void removeUsingBundle(AbstractBundle bundleState)
   {
      synchronized (this)
      {
         if (usingBundles != null)
            usingBundles.remove(bundleState);
      }
   }

   public Set<AbstractBundle> getUsingBundlesInternal()
   {
      synchronized (this)
      {
         if (usingBundles == null)
            return Collections.emptySet();

         // Return an unmodifieable snapshot of the set
         return Collections.unmodifiableSet(new HashSet<AbstractBundle>(usingBundles));
      }
   }

   @Override
   public Bundle[] getUsingBundles()
   {
      synchronized (this)
      {
         if (usingBundles == null)
            return null;

         Set<Bundle> bundles = new HashSet<Bundle>();
         for (AbstractBundle aux : usingBundles)
            bundles.add(aux.getBundleWrapper());

         return bundles.toArray(new Bundle[bundles.size()]);
      }
   }

   @Override
   public boolean isAssignableTo(Bundle bundle, String className)
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");
      if (className == null)
         throw new IllegalArgumentException("Null className");
      
      if (ownerBundle == AbstractBundle.assertBundleState(bundle))
         return true;
      
      Class<?> targetClass = null;
      try
      {
         targetClass = bundle.loadClass(className);
      }
      catch (ClassNotFoundException ex)
      {
         // If the requesting bundle does not have a wire to the 
         // service package it cannot be constraint on that package. 
         log.warn("Requesting bundle cannot load class: " + className);
         return true;
      }
      
      Class<?> ownerClass = null;
      try
      {
         ownerClass = ownerBundle.loadClass(className);
      }
      catch (ClassNotFoundException ex)
      {
         log.warn("Owner bundle cannot load class: " + className);
         return false;
      }
      
      if (targetClass != ownerClass)
      {
         log.debug("Not assignable: " + value.getClass().getName());
         return false;
      }
      
      return true;
   }

   @Override
   public int compareTo(Object sref)
   {
      if (sref instanceof ServiceReference == false)
         throw new IllegalArgumentException("Invalid sref: " + sref);
      
      Comparator<ServiceReference> comparator = ServiceReferenceComparator.getInstance();
      return comparator.compare(this, (ServiceReference)sref);
   }

   int getServiceRanking()
   {
      Object prop = getProperty(Constants.SERVICE_RANKING);
      if (prop instanceof Integer == false)
         return 0;
      
      return ((Integer)prop).intValue();
   }
   
   public boolean isUnregistered()
   {
      return registration == null;
   }

   void assertNotUnregistered()
   {
      if (isUnregistered())
         throw new IllegalStateException("Service is unregistered: " + this);
   }

   private boolean checkValidClassNames(AbstractBundle bundleState, String[] clazzeNames, Object value)
   {
      if (value instanceof ServiceFactory)
         return true;

      for (String clazzName : clazzeNames)
      {
         if (clazzName == null)
            throw new IllegalArgumentException("Null clazz");

         try
         {
            Class<?> clazz = bundleState.loadClass(clazzName);
            if (clazz.isAssignableFrom(value.getClass()) == false)
            {
               log.error("Service interface [" + clazzName + "] is not assignable from [" + value.getClass().getName() + "]");
               return false;
            }
         }
         catch (ClassNotFoundException ex)
         {
            log.error("Cannot load [" + clazzName + "] from: " + bundleState);
            return false;
         }
      }
      return true;
   }

   @Override
   @SuppressWarnings("unchecked")
   public String toString()
   {
      Hashtable<String, Object> props = new Hashtable<String, Object>(currProperties);
      String[] classes = (String[])props.get(Constants.OBJECTCLASS);
      props.put(Constants.OBJECTCLASS, Arrays.asList(classes));
      return "ServiceState" + props;
   }

   class ServiceFactoryHolder
   {
      ServiceFactory factory;
      AbstractBundle bundleState;
      AtomicInteger useCount;
      Object value;

      ServiceFactoryHolder(AbstractBundle bundleState, ServiceFactory factory)
      {
         this.bundleState = bundleState;
         this.factory = factory;
         this.useCount = new AtomicInteger();
      }

      Object getService()
      {
         // Multiple calls to getService() return the same value
         if (useCount.get() == 0)
         {
            // The Framework must not allow this method to be concurrently called for the same bundle
            synchronized (bundleState)
            {
               Object retValue = factory.getService(bundleState.getBundleWrapper(), getRegistration());

               // The Framework will check if the returned service object is an instance of all the 
               // classes named when the service was registered. If not, then null is returned to the bundle.
               if (checkValidClassNames(ownerBundle, (String[])getProperty(Constants.OBJECTCLASS), retValue) == false)
                  return null;

               value = retValue;
            }
         }

         useCount.incrementAndGet();
         return value;
      }

      void ungetService()
      {
         if (useCount.get() == 0)
            return;

         // Call unget on the factory when done
         if (useCount.decrementAndGet() == 0)
         {
            synchronized (bundleState)
            {
               factory.ungetService(bundleState.getBundleWrapper(), getRegistration(), value);
               value = null;
            }
         }
      }
   }
}
