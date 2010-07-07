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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.msc.plugin.ServiceManagerPlugin;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
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
   // The service properties
   private Map<String, Object> properties;
   // The list of service names associated with this service
   private List<ServiceName> serviceNames;
   // The service object value
   private Object value;

   // Cache commonly used managers
   private ServiceManagerPlugin serviceManager;
   
   public ServiceState(AbstractBundle owner, String[] clazzes, Object value, Dictionary props)
   {
      if (owner == null)
         throw new IllegalArgumentException("Null owner");
      if (clazzes == null)
         throw new IllegalArgumentException("Null clazzes");
      if (value == null)
         throw new IllegalArgumentException("Null value");
      
      this.serviceManager = owner.getServiceManagerPlugin();
      this.serviceId = serviceManager.getNextServiceId();
      this.owner = owner;
      this.value = value;
      
      // Copy the given service properties
      properties = new HashMap<String, Object>();
      if (props != null)
      {
         Enumeration keys = props.keys();
         while(keys.hasMoreElements())
         {
            String key = (String)keys.nextElement();
            properties.put(key, props.get(key));
         }
      }
      properties.put(Constants.SERVICE_ID, serviceId);
      properties.put(Constants.OBJECTCLASS, clazzes);
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
   public void setProperties(Dictionary properties)
   {
      throw new NotImplementedException();
   }

   @Override
   public void unregister()
   {
      serviceManager.unregisterService(this);
   }

   @Override
   public Object getProperty(String key)
   {
      return properties.get(key);
   }

   @Override
   public String[] getPropertyKeys()
   {
      Set<String> keys = properties.keySet();
      return keys.toArray(new String[keys.size()]);
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
}
