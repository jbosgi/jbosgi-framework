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
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.framework.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.framework.plugin.ServiceManagerPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
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
   private BundleManager bundleManager;
   private AbstractBundle bundleState;
   private BundleContext contextWrapper;
   private boolean destroyed;

   AbstractBundleContext(AbstractBundle bundleState)
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");
      this.bundleManager = bundleState.getBundleManager();
      this.bundleState = bundleState;
   }

   /**
    * Assert that the given context is an instance of AbstractBundleContext
    * @throws IllegalArgumentException if the given context is not an instance of AbstractBundleContext
    */
   public static AbstractBundleContext assertBundleContext(BundleContext context)
   {
      if (context == null)
         throw new IllegalArgumentException("Null bundle");

      if (context instanceof BundleContextWrapper)
         context = ((BundleContextWrapper)context).getInternal();

      if (context instanceof AbstractBundleContext == false)
         throw new IllegalArgumentException("Not an AbstractBundleContext: " + context);

      return (AbstractBundleContext)context;
   }
   
   public BundleManager getBundleManager()
   {
      return bundleManager;
   }

   public AbstractBundle getBundleInternal()
   {
      return bundleState;
   }
   
   public BundleContext getContextWrapper()
   {
      checkValidBundleContext();
      if (contextWrapper == null)
         contextWrapper = new BundleContextWrapper(this);
      return contextWrapper;
   }

   void destroy()
   {
      destroyed = true;
   }
   
   @Override
   public String getProperty(String key)
   {
      checkValidBundleContext();
      FrameworkState frameworkState = bundleState.getBundleManager().getFrameworkState();
      frameworkState.assertFrameworkActive();
      return frameworkState.getProperty(key);
   }

   @Override
   public Bundle getBundle()
   {
      checkValidBundleContext();
      return bundleState.getBundleWrapper();
   }

   @Override
   public Bundle installBundle(String location, InputStream input) throws BundleException
   {
      checkValidBundleContext();
      AbstractBundle bundleState = bundleManager.installBundle(location, input);
      return bundleState.getBundleWrapper();
   }

   @Override
   public Bundle installBundle(String location) throws BundleException
   {
      checkValidBundleContext();
      AbstractBundle bundleState = bundleManager.installBundle(location);
      return bundleState.getBundleWrapper();
   }

   @Override
   public Bundle getBundle(long id)
   {
      checkValidBundleContext();
      
      AbstractBundle bundle = bundleManager.getBundleById(id);
      if (bundle == null)
         return null;
      
      return bundle.getBundleWrapper();
   }

   @Override
   public Bundle[] getBundles()
   {
      checkValidBundleContext();
      List<Bundle> result = new ArrayList<Bundle>();
      for (AbstractBundle bundle : bundleManager.getBundles())
         result.add(bundle.getBundleWrapper());
      return result.toArray(new Bundle[result.size()]);
   }

   @Override
   public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException
   {
      checkValidBundleContext();
      getFrameworkEventsPlugin().addServiceListener(bundleState, listener, filter);
   }

   @Override
   public void addServiceListener(ServiceListener listener)
   {
      checkValidBundleContext();
      try
      {
         getFrameworkEventsPlugin().addServiceListener(bundleState, listener, null);
      }
      catch (InvalidSyntaxException ex)
      {
         // ignore
      }
   }

   @Override
   public void removeServiceListener(ServiceListener listener)
   {
      checkValidBundleContext();
      getFrameworkEventsPlugin().removeServiceListener(bundleState, listener);
   }

   @Override
   public void addBundleListener(BundleListener listener)
   {
      checkValidBundleContext();
      getFrameworkEventsPlugin().addBundleListener(bundleState, listener);
   }

   @Override
   public void removeBundleListener(BundleListener listener)
   {
      checkValidBundleContext();
      getFrameworkEventsPlugin().removeBundleListener(bundleState, listener);
   }

   @Override
   public void addFrameworkListener(FrameworkListener listener)
   {
      checkValidBundleContext();
      getFrameworkEventsPlugin().addFrameworkListener(bundleState, listener);
   }

   @Override
   public void removeFrameworkListener(FrameworkListener listener)
   {
      checkValidBundleContext();
      getFrameworkEventsPlugin().removeFrameworkListener(bundleState, listener);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public ServiceRegistration registerService(String clazz, Object service, Dictionary properties)
   {
      checkValidBundleContext();
      return registerService(new String[] { clazz }, service, properties);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public ServiceRegistration registerService(String[] clazzes, Object service, Dictionary properties)
   {
      checkValidBundleContext();
      ServiceState serviceState = getServiceManager().registerService(bundleState, clazzes, service, properties);
      return serviceState.getRegistration();
   }

   @Override
   public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException
   {
      checkValidBundleContext();
      List<ServiceState> srefs = getServiceManager().getServiceReferences(bundleState, clazz, filter, true);
      if (srefs.isEmpty())
         return null;
      
      List<ServiceReference> result = new ArrayList<ServiceReference>();
      for (ServiceState serviceState : srefs)
         result.add(serviceState.getReference());
      
      return result.toArray(new ServiceReference[result.size()]);
   }

   @Override
   public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException
   {
      checkValidBundleContext();
      List<ServiceReference> result = new ArrayList<ServiceReference>();
      List<ServiceState> srefs = getServiceManager().getServiceReferences(bundleState, clazz, filter, false);
      if (srefs.isEmpty())
         return null;
      
      for (ServiceState serviceState : srefs)
         result.add(serviceState.getReference());
      
      return result.toArray(new ServiceReference[result.size()]);
   }

   @Override
   public ServiceReference getServiceReference(String clazz)
   {
      checkValidBundleContext();
      ServiceState serviceState = getServiceManager().getServiceReference(bundleState, clazz);
      if (serviceState == null)
         return null;
      
      return new ServiceReferenceWrapper(serviceState);
   }

   @Override
   public Object getService(ServiceReference sref)
   {
      checkValidBundleContext();
      ServiceState serviceState = ServiceState.assertServiceState(sref);
      Object service = getServiceManager().getService(bundleState, serviceState);
      return service;
   }

   @Override
   public boolean ungetService(ServiceReference sref)
   {
      checkValidBundleContext();
      ServiceState serviceState = ServiceState.assertServiceState(sref);
      return getServiceManager().ungetService(bundleState, serviceState);
   }

   @Override
   public File getDataFile(String filename)
   {
      checkValidBundleContext();
      BundleStoragePlugin storagePlugin = bundleManager.getOptionalPlugin(BundleStoragePlugin.class);
      return storagePlugin != null ? storagePlugin.getDataFile(bundleState, filename) : null;
   }

   @Override
   public Filter createFilter(String filter) throws InvalidSyntaxException
   {
      checkValidBundleContext();
      return FrameworkUtil.createFilter(filter);
   }

   void checkValidBundleContext()
   {
      if (destroyed == true)
         throw new IllegalStateException("Invalid bundle context: " + this);
   }

   private ServiceManagerPlugin getServiceManager()
   {
      return bundleState.getServiceManagerPlugin();
   }

   private FrameworkEventsPlugin getFrameworkEventsPlugin()
   {
      return bundleState.getFrameworkEventsPlugin();
   }

   @Override
   public String toString()
   {
      return "BundleContext[" + bundleState + "]";
   }
}
