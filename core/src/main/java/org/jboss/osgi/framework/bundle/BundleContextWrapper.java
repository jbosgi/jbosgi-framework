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
package org.jboss.osgi.framework.bundle;

import java.io.File;
import java.io.InputStream;
import java.util.Dictionary;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * BundleContextWrapper.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class BundleContextWrapper implements BundleContext
{
   /** The bundle state */
   private BundleContext delegate;

   public BundleContextWrapper(AbstractBundleContext context)
   {
      if (context == null)
         throw new IllegalArgumentException("Null context");
      this.delegate = context;
   }

   BundleContext getInternal()
   {
      return delegate;
   }

   public String getProperty(String key)
   {
      return delegate.getProperty(key);
   }

   public Bundle getBundle()
   {
      return delegate.getBundle();
   }

   public Bundle installBundle(String location, InputStream input) throws BundleException
   {
      return delegate.installBundle(location, input);
   }

   public Bundle installBundle(String location) throws BundleException
   {
      return delegate.installBundle(location);
   }

   public Bundle getBundle(long id)
   {
      return delegate.getBundle(id);
   }

   public Bundle[] getBundles()
   {
      return delegate.getBundles();
   }

   public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException
   {
      delegate.addServiceListener(listener, filter);
   }

   public void addServiceListener(ServiceListener listener)
   {
      delegate.addServiceListener(listener);
   }

   public void removeServiceListener(ServiceListener listener)
   {
      delegate.removeServiceListener(listener);
   }

   public void addBundleListener(BundleListener listener)
   {
      delegate.addBundleListener(listener);
   }

   public void removeBundleListener(BundleListener listener)
   {
      delegate.removeBundleListener(listener);
   }

   public void addFrameworkListener(FrameworkListener listener)
   {
      delegate.addFrameworkListener(listener);
   }

   public void removeFrameworkListener(FrameworkListener listener)
   {
      delegate.removeFrameworkListener(listener);
   }

   @SuppressWarnings("rawtypes")
   public ServiceRegistration registerService(String clazz, Object service, Dictionary properties)
   {
      return delegate.registerService(clazz, service, properties);
   }

   @SuppressWarnings("rawtypes")
   public ServiceRegistration registerService(String[] clazzes, Object service, Dictionary properties)
   {
      return delegate.registerService(clazzes, service, properties);
   }

   public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException
   {
      return delegate.getServiceReferences(clazz, filter);
   }

   public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException
   {
      return delegate.getAllServiceReferences(clazz, filter);
   }

   public ServiceReference getServiceReference(String clazz)
   {
      return delegate.getServiceReference(clazz);
   }

   public Object getService(ServiceReference reference)
   {
      return delegate.getService(reference);
   }

   public boolean ungetService(ServiceReference reference)
   {
      return delegate.ungetService(reference);
   }

   public File getDataFile(String filename)
   {
      return delegate.getDataFile(filename);
   }

   public Filter createFilter(String filter) throws InvalidSyntaxException
   {
      return delegate.createFilter(filter);
   }

   @Override
   public int hashCode()
   {
      return delegate.hashCode();
   }

   @Override
   public boolean equals(Object obj)
   {
      if (!(obj instanceof BundleContextWrapper))
         return false;
      
      BundleContextWrapper other = (BundleContextWrapper)obj;
      return delegate.equals(other.delegate);
   }

   @Override
   public String toString()
   {
      return delegate.toString();
   }
}