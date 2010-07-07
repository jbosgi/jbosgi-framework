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
package org.jboss.osgi.msc.bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.metadata.CaseInsensitiveDictionary;
import org.jboss.osgi.msc.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.msc.plugin.ServiceManagerPlugin;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * The service implementation.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
@SuppressWarnings("rawtypes")
public class ServiceState  implements ServiceRegistration, ServiceReference
{
   // The service id 
   private long serviceId;
   // The bundle that ownes this service
   private AbstractBundle owner;
   // The list of service names associated with this service
   private List<ServiceName> serviceNames;
   // The service registration
   private ServiceRegistration registration;
   // The service reference
   private ServiceReference reference;
   // The service object value
   private Object value;
   
   // The properties 
   private CaseInsensitiveDictionary prevProperties;
   private CaseInsensitiveDictionary currProperties;
   
   // Cache commonly used managers
   private ServiceManagerPlugin serviceManager;
   private FrameworkEventsPlugin eventsPlugin;
   
   @SuppressWarnings("unchecked")
   public ServiceState(AbstractBundle owner, String[] clazzes, Object value, Dictionary properties)
   {
      if (owner == null)
         throw new IllegalArgumentException("Null owner");
      if (clazzes == null)
         throw new IllegalArgumentException("Null clazzes");
      if (value == null)
         throw new IllegalArgumentException("Null value");
      
      this.serviceManager = owner.getServiceManagerPlugin();
      this.eventsPlugin = owner.getFrameworkEventsPlugin();
      
      this.serviceId = serviceManager.getNextServiceId();
      this.owner = owner;
      this.value = value;
      
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

   public Object getValue() 
   {
      return value;
   }

   public ServiceRegistration getServiceRegistration()
   {
      return registration;
   }
   
   public ServiceReference getServiceReference()
   {
      return reference;
   }
   
   public void removeServiceRegistration()
   {
      registration = null;
   }
   
   public List<ServiceName> getServiceNames()
   {
      return Collections.unmodifiableList(serviceNames);
   }

   public void addServiceName(ServiceName name)
   {
      if (serviceNames == null)
         serviceNames = new ArrayList<ServiceName>();
      
      serviceNames.add(name);
   }
   
   @Override
   public ServiceReference getReference()
   {
      return new ServiceReferenceWrapper(this);
   }

   @Override
   public void unregister()
   {
      serviceManager.unregisterService(this);
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
      ArrayList<String> result = new ArrayList<String>();
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
      checkUnregistered();

      // Remember the previous properties for a potential
      // delivery of the MODIFIED_ENDMATCH event
      prevProperties = currProperties;
      
      if (properties == null)
         properties = new Hashtable();
      
      properties.put(Constants.SERVICE_ID, currProperties.get(Constants.SERVICE_ID));
      properties.put(Constants.OBJECTCLASS, currProperties.get(Constants.OBJECTCLASS));
      currProperties = new CaseInsensitiveDictionary(properties);

      // This event is synchronously delivered after the service properties have been modified. 
      eventsPlugin.fireServiceEvent(owner, ServiceEvent.MODIFIED, this);
   }

   public Dictionary getPreviousProperties()
   {
      return prevProperties;
   }
   
   @Override
   public Bundle getBundle()
   {
      return owner.getBundleWrapper();
   }

   @Override
   public Bundle[] getUsingBundles()
   {
      throw new NotImplementedException();
   }

   @Override
   public boolean isAssignableTo(Bundle bundle, String className)
   {
      throw new NotImplementedException();
   }

   @Override
   public int compareTo(Object reference)
   {
      throw new NotImplementedException();
   }
   
   private void checkUnregistered()
   {
      if (isUnregistered())
         throw new IllegalStateException("Service is unregistered: " + this);
   }

   synchronized boolean isUnregistered()
   {
      return registration == null;
   }
}
