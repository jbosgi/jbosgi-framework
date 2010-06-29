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
package org.jboss.osgi.msc.framework.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * OSGiBundleWrapper.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class BundleWrapper implements Bundle
{
   /** The bundle state */
   private AbstractBundle bundleState;

   /**
    * Create a new OSGiBundleImpl.
    * 
    * @param bundle the bundle state
    * @throws IllegalArgumentException for a null parameter
    */
   public BundleWrapper(AbstractBundle bundleState)
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");
      this.bundleState = bundleState;
   }

   AbstractBundle getBundleState()
   {
      return bundleState;
   }
   
   @SuppressWarnings("rawtypes")
   public Enumeration findEntries(String path, String filePattern, boolean recurse)
   {
      return bundleState.findEntries(path, filePattern, recurse);
   }

   public BundleContext getBundleContext()
   {
      return bundleState.getBundleContext();
   }

   public long getBundleId()
   {
      return bundleState.getBundleId();
   }

   public URL getEntry(String path)
   {
      return bundleState.getEntry(path);
   }

   @SuppressWarnings("rawtypes")
   public Enumeration getEntryPaths(String path)
   {
      return bundleState.getEntryPaths(path);
   }

   @SuppressWarnings("rawtypes")
   public Dictionary getHeaders()
   {
      return bundleState.getHeaders();
   }

   @SuppressWarnings("rawtypes")
   public Dictionary getHeaders(String locale)
   {
      return bundleState.getHeaders(locale);
   }

   public long getLastModified()
   {
      return bundleState.getLastModified();
   }

   public String getLocation()
   {
      return bundleState.getLocation();
   }

   public ServiceReference[] getRegisteredServices()
   {
      return bundleState.getRegisteredServices();
   }

   public URL getResource(String name)
   {
      return bundleState.getResource(name);
   }

   @SuppressWarnings("rawtypes")
   public Enumeration getResources(String name) throws IOException
   {
      return bundleState.getResources(name);
   }

   public ServiceReference[] getServicesInUse()
   {
      return bundleState.getServicesInUse();
   }

   public int getState()
   {
      return bundleState.getState();
   }

   public String getSymbolicName()
   {
      return bundleState.getSymbolicName();
   }

   public Version getVersion()
   {
      return bundleState.getVersion();
   }

   @SuppressWarnings("unchecked")
   public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType)
   {
      return bundleState.getSignerCertificates(signersType);
   }

   public boolean hasPermission(Object permission)
   {
      return bundleState.hasPermission(permission);
   }

   @SuppressWarnings("rawtypes")
   public Class loadClass(String name) throws ClassNotFoundException
   {
      return bundleState.loadClass(name);
   }

   public void start() throws BundleException
   {
      bundleState.start();
   }

   public void start(int options) throws BundleException
   {
      bundleState.start(options);
   }

   public void stop() throws BundleException
   {
      bundleState.stop();
   }

   public void stop(int options) throws BundleException
   {
      bundleState.stop(options);
   }

   public void uninstall() throws BundleException
   {
      bundleState.uninstall();
   }

   public void update() throws BundleException
   {
      bundleState.update();
   }

   public void update(InputStream in) throws BundleException
   {
      bundleState.update(in);
   }

   @Override
   public int hashCode()
   {
      return bundleState.hashCode();
   }

   @Override
   public boolean equals(Object obj)
   {
      if (!(obj instanceof BundleWrapper))
         return false;

      BundleWrapper other = (BundleWrapper)obj;
      return bundleState.equals(other.bundleState);
   }

   @Override
   public String toString()
   {
      return bundleState.toString();
   }
}
