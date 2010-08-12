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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * This is the internal implementation of a Bundle based on a user {@link Deployment}. 
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
public abstract class DeploymentBundle extends AbstractBundle
{
   private static final Logger log = Logger.getLogger(DeploymentBundle.class);

   // The associated deployment
   private final Deployment deployment;
   // This list contains any revisions of the bundle that are updated by newer ones, but still available
   private final List<AbstractRevision> updatedRevisions = new CopyOnWriteArrayList<AbstractRevision>();
   // The current revision is the most recent revision of the bundle. 
   private AbstractRevision currentRevision;
   // The revision counter
   private final AtomicInteger revisionCounter = new AtomicInteger(0);

   DeploymentBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment.getSymbolicName());
      this.deployment = deployment;

      createRevision(deployment);
   }

   /**
    * Assert that the given bundle is an instance of DeploymentBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of DeploymentBundle
    */
   public static DeploymentBundle assertBundleState(Bundle bundle)
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");

      if (bundle instanceof BundleWrapper)
         bundle = ((BundleWrapper)bundle).getBundleState();

      if (bundle instanceof DeploymentBundle == false)
         throw new IllegalArgumentException("Not an DeploymentBundle: " + bundle);

      return (DeploymentBundle)bundle;
   }

   public Deployment getDeployment()
   {
      return deployment;
   }

   public VirtualFile getContentRoot()
   {
      return deployment.getRoot();
   }

   @Override
   public String getLocation()
   {
      return deployment.getLocation();
   }

   AbstractRevision createRevision(Deployment deployment) throws BundleException
   {
      if (currentRevision != null)
         updatedRevisions.add(currentRevision);

      currentRevision = createRevision(deployment, revisionCounter.incrementAndGet());
      return currentRevision;
   }

   abstract AbstractRevision createRevision(Deployment deployment, int revision) throws BundleException;

   AbstractRevision getCurrentRevision()
   {
      return currentRevision;
   }

   List<AbstractRevision> getRevisions()
   {
      List<AbstractRevision> result = new ArrayList<AbstractRevision>();
      result.addAll(updatedRevisions);
      if (currentRevision != null)
         result.add(currentRevision);
      
      return Collections.unmodifiableList(result);
   }

   void clearRevisions()
   {
      updatedRevisions.clear();
   }
}
