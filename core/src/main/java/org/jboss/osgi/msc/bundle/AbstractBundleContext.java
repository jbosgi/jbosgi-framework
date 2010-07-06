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

import java.io.File;
import java.io.InputStream;
import java.util.Dictionary;
import java.util.Map;

import org.jboss.osgi.msc.plugin.ServiceManagerPlugin;
import org.jboss.osgi.spi.NotImplementedException;
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
 * The base of all {@link BundleContext} implementations.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public abstract class AbstractBundleContext implements BundleContext
{
   private AbstractBundle bundleState;

   AbstractBundleContext(AbstractBundle bundleState)
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");

      this.bundleState = bundleState;
   }

   BundleManager getBundleManager()
   {
      return bundleState.getBundleManager();
   }

   @Override
   public String getProperty(String key)
   {
      FrameworkState frameworkState = bundleState.getBundleManager().getFrameworkState();
      return frameworkState.getProperty(key);
   }

   @Override
   public Bundle getBundle()
   {
      return bundleState.getBundleWrapper();
   }

   @Override
   public Bundle installBundle(String location, InputStream input) throws BundleException
   {
      BundleManager bundleManager = getBundleManager();
      return bundleManager.installBundle(location, input);
   }

   @Override
   public Bundle installBundle(String location) throws BundleException
   {
      BundleManager bundleManager = getBundleManager();
      return bundleManager.installBundle(location);
   }

   @Override
   public Bundle getBundle(long id)
   {
      throw new NotImplementedException();
   }

   @Override
   public Bundle[] getBundles()
   {
      throw new NotImplementedException();
   }

   @Override
   public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException
   {
      throw new NotImplementedException();
   }

   @Override
   public void addServiceListener(ServiceListener listener)
   {
      throw new NotImplementedException();
   }

   @Override
   public void removeServiceListener(ServiceListener listener)
   {
      throw new NotImplementedException();
   }

   @Override
   public void addBundleListener(BundleListener listener)
   {
      throw new NotImplementedException();
   }

   @Override
   public void removeBundleListener(BundleListener listener)
   {
      throw new NotImplementedException();
   }

   @Override
   public void addFrameworkListener(FrameworkListener listener)
   {
      throw new NotImplementedException();
   }

   @Override
   public void removeFrameworkListener(FrameworkListener listener)
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public ServiceRegistration registerService(String clazz, Object service, Dictionary properties)
   {
      return registerService(new String[] { clazz }, service, properties);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public ServiceRegistration registerService(String[] clazzes, Object service, Dictionary properties)
   {
      ServiceManagerPlugin plugin = bundleState.getServiceManagerPlugin();
      ServiceState serviceState = plugin.registerService(bundleState, clazzes, service, properties);
      return new ServiceRegistrationWrapper(serviceState);
   }

   @Override
   public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException
   {
      return null;
   }

   @Override
   public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException
   {
      return null;
   }

   @Override
   public ServiceReference getServiceReference(String clazz)
   {
      ServiceManagerPlugin plugin = bundleState.getServiceManagerPlugin();
      ServiceState serviceState = plugin.getServiceReference(bundleState, clazz);
      if (serviceState == null)
         return null;
      
      return new ServiceReferenceWrapper(serviceState);
   }

   @Override
   public Object getService(ServiceReference sref)
   {
      ServiceManagerPlugin plugin = bundleState.getServiceManagerPlugin();
      Object service = plugin.getService(bundleState, ServiceState.assertServiceState(sref));
      return service;
   }

   @Override
   public boolean ungetService(ServiceReference reference)
   {
      throw new NotImplementedException();
   }

   @Override
   public File getDataFile(String filename)
   {
      throw new NotImplementedException();
   }

   @Override
   public Filter createFilter(String filter) throws InvalidSyntaxException
   {
      throw new NotImplementedException();
   }
}
