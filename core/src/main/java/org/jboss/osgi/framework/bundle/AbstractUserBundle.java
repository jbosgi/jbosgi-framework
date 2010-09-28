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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.plugin.BundleDeploymentPlugin;
import org.jboss.osgi.framework.plugin.BundleStoragePlugin;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;

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
   private final AtomicInteger revCounter = new AtomicInteger(0);

   AbstractUserBundle(BundleManager bundleManager, Deployment dep) throws BundleException
   {
      super(bundleManager, dep.getSymbolicName());
      createRevision(dep, incrementRevisionCount(dep));
   }

   // The initial revision count is greater than 1 if the framework
   // still retains UNISTALLED bundles with the same symbolic name and version
   private int incrementRevisionCount(Deployment dep) throws BundleException
   {
      int revCount = revCounter.incrementAndGet();
      XModule resModule = dep.getAttachment(XModule.class);
      XModuleIdentity moduleId = (resModule != null ? resModule.getModuleId() : null);
      if (moduleId == null)
      {
         OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
         String name = metadata.getBundleSymbolicName();
         Version version = metadata.getBundleVersion();

         XModuleBuilder builder = getResolverPlugin().getModuleBuilder();
         builder.createModule(name, version, revCount);
         moduleId = builder.getModuleIdentity();
      }
      resModule = getResolverPlugin().getModuleById(moduleId);
      if (resModule != null)
      {
         Bundle resBundle = resModule.getAttachment(Bundle.class);
         if (resBundle.getState() == Bundle.UNINSTALLED)
            return incrementRevisionCount(dep);
      }
      return revCount;
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

   AbstractUserRevision createRevision(Deployment deployment, int revCount) throws BundleException
   {
      AbstractUserRevision revision = createRevisionInternal(deployment, revCount);
      addRevision(revision);
      return revision;
   }

   abstract AbstractUserRevision createRevisionInternal(Deployment deployment, int revCount) throws BundleException;

   public ModuleClassLoader getModuleClassLoader()
   {
      return getCurrentRevision().getModuleClassLoader();
   }

   public Deployment getDeployment()
   {
      return getCurrentRevision().getDeployment();
   }

   @Override
   public AbstractUserRevision getCurrentRevision()
   {
      return (AbstractUserRevision)super.getCurrentRevision();
   }

   public VirtualFile getContentRoot()
   {
      return getCurrentRevision().getContentRoot();
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
         createRevision(input);
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
   private void createRevision(InputStream input) throws Exception
   {
      BundleManager bundleManager = getBundleManager();
      InputStream internalInput = null;
      VirtualFile internalRoot = null;

      int revCount = revCounter.incrementAndGet();

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
            internalRoot = AbstractVFS.getRoot(updateURL);
            internalInput = internalRoot.openStream();
         }
         else
         {
            internalRoot = getCurrentRevision().getContentRoot();
            internalInput = internalRoot.openStream();
         }
      }

      VirtualFile newRoot;
      try
      {
         BundleStoragePlugin plugin = bundleManager.getPlugin(BundleStoragePlugin.class);
         InputStream newInput = (input != null ? input : internalInput);
         File newFile = plugin.storeBundleStream(getLocation(), newInput, revCount);
         newRoot = AbstractVFS.getRoot(newFile.toURI().toURL());
      }
      catch (IOException ex)
      {
         if (internalRoot != null)
            bundleManager.deleteContentRoot(internalRoot);

         throw new BundleException("Cannot store bundle from stream", ex);
      }

      try
      {
         BundleDeploymentPlugin plugin = bundleManager.getPlugin(BundleDeploymentPlugin.class);
         Deployment dep = plugin.createDeployment(newRoot, getLocation());
         OSGiMetaData metadata = plugin.createOSGiMetaData(dep);
         dep.addAttachment(OSGiMetaData.class, metadata);

         createRevision(dep, revCount);
         getResolverPlugin().addModule(getResolverModule());
      }
      catch (BundleException ex)
      {
         if (internalRoot != null)
            bundleManager.deleteContentRoot(internalRoot);

         throw ex;
      }
   }

   /**
    * This method gets called by Package Admin when the bundle needs to be refreshed,
    * this means that all the old revisions are thrown out.
    */
   public void refresh() throws BundleException
   {
      assertNotUninstalled();
      if (isResolved() == false)
         throw new IllegalStateException("Attempt to refresh an unresolved bundle: " + this);

      changeState(Bundle.INSTALLED);

      BundleManager bundleManager = getBundleManager();
      AbstractRevision currentRev = getCurrentRevision();

      // Remove the revisions from the resolver
      for (AbstractRevision rev : getRevisions())
      {
         XModule resModule = rev.getResolverModule();
         getResolverPlugin().removeModule(resModule);

         ModuleIdentifier identifier = rev.getModuleIdentifier();
         getModuleManagerPlugin().removeModule(identifier);

         // Delete the content root file
         if (rev != currentRev)
         {
            AbstractUserRevision userRev = (AbstractUserRevision)rev;
            VirtualFile contentRoot = userRev.getContentRoot();
            bundleManager.deleteContentRoot(contentRoot);
         }
      }
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
      // Get a snapshot of all revisions before we clear the list
      List<AbstractRevision> revisions = new ArrayList<AbstractRevision>(getRevisions());

      BundleManager bundleManager = getBundleManager();
      bundleManager.removeBundle(this);

      ModuleManagerPlugin moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
      for (AbstractRevision rev : revisions)
      {
         if (isFragment() == false)
         {
            ModuleIdentifier identifier = rev.getModuleIdentifier();
            moduleManager.removeModule(identifier);
         }

         AbstractUserRevision userRev = (AbstractUserRevision)rev;
         bundleManager.deleteContentRoot(userRev.getContentRoot());
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
