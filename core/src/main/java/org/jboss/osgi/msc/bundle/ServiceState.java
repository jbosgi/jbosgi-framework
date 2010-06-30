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

import java.util.Dictionary;

import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * The service implementation.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class ServiceState  implements ServiceRegistration, ServiceReference
{
   private AbstractBundle owner;
   private Object value;

   @SuppressWarnings("rawtypes")
   public ServiceState(AbstractBundle owner, String[] clazzes, Object value, Dictionary properties)
   {
      if (owner == null)
         throw new IllegalArgumentException("Null owner");
      if (value == null)
         throw new IllegalArgumentException("Null value");
      
      this.owner = owner;
      this.value = value;
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

   public Object getValue() 
   {
      return value;
   }
   
   @Override
   public ServiceReference getReference()
   {
      return new ServiceReferenceWrapper(this);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public void setProperties(Dictionary properties)
   {
      throw new NotImplementedException();
   }

   @Override
   public void unregister()
   {
      throw new NotImplementedException();
   }

   @Override
   public Object getProperty(String key)
   {
      throw new NotImplementedException();
   }

   @Override
   public String[] getPropertyKeys()
   {
      throw new NotImplementedException();
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
