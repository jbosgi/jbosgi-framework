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

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * This is the internal implementation of a fragment Bundle.
 *
 * Fragment specific functionality is handled here.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
public final class FragmentBundle extends AbstractUserBundle
{
   FragmentBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment);
   }

   /**
    * Assert that the given bundle is an instance of FragmentBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of FragmentBundle
    */
   public static FragmentBundle assertBundleState(Bundle bundle)
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");

      if (bundle instanceof BundleWrapper)
         bundle = ((BundleWrapper)bundle).getBundleState();

      if (bundle instanceof FragmentBundle == false)
         throw new IllegalArgumentException("Not an FragmentBundle: " + bundle);

      return (FragmentBundle)bundle;
   }

   @Override
   AbstractUserRevision createRevisionInternal(Deployment deployment) throws BundleException
   {
      return new FragmentRevision(this, deployment);
   }

   @Override
   public FragmentRevision getCurrentRevision()
   {
      return (FragmentRevision)super.getCurrentRevision();
   }

   @Override
   public boolean isFragment()
   {
      return true;
   }

   @Override
   public boolean ensureResolved()
   {
      throw new NotImplementedException();
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      // If this bundle is a fragment bundle, then this bundle has no valid BundleContext.
      // This method will return null if this bundle has no valid BundleContext.
      return null;
   }

   @Override
   void startInternal(int options) throws BundleException
   {
      throw new BundleException("Fragments cannot be started");
   }

   @Override
   void stopInternal(int options) throws BundleException
   {
      throw new BundleException("Fragments cannot be stoped");
   }
}
