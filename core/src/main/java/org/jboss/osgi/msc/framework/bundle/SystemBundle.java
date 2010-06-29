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

import java.io.InputStream;

import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;


/**
 * The system bundle
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class SystemBundle extends AbstractBundle
{
   public SystemBundle(BundleManager bundleManager)
   {
      super(bundleManager, Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
   }

   @Override
   public long getBundleId()
   {
      return 0;
   }

   @Override
   public String getLocation()
   {
      return Constants.SYSTEM_BUNDLE_LOCATION;
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      return new SystemBundleContext(this);
   }

   @Override
   void startInternal() throws BundleException
   {
      createBundleContext();
   }

   @Override
   void stopInternal() throws BundleException
   {
      destroyBundleContext();
   }

   @Override
   void updateInternal(InputStream input) throws BundleException
   {
      throw new NotImplementedException();
   }

   @Override
   void uninstallInternal() throws BundleException
   {
      throw new NotImplementedException();
   }
}
