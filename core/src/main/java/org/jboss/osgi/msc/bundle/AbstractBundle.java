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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Manifest;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.msc.metadata.OSGiMetaData;
import org.jboss.osgi.msc.metadata.internal.OSGiManifestMetaData;
import org.jboss.osgi.spi.NotImplementedException;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * The base of all {@link Bundle} implementations.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public abstract class AbstractBundle implements Bundle
{
   private BundleManager bundleManager;
   private BundleWrapper bundleWrapper;
   private BundleContext bundleContext;
   private AtomicInteger bundleState = new AtomicInteger(UNINSTALLED);
   private Version version = Version.emptyVersion;
   private String symbolicName;
   
   public AbstractBundle(BundleManager bundleManager, String symbolicName)
   {
      this.bundleManager = bundleManager;
      this.symbolicName = symbolicName;
      if (bundleManager == null)
         throw new IllegalArgumentException("Null bundleManager");
      if (symbolicName == null)
         throw new IllegalArgumentException("Null symbolicName");
   }

   public static AbstractBundle createBundle(BundleManager bundleManager, Deployment dep) throws BundleException
   {
      Manifest manifest;
      try
      {
         manifest = VFSUtils.getManifest(dep.getRoot());
      }
      catch (IOException ex)
      {
         throw new BundleException("Cannot obtain manifest from: " + dep);
      }
      
      OSGiMetaData metadata = new OSGiManifestMetaData(manifest);
      if (metadata.getFragmentHost() != null)
         throw new NotImplementedException("Fragments not support");
      
      AbstractBundle bundleState = new HostBundle(bundleManager, metadata, dep.getLocation());
      return bundleState;
   }
   
   /**
    * Assert that the given bundle is an instance of AbstractBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of AbstractBundle
    */
   public static AbstractBundle assertBundleState(Bundle bundle)
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");

      if (bundle instanceof BundleWrapper)
         bundle = ((BundleWrapper)bundle).getBundleState();

      if (bundle instanceof AbstractBundle == false)
         throw new IllegalArgumentException("Not an AbstractBundle: " + bundle);

      return (AbstractBundle)bundle;
   }

   public BundleManager getBundleManager()
   {
      return bundleManager;
   }

   public Bundle getBundleWrapper()
   {
      if (bundleWrapper == null)
         bundleWrapper = new BundleWrapper(this);
      
      return bundleWrapper;
   }
   
   public void changeState(int newstate)
   {
      bundleState.getAndSet(newstate);
   }
   
   @Override
   public int getState()
   {
      return bundleState.get();
   }

   public BundleContext getBundleContext()
   {
      return bundleContext;
   }

   void createBundleContext()
   {
      if (bundleContext != null)
         throw new IllegalStateException("BundleContext already available");
      bundleContext = createContextInternal();
   }
   
   abstract AbstractBundleContext createContextInternal();
   
   void destroyBundleContext()
   {
      bundleContext = null;
   }
   
   @Override
   public void start(int options) throws BundleException
   {
      startInternal();
   }

   @Override
   public void start() throws BundleException
   {
      startInternal();
   }

   abstract void startInternal() throws BundleException;

   @Override
   public void stop(int options) throws BundleException
   {
      stopInternal();
   }

   @Override
   public void stop() throws BundleException
   {
      stopInternal();
   }

   abstract void stopInternal() throws BundleException;

   @Override
   public void update(InputStream input) throws BundleException
   {
      updateInternal(input);
   }

   @Override
   public void update() throws BundleException
   {
      updateInternal(null);
   }

   abstract void updateInternal(InputStream input) throws BundleException;

   @Override
   public void uninstall() throws BundleException
   {
      uninstallInternal();
   }

   abstract void uninstallInternal() throws BundleException;
   
   @Override
   @SuppressWarnings("rawtypes")
   public Dictionary getHeaders()
   {
      throw new NotImplementedException();
   }

   @Override
   public ServiceReference[] getRegisteredServices()
   {
      throw new NotImplementedException();
   }

   @Override
   public ServiceReference[] getServicesInUse()
   {
      throw new NotImplementedException();
   }

   @Override
   public boolean hasPermission(Object permission)
   {
      throw new NotImplementedException();
   }

   @Override
   public URL getResource(String name)
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Dictionary getHeaders(String locale)
   {
      throw new NotImplementedException();
   }

   @Override
   public String getSymbolicName()
   {
      return symbolicName;
   }

   @Override
   public Version getVersion()
   {
      return version;
   }
   
   void setVersion(Version version)
   {
      this.version = version;
   }

   @Override
   public Class<?> loadClass(String name) throws ClassNotFoundException
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getResources(String name) throws IOException
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getEntryPaths(String path)
   {
      throw new NotImplementedException();
   }

   @Override
   public URL getEntry(String path)
   {
      throw new NotImplementedException();
   }

   @Override
   public long getLastModified()
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration findEntries(String path, String filePattern, boolean recurse)
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Map getSignerCertificates(int signersType)
   {
      throw new NotImplementedException();
   }
}
