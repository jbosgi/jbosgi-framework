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

import java.io.InputStream;

import org.jboss.osgi.msc.metadata.OSGiMetaData;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;



/**
 * A host bundle.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class HostBundle extends AbstractBundle
{
   private long bundleId;
   private String location;
   private OSGiMetaData metadata;
   
   public HostBundle(BundleManager bundleManager, OSGiMetaData metadata, String location)
   {
      super(bundleManager, metadata.getBundleSymbolicName());
      this.metadata = metadata;
      this.location = location;
      if (location == null)
         throw new IllegalArgumentException("Null location");
      
      // Set the bundle version if available
      String versionstr = metadata.getBundleVersion();
      if (versionstr != null)
      {
         Version version = Version.parseVersion(versionstr);
         setVersion(version);
      }
   }

   public OSGiMetaData getOSGiMetaData()
   {
      return metadata;
   }
   
   @Override
   public long getBundleId()
   {
      return bundleId;
   }

   @Override
   public String getLocation()
   {
      return location;
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      return new HostBundleContext(this);
   }

   void startInternal() throws BundleException
   {
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
            Object result = loadClass(bundleActivatorClassName).newInstance();
            if (result instanceof BundleActivator == false)
               throw new BundleException(bundleActivatorClassName + " is not an implementation of " + BundleActivator.class.getName());

            BundleActivator bundleActivator = (BundleActivator)result;
            bundleActivator.start(getBundleContext());
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

   void stopInternal()
   {
      throw new NotImplementedException();
   }

   void updateInternal(InputStream input)
   {
      throw new NotImplementedException();
   }

   void uninstallInternal()
   {
      throw new NotImplementedException();
   }
}
