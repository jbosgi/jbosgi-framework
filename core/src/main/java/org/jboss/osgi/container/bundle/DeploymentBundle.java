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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * This is the internal implementation of a Bundle based on a user {@link Deployment}. 
 * 
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
public abstract class DeploymentBundle extends AbstractBundle
{
   // This list contains any revisions of the bundle that are updated by newer ones, but still available
   private final List<AbstractRevision> updatedRevisions = new CopyOnWriteArrayList<AbstractRevision>();
   // The revision counter thats gets incremnted for updates of this bundle
   private final AtomicInteger revisionCounter = new AtomicInteger(0);
   // The current revision is the most recent revision of the bundle. 
   private AbstractRevision currentRevision;

   DeploymentBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment.getSymbolicName());
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

   AbstractRevision createRevision(Deployment deployment) throws BundleException
   {
      if (currentRevision != null)
         updatedRevisions.add(currentRevision);

      currentRevision = createRevision(deployment, revisionCounter.incrementAndGet());
      return currentRevision;
   }

   abstract AbstractRevision createRevision(Deployment deployment, int revision) throws BundleException;

   public ModuleClassLoader getModuleClassLoader()
   {
      return currentRevision.getModuleClassLoader();
   }
   
   public Deployment getDeployment()
   {
      return currentRevision.getDeployment();
   }

   public VirtualFile getContentRoot()
   {
      return currentRevision.getContentRoot();
   }

   @Override
   public String getLocation()
   {
      return currentRevision.getLocation();
   }

   public AbstractRevision getCurrentRevision()
   {
      return currentRevision;
   }

   public List<AbstractRevision> getRevisions()
   {
      List<AbstractRevision> result = new ArrayList<AbstractRevision>();
      result.addAll(updatedRevisions);
      if (currentRevision != null)
         result.add(currentRevision);
      
      return Collections.unmodifiableList(result);
   }

   public void clearRevisions()
   {
      updatedRevisions.clear();
   }

   @Override
   public URL getResource(String name)
   {
      return getCurrentRevision().getResource(name);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Class loadClass(String name) throws ClassNotFoundException
   {
      return getCurrentRevision().loadClass(name);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getResources(String name) throws IOException
   {
      return getCurrentRevision().getResources(name);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getEntryPaths(String path)
   {
      return getCurrentRevision().getEntryPaths(path);
   }

   @Override
   public URL getEntry(String path)
   {
      return getCurrentRevision().getEntry(path);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration findEntries(String path, String filePattern, boolean recurse)
   {
      return getCurrentRevision().findEntries(path, filePattern, recurse);
   }

   @Override
   URL getLocalizationEntry(String entryPath)
   {
      return getCurrentRevision().getLocalizationEntry();
   }

   @Override
   public Version getVersion()
   {
      return getCurrentRevision().getVersion();
   }

   @Override
   public OSGiMetaData getOSGiMetaData()
   {
      return getCurrentRevision().getOSGiMetaData();
   }

   @Override
   public XModule getResolverModule()
   {
      return getCurrentRevision().getResolverModule();
   }
}
