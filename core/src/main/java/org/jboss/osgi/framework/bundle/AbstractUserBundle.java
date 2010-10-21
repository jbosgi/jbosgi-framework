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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.plugin.BundleDeploymentPlugin;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * This is the internal implementation of a Bundle based on a user {@link Deployment}.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Aug-2010
 */
public abstract class AbstractUserBundle extends AbstractBundle
{
   // The headers localized with the default locale
   private Dictionary<String, String> headersOnUninstall;

   AbstractUserBundle(BundleManager bundleManager, Deployment dep) throws BundleException
   {
      super(bundleManager, dep.getSymbolicName(), dep.getAttachment(BundleStorageState.class));
      createRevision(dep);
   }

   /**
    * Assert that the given bundle is an instance of DeploymentBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of DeploymentBundle
    */
   public static AbstractUserBundle assertBundleState(Bundle bundle)
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");

      if (bundle instanceof BundleWrapper)
         bundle = ((BundleWrapper)bundle).getBundleState();

      if (bundle instanceof AbstractUserBundle == false)
         throw new IllegalArgumentException("Not an DeploymentBundle: " + bundle);

      return (AbstractUserBundle)bundle;
   }

   AbstractUserRevision createRevision(Deployment deployment) throws BundleException
   {
      AbstractUserRevision revision = createRevisionInternal(deployment);
      addRevision(revision);
      return revision;
   }

   abstract AbstractUserRevision createRevisionInternal(Deployment deployment) throws BundleException;

   public ModuleClassLoader getModuleClassLoader()
   {
      return getCurrentRevision().getModuleClassLoader();
   }

   public Deployment getDeployment()
   {
      return getCurrentRevision().getDeployment();
   }

   public VirtualFile getFirstContentRoot()
   {
      return getCurrentRevision().getFirstContentRoot();
   }

   public List<VirtualFile> getContentRoots()
   {
      return getCurrentRevision().getContentRoots();
   }

   @Override
   public AbstractUserRevision getCurrentRevision()
   {
      return (AbstractUserRevision)super.getCurrentRevision();
   }

   @Override
   public String getLocation()
   {
      return getCurrentRevision().getLocation();
   }

   @Override
   public List<XModule> getAllResolverModules()
   {
      List<XModule> allModules = new ArrayList<XModule>();
      for (AbstractRevision rev : getRevisions())
         allModules.add(rev.getResolverModule());

      return allModules;
   }

   @Override
   @SuppressWarnings("unchecked")
   public Dictionary<String, String> getHeaders(String locale)
   {
      // This method must continue to return Manifest header information while this bundle is in the UNINSTALLED state,
      // however the header values must only be available in the raw and default locale values
      if (getState() == Bundle.UNINSTALLED)
         return headersOnUninstall;

      return super.getHeaders(locale);
   }

   @Override
   void updateInternal(InputStream input) throws BundleException
   {
      // Not checking that the bundle is uninstalled as that already happened

      boolean restart = false;
      if (isFragment() == false)
      {
         int state = getState();
         if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING)
         {
            // If this bundle's state is ACTIVE, STARTING  or STOPPING, this bundle is stopped as described in the Bundle.stop method.
            // If Bundle.stop throws an exception, the exception is rethrown terminating the update.
            stopInternal(Bundle.STOP_TRANSIENT);
            if (state != Bundle.STOPPING)
               restart = true;
         }
      }

      changeState(Bundle.INSTALLED);

      try
      {
         // If the Framework is unable to install the updated version of this bundle, the original
         // version of this bundle must be restored and a BundleException must be thrown after
         // completion of the remaining steps.
         createUpdateRevision(input);
      }
      catch (BundleException ex)
      {
         if (restart)
            startInternal(Bundle.START_TRANSIENT);

         throw ex;
      }
      catch (Exception ex)
      {
         BundleException be = new BundleException("Problem updating bundle");
         be.initCause(ex);

         if (restart)
            startInternal(Bundle.START_TRANSIENT);

         throw be;
      }

      getFrameworkEventsPlugin().fireBundleEvent(getBundleWrapper(), BundleEvent.UPDATED);
      if (restart)
      {
         // If this bundle's state was originally ACTIVE or STARTING, the updated bundle is started as described in the Bundle.start method.
         // If Bundle.start throws an exception, a Framework event of type FrameworkEvent.ERROR is fired containing the exception
         try
         {
            startInternal(Bundle.START_TRANSIENT);
         }
         catch (BundleException e)
         {
            getFrameworkEventsPlugin().fireFrameworkEvent(getBundleWrapper(), FrameworkEvent.ERROR, e);
         }
      }
   }

   /**
    * Creates a new Bundle Revision when the bundle is updated. Multiple Bundle Revisions
    * can co-exist at the same time.
    * @param input The stream to create the bundle revision from or <tt>null</tt>
    * if the new revision needs to be created from the same location as where the bundle
    * was initially installed.
    * @throws Exception If the bundle cannot be read, or if the update attempt to change the BSN.
    */
   private void createUpdateRevision(InputStream input) throws Exception
   {
      BundleManager bundleManager = getBundleManager();
      VirtualFile rootFile = null;

      // If the specified InputStream is null, the Framework must create the InputStream from
      // which to read the updated bundle by interpreting, in an implementation dependent manner,
      // this bundle's Bundle-UpdateLocation Manifest header, if present, or this bundle's
      // original location.
      if (input == null)
      {
         String updateLocation = getOSGiMetaData().getHeader(Constants.BUNDLE_UPDATELOCATION);
         if (updateLocation != null)
         {
            URL updateURL = new URL(updateLocation);
            rootFile = AbstractVFS.toVirtualFile(updateURL);
         }
         else
         {
            rootFile = getFirstContentRoot();
         }
      }

      if (rootFile == null && input != null)
         rootFile = AbstractVFS.toVirtualFile(getLocation(), input);

      BundleStorageState storageState;
      try
      {
         BundleStoragePlugin plugin = bundleManager.getPlugin(BundleStoragePlugin.class);
         storageState = plugin.createStorageState(getBundleId(), getLocation(), rootFile);
      }
      catch (IOException ex)
      {
         throw new BundleException("Cannot setup storage for: " + rootFile, ex);
      }
      
      try
      {
         BundleDeploymentPlugin plugin = bundleManager.getPlugin(BundleDeploymentPlugin.class);
         Deployment dep = plugin.createDeployment(storageState);
         OSGiMetaData metadata = plugin.createOSGiMetaData(dep);
         dep.addAttachment(OSGiMetaData.class, metadata);

         createRevision(dep);
         getResolverPlugin().addModule(getResolverModule());
      }
      catch (BundleException ex)
      {
         throw ex;
      }
   }

   /**
    * This method gets called by {@link PackageAdmin} when the bundle needs to be refreshed,
    * this means that all the old revisions are thrown out.
    */
   public void refresh() throws BundleException
   {
      assertNotUninstalled();
      if (isResolved() == false)
         throw new IllegalStateException("Attempt to refresh an unresolved bundle: " + this);

      changeState(Bundle.INSTALLED);

      // Remove the revisions from the resolver
      for (AbstractRevision rev : getRevisions())
      {
         XModule resModule = rev.getResolverModule();
         getResolverPlugin().removeModule(resModule);

         ModuleIdentifier identifier = rev.getModuleIdentifier();
         getModuleManagerPlugin().removeModule(identifier);
      }
      
      AbstractRevision currentRev = getCurrentRevision();
      clearRevisions();

      // Update the resolver module for the current revision
      currentRev.refreshRevision(getOSGiMetaData());
      getResolverPlugin().addModule(currentRev.getResolverModule());
   }

   /**
    * Removes uninstalled bundles
    */
   public void remove()
   {
      BundleManager bundleManager = getBundleManager();
      bundleManager.removeBundle(this);

      BundleStorageState storageState = getDeployment().getAttachment(BundleStorageState.class);
      storageState.deleteBundleStorage();
      
      ModuleManagerPlugin moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
      for (AbstractRevision rev : getRevisions())
      {
         if (isFragment() == false)
         {
            ModuleIdentifier identifier = rev.getModuleIdentifier();
            moduleManager.removeModule(identifier);
         }
      }
   }

   @Override
   public void uninstall() throws BundleException
   {
      // Cache the headers in the default locale
      headersOnUninstall = getHeaders(null);
      super.uninstall();
   }
}
