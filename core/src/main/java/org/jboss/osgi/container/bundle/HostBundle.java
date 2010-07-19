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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.plugin.StartLevelPlugin;
import org.jboss.osgi.container.util.AggregatedVirtualFile;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;

/**
 * A host bundle.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class HostBundle extends AbstractBundle
{
   // Provide logging
   private static final Logger log = Logger.getLogger(HostBundle.class);
   
   private String location;
   private OSGiMetaData metadata;
   private BundleActivator bundleActivator;
   private XModule resolverModule;
   private VirtualFile rootFile;
   private int startLevel = StartLevelPlugin.BUNDLE_STARTLEVEL_UNSPECIFIED;

   public HostBundle(BundleManager bundleManager, Deployment dep) throws BundleException
   {
      super(bundleManager, dep.getSymbolicName());

      metadata = dep.getAttachment(OSGiMetaData.class);
      location = dep.getLocation();
      rootFile = dep.getRoot();

      if (metadata == null)
         throw new IllegalArgumentException("Null metadata");
      if (location == null)
         throw new IllegalArgumentException("Null location");
      if (rootFile == null)
         throw new IllegalArgumentException("Null rootFile");

      // Set the aggregated root file
      rootFile = AggregatedVirtualFile.aggregatedBundleClassPath(rootFile, metadata);

      // Set the bundle version
      setVersion(metadata.getBundleVersion());

      // Create the resolver module
      XModuleBuilder builder = XResolverFactory.getModuleBuilder();
      resolverModule = builder.createModule(getBundleId(), metadata);

      // In case this bundle is a module.xml deployment, we already have a ModuleSpec
      ModuleSpec moduleSpec = dep.getAttachment(ModuleSpec.class);
      if (moduleSpec != null)
         resolverModule.addAttachment(ModuleSpec.class, moduleSpec);

      StartLevelPlugin sl = bundleManager.getOptionalPlugin(StartLevelPlugin.class);
      if (sl != null)
         startLevel = sl.getInitialBundleStartLevel();
   }

   /**
    * Assert that the given bundle is an instance of HostBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of HostBundle
    */
   public static HostBundle assertBundleState(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);

      if (bundleState instanceof HostBundle == false)
         throw new IllegalArgumentException("Not an HostBundle: " + bundleState);

      return (HostBundle)bundleState;
   }

   @Override
   public OSGiMetaData getOSGiMetaData()
   {
      return metadata;
   }

   @Override
   public XModule getResolverModule()
   {
      return resolverModule;
   }

   @Override
   public VirtualFile getRootFile()
   {
      return rootFile;
   }

   @Override
   public String getLocation()
   {
      return location;
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      return new HostBundleContext(this, null);
   }

   @Override
   public Class<?> loadClass(String className) throws ClassNotFoundException
   {
      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      if (checkResolved() == false)
         throw new ClassNotFoundException("Class '" + className + "' not found in: " + this);

      // Load the class through the module
      ModuleClassLoader loader = getBundleClassLoader();
      return loader.loadClass(className);
   }

   private boolean checkResolved()
   {
      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle 
      // [TODO] If this bundle cannot be resolved, a Framework event of type FrameworkEvent.ERROR is fired 
      //        containing a BundleException with details of the reason this bundle could not be resolved. 
      //        This method must then throw a ClassNotFoundException.
      if (getState() == Bundle.INSTALLED)
         getResolverPlugin().resolve(Collections.singletonList((AbstractBundle)this));

      return getBundleClassLoader() != null;
   }

   @Override
   public URL getResource(String name)
   {
      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      checkResolved();

      ModuleClassLoader classLoader = getBundleClassLoader();
      if (classLoader != null)
      {
         return classLoader.getResource(name);
      }

      // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
      try
      {
         VirtualFile child = getRootFile().getChild(name);
         return child != null ? child.toURL() : null;
      }
      catch (IOException ex)
      {
         log.error("Cannot get resource: " + name, ex);
         return null;
      }
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getResources(String name) throws IOException
   {
      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
      checkResolved();

      ModuleClassLoader classLoader = getBundleClassLoader();
      if (classLoader != null)
      {
         return classLoader.getResources(name);
      }

      // If this bundle cannot be resolved, then only this bundle must be searched for the specified resource
      try
      {
         VirtualFile child = getRootFile().getChild(name);
         if (child == null)
            return null;
         
         Vector<URL> vector = new Vector<URL>();
         vector.add(child.toURL());
         return vector.elements();
      }
      catch (IOException ex)
      {
         log.error("Cannot get resource: " + name, ex);
         return null;
      }
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getEntryPaths(String path)
   {
      try
      {
         return getRootFile().getEntryPaths(path);
      }
      catch (IOException ex)
      {
         return null;
      }
   }

   @Override
   public URL getEntry(String path)
   {
      try
      {
         VirtualFile child = getRootFile().getChild(path);
         return child != null ? child.toURL() : null;
      }
      catch (IOException ex)
      {
         log.error("Cannot get entry: " + path, ex);
         return null;
      }
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration findEntries(String path, String pattern, boolean recurse)
   {
      try
      {
         return getRootFile().findEntries(path, pattern, recurse);
      }
      catch (IOException ex)
      {
         return null;
      }
   }

   public int getStartLevel()
   {
      return startLevel;
   }

   public void setStartLevel(int sl)
   {
      startLevel = sl;
   }

   @Override
   public void start(int options) throws BundleException
   {
      if ((options & Bundle.START_TRANSIENT) == 0)
         setPersistentlyStarted(true);

      if (isPersistentlyStarted())
      {
         StartLevelPlugin sl = getBundleManager().getOptionalPlugin(StartLevelPlugin.class);
         if (sl == null || sl.getStartLevel() >= getStartLevel())
            super.start(options);
      }
   }

   void startInternal() throws BundleException
   {
      if (getState() == Bundle.UNINSTALLED)
         throw new IllegalStateException("Cannot start an uninstalled bundle: " + this);

      // Resolve all installed bundles 
      if (getState() == Bundle.INSTALLED)
      {
         getResolverPlugin().resolve(this);
      }

      // This bundle's state is set to STARTING
      // A bundle event of type BundleEvent.STARTING is fired
      createBundleContext();
      changeState(Bundle.STARTING);

      // The BundleActivator.start(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is specified, is called. 
      try
      {
         OSGiMetaData osgiMetaData = getOSGiMetaData();
         if (osgiMetaData == null)
            throw new IllegalStateException("Cannot obtain OSGi meta data");

         // Do we have a bundle activator
         String bundleActivatorClassName = osgiMetaData.getBundleActivator();
         if (bundleActivatorClassName != null)
         {
            if (bundleActivatorClassName.equals(ModuleActivatorBridge.class.getName()))
            {
               bundleActivator = new ModuleActivatorBridge();
               bundleActivator.start(getBundleContextInternal());
            }
            else
            {
               Object result = loadClass(bundleActivatorClassName).newInstance();
               if (result instanceof BundleActivator == false)
                  throw new BundleException(bundleActivatorClassName + " is not an implementation of " + BundleActivator.class.getName());
               
               bundleActivator = (BundleActivator)result;
               bundleActivator.start(getBundleContext());
            }
         }

         if (getState() != STARTING)
            throw new BundleException("Bundle has been uninstalled: " + this);

         changeState(ACTIVE);
      }

      // If the BundleActivator is invalid or throws an exception then:
      //   * This bundle's state is set to STOPPING.
      //   * A bundle event of type BundleEvent.STOPPING is fired.
      //   * Any services registered by this bundle must be unregistered.
      //   * Any services used by this bundle must be released.
      //   * Any listeners registered by this bundle must be removed.
      //   * This bundle's state is set to RESOLVED.
      //   * A bundle event of type BundleEvent.STOPPED is fired.
      //   * A BundleException is then thrown.
      catch (Throwable t)
      {
         // This bundle's state is set to STOPPING
         // A bundle event of type BundleEvent.STOPPING is fired
         changeState(STOPPING);

         // Any services registered by this bundle must be unregistered.
         // Any services used by this bundle must be released.
         // Any listeners registered by this bundle must be removed.
         stopInternal();

         // This bundle's state is set to RESOLVED
         // A bundle event of type BundleEvent.STOPPED is fired
         destroyBundleContext();
         changeState(RESOLVED);

         if (t instanceof BundleException)
            throw (BundleException)t;

         throw new BundleException("Cannot start bundle: " + this, t);
      }
   }

   @Override
   public void stop(int options) throws BundleException
   {
      if ((options & Bundle.STOP_TRANSIENT) == 0)
         setPersistentlyStarted(false);

      super.stop(options);
   }

   void stopInternal() throws BundleException
   {
      // If this bundle's state is UNINSTALLED then an IllegalStateException is thrown. 
      if (getState() == Bundle.UNINSTALLED)
         throw new IllegalStateException("Bundle already uninstalled: " + this);

      // [TODO] If this bundle is in the process of being activated or deactivated then this method must wait for activation or deactivation 
      // to complete before continuing. If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle 
      // was unable to be stopped.

      // [TODO] If the STOP_TRANSIENT option is not set then then set this bundle's persistent autostart setting to to Stopped. 
      // When the Framework is restarted and this bundle's autostart setting is Stopped, this bundle must not be automatically started. 

      // If this bundle's state is not STARTING or ACTIVE then this method returns immediately
      if (getState() != Bundle.STARTING && getState() != Bundle.ACTIVE)
         return;

      // This bundle's state is set to STOPPING
      // A bundle event of type BundleEvent.STOPPING is fired
      int priorState = getState();
      changeState(STOPPING);

      // If this bundle's state was ACTIVE prior to setting the state to STOPPING, 
      // the BundleActivator.stop(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is specified, is called. 
      // If that method throws an exception, this method must continue to stop this bundle and a BundleException must be thrown after completion 
      // of the remaining steps.
      Throwable rethrow = null;
      if (priorState == Bundle.ACTIVE)
      {
         if (bundleActivator != null && getBundleContext() != null)
         {
            try
            {
               if (bundleActivator instanceof ModuleActivatorBridge)
               {
                  bundleActivator.stop(getBundleContextInternal());
               }
               else
               {
                  bundleActivator.stop(getBundleContext());
               }
            }
            catch (Throwable t)
            {
               rethrow = t;
            }
         }
      }

      // Any services registered by this bundle must be unregistered
      List<ServiceState> ownedServices = getOwnedServices();
      for (ServiceState serviceState : ownedServices)
         getServiceManagerPlugin().unregisterService(serviceState);

      // [TODO] Any listeners registered by this bundle must be removed

      // If this bundle's state is UNINSTALLED, because this bundle was uninstalled while the 
      // BundleActivator.stop method was running, a BundleException must be thrown
      if (getState() == Bundle.UNINSTALLED)
         throw new BundleException("Bundle uninstalled during activator stop: " + this);

      // This bundle's state is set to RESOLVED
      // A bundle event of type BundleEvent.STOPPED is fired
      destroyBundleContext();
      changeState(RESOLVED);

      if (rethrow != null)
         throw new BundleException("Error during stop of bundle: " + this, rethrow);
   }

   void updateInternal(InputStream input)
   {
      throw new NotImplementedException();
   }

   void uninstallInternal() throws BundleException
   {
      BundleManager bundleManager = getBundleManager();
      if (bundleManager.getBundleById(getBundleId()) == null)
         throw new BundleException("Not installed: " + this);

      // If this bundle's state is ACTIVE, STARTING or STOPPING, this bundle is stopped 
      // as described in the Bundle.stop method.
      int state = getState();
      if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING)
      {
         try
         {
            stopInternal();
         }
         catch (Exception ex)
         {
            // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is
            // fired containing the exception
            bundleManager.fireError(this, "Error stopping bundle: " + this, ex);
         }
      }

      bundleManager.removeBundleState(this);
   }
}
