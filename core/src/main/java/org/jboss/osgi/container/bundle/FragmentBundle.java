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

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.resolver.XModule;
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
public class FragmentBundle extends DeploymentBundle
{
   FragmentBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment);
   }

   @Override
   AbstractRevision createRevision(Deployment deployment, int revision) throws BundleException
   {
      return new FragmentRevision(this, deployment, revision);
   }

   @Override
   public void addToResolver()
   {
      // fragments not supported in resolver 
   }

   @Override
   public void removeFromResolver()
   {
      // fragments not supported in resolver 
   }

   @Override
   public List<XModule> getAllResolverModules()
   {
      return Collections.emptyList();
   }

   @Override
   public boolean ensureResolved()
   {
      throw new NotImplementedException();
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      throw new NotImplementedException();
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

   @Override
   void updateInternal(InputStream input) throws BundleException
   {
      throw new NotImplementedException();
   }

   @Override
   void uninstallInternal() throws BundleException
   {
      BundleManager bundleManager = getBundleManager();
      bundleManager.uninstallBundleState(this);
   }
}
