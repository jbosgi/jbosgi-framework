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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.jboss.osgi.deployment.deployer.DeployerService;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.plugin.BundleDeploymentPlugin;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.framework.plugin.DeployerServicePlugin;
import org.jboss.osgi.framework.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.framework.plugin.ServiceManagerPlugin;
import org.jboss.osgi.framework.plugin.internal.BundleProtocolHandler;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
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
   private final BundleManager bundleManager;
   private final AbstractBundle bundleState;
   private final DeployerService deployerService;
   private final BundleStoragePlugin storagePlugin;
   private final BundleDeploymentPlugin deploymentPlugin;
   private BundleContext contextWrapper;
   private boolean destroyed;

   AbstractBundleContext(AbstractBundle bundleState)
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");
      
      this.bundleManager = bundleState.getBundleManager();
      this.bundleState = bundleState;
      
      // Cache the frequently used plugins
      this.deployerService = bundleManager.getPlugin(DeployerServicePlugin.class);
      this.storagePlugin = bundleManager.getPlugin(BundleStoragePlugin.class);
      this.deploymentPlugin = bundleManager.getPlugin(BundleDeploymentPlugin.class);
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
   public Bundle installBundle(String location) throws BundleException
   {
      return installBundleInternal(location, null);
   }

   @Override
   public Bundle installBundle(String location, InputStream input) throws BundleException
   {
      return installBundleInternal(location, input);
   }

   /*
    * This is the entry point for all bundle deployments.
    * 
    * #1 Construct the {@link VirtualFile} from the given parameters
    * #2 Setup Bundle permanent storage
    * #3 Create a Bundle {@link Deployment}
    * #4 Deploy the Bundle through the {@link DeploymentService}
    * 
    * The {@link DeploymentService} is the integration point for JBossAS.
    * 
    * By default the {@link DeployerServicePlugin} simply delegates to {@link BundleManager#installBundle(Deployment)}
    * In JBossAS however, the {@link DeployerServicePlugin} delegates to the management API that feeds the Bundle
    * deployment through the DeploymentUnitProcessor chain.
    */
   private Bundle installBundleInternal(String location, InputStream input) throws BundleException
   {
      checkValidBundleContext();
      
      VirtualFile rootFile = null;
      if (input != null)
      {
         try
         {
            rootFile = AbstractVFS.toVirtualFile(location, input);
         }
         catch (IOException ex)
         {
            throw new BundleException("Cannot obtain virtual file from input stream", ex);
         }
      }

      // Try location as URL
      if (rootFile == null)
      {
         try
         {
            URL url = new URL(location);
            if (BundleProtocolHandler.PROTOCOL_NAME.equals(url.getProtocol()))
            {
               rootFile = AbstractVFS.toVirtualFile(location, url.openStream());
            }
            else
            {
               rootFile = AbstractVFS.toVirtualFile(url);
            }
         }
         catch (IOException ex)
         {
            // Ignore, not a valid URL
         }
      }

      // Try location as File
      if (rootFile == null)
      {
         try
         {
            File file = new File(location);
            if (file.exists())
               rootFile = AbstractVFS.toVirtualFile(file.toURI().toURL());
         }
         catch (IOException ex)
         {
            throw new BundleException("Cannot obtain virtual file from: " + location, ex);
         }
      }

      if (rootFile == null)
         throw new BundleException("Cannot obtain virtual file from: " + location);
      
      BundleStorageState storageState;
      try
      {
         storageState = storagePlugin.createStorageState(bundleManager.getNextBundleId(), location, rootFile);
      }
      catch (IOException ex)
      {
         throw new BundleException("Cannot setup storage for: " + rootFile, ex);
      }

      AbstractBundle bundleState;
      try
      {
         Deployment dep = deploymentPlugin.createDeployment(storageState);
         bundleState = AbstractBundle.assertBundleState(deployerService.deploy(dep));
      }
      catch (BundleException ex)
      {
         storageState.deleteBundleStorage();
         throw ex;
      }
      catch (RuntimeException rte)
      {
         storageState.deleteBundleStorage();
         throw new BundleException("Cannot install bundle: " + location, rte);
      }
      
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
