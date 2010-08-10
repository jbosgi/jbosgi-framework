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
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.plugin.BundleDeploymentPlugin;
import org.jboss.osgi.container.plugin.StartLevelPlugin;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * This is the internal implementation of a Bundle. The logic related to loading of classes
 * and resources is delegated to the current {@link BundleRevision}. As bundles can be updated there
 * can be multiple bundle revisions.<p/>
 * 
 * The InternalBundle can contain multiple revisions: the current revision any number of old revisions.
 * This relates to updating of bundles. When a bundle is updated a new revision is created
 * and assigned to the current revision. However, the previous revision is kept available until 
 * {@link PackageAdmin#refreshPackages(Bundle[])} is called.<p/>
 * 
 * In addition other bundle-specific functionality is handled here, such as Start Level and the
 * Bundle Activator and internal implementations of lifecycle management. 
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public class InternalBundle extends AbstractBundle
{
   private static final Logger log = Logger.getLogger(InternalBundle.class);

   private BundleActivator bundleActivator;
   private final String location;
   // This list contains any revisions of the bundle that are updated by newer ones, but still available
   private List<AbstractBundleRevision> oldRevisions = new CopyOnWriteArrayList<AbstractBundleRevision>();
   // The current revision is the most recent revision of the bundle. 
   private AbstractBundleRevision currentRevision;
   private int startLevel = StartLevelPlugin.BUNDLE_STARTLEVEL_UNSPECIFIED;
   private boolean persistentlyStarted;
   private AtomicInteger revisionCounter = new AtomicInteger(0);

   InternalBundle(BundleManager bundleManager, Deployment deployment) throws BundleException
   {
      super(bundleManager, deployment.getSymbolicName());
      location = deployment.getLocation();
      if (location == null)
         throw new IllegalArgumentException("Null location");

      currentRevision = new BundleRevision(this, deployment, revisionCounter.getAndIncrement());

      StartLevelPlugin sl = getBundleManager().getOptionalPlugin(StartLevelPlugin.class);
      if (sl != null)
         startLevel = sl.getInitialBundleStartLevel();
   }

   /**
    * Assert that the given bundle is an instance of InternalBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of InternalBundle
    */
   public static InternalBundle assertBundleState(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);

      if (bundleState instanceof InternalBundle == false)
         throw new IllegalArgumentException("Not an InternalBundle: " + bundleState);

      return (InternalBundle)bundleState;
   }

   @Override
   public void addToResolver()
   {
      getResolverPlugin().addRevision(currentRevision);
   }

   @Override
   public boolean ensureResolved()
   {
      // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle 
      // [TODO] If this bundle cannot be resolved, a Framework event of type FrameworkEvent.ERROR is fired 
      //        containing a BundleException with details of the reason this bundle could not be resolved. 
      //        This method must then throw a ClassNotFoundException.
      if (getState() == Bundle.INSTALLED)
         getResolverPlugin().resolve(Collections.<Revision> singletonList(currentRevision));

      // If the bundle has a ClassLoader it is in state {@link Bundle#RESOLVED}
      return currentRevision.getBundleClassLoader() != null;
   }

   @Override
   public void removeFromResolver()
   {
      for (AbstractBundleRevision abr : oldRevisions)
         getResolverPlugin().removeRevision(abr);

      if (currentRevision != null)
         getResolverPlugin().removeRevision(currentRevision);

      oldRevisions.clear();
      currentRevision = null;
   }

   @Override
   AbstractBundleContext createContextInternal()
   {
      return new InternalBundleContext(this, null);
   }

   @Override
   public String getLocation()
   {
      return location;
   }

   public int getStartLevel()
   {
      return startLevel;
   }

   public void setStartLevel(int sl)
   {
      startLevel = sl;
   }

   public boolean isPersistentlyStarted()
   {
      return persistentlyStarted;
   }

   public void setPersistentlyStarted(boolean started)
   {
      persistentlyStarted = started;
   }

   /** 
    * This method gets called by Package Admin when the bundle needs to be refreshed,
    * this means that all the old revisions are thrown out.
    */
   public void refresh() throws BundleException
   {
      List<AbstractBundleRevision> oldRevs = oldRevisions;
      oldRevisions = new CopyOnWriteArrayList<AbstractBundleRevision>();

      // Remove the old revisions from the resolver
      for (AbstractBundleRevision abr : oldRevs)
         getResolverPlugin().removeRevision(abr);
   }
   
   /**
    * Removes uninstalled bundles, called by Package Admin
    */
   public void remove()
   {
      getBundleManager().removeBundleState(this);

      removeFromResolver();
   }

   @Override
   void startInternal(int options) throws BundleException
   {
      // If this bundle's state is ACTIVE then this method returns immediately.
      if (getState() == Bundle.ACTIVE)
         return;

      if ((options & Bundle.START_TRANSIENT) == 0)
         setPersistentlyStarted(true);

      StartLevelPlugin plugin = getBundleManager().getOptionalPlugin(StartLevelPlugin.class);
      if (plugin != null && plugin.getStartLevel() < getStartLevel())
         // Not at the required start level yet. This bundle will be started later once
         // the required start level has been reached.
         // TODO the spec says that we need to throw a BundleException here... 
         return;

      OSGiMetaData osgiMetaData = getOSGiMetaData();
      if (osgiMetaData == null)
         throw new IllegalStateException("Cannot obtain OSGi meta data");

      // Resolve this bundles 
      if (getState() == Bundle.INSTALLED)
         getResolverPlugin().resolve(currentRevision);

      // The BundleActivator.start(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is specified, is called. 
      try
      {
         // Create the bundle context
         createBundleContext();

         // This bundle's state is set to STARTING
         changeState(Bundle.STARTING);

         // Do we have a bundle activator
         String bundleActivatorClassName = osgiMetaData.getBundleActivator();
         if (bundleActivatorClassName != null)
         {
            if (bundleActivatorClassName.equals(ModuleActivatorBridge.class.getName()))
            {
               bundleActivator = new ModuleActivatorBridge();
               bundleActivator.start(getBundleContextInternal());
            }
            else
            {
               Object result = loadClass(bundleActivatorClassName).newInstance();
               if (result instanceof BundleActivator == false)
                  throw new BundleException(bundleActivatorClassName + " is not an implementation of " + BundleActivator.class.getName());

               bundleActivator = (BundleActivator)result;
               bundleActivator.start(getBundleContext());
            }
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
         stopInternal(options);

         // This bundle's state is set to RESOLVED
         // A bundle event of type BundleEvent.STOPPED is fired
         destroyBundleContext();
         changeState(RESOLVED);

         if (t instanceof BundleException)
            throw (BundleException)t;

         throw new BundleException("Cannot start bundle: " + this, t);
      }
   }

   @Override
   public void stop(int options) throws BundleException
   {
      if ((options & Bundle.STOP_TRANSIENT) == 0)
         setPersistentlyStarted(false);

      super.stop(options);
   }

   @Override
   void stopInternal(int options) throws BundleException
   {
      // If this bundle's state is UNINSTALLED then an IllegalStateException is thrown. 
      if (getState() == Bundle.UNINSTALLED)
         throw new IllegalStateException("Bundle already uninstalled: " + this);

      // [TODO] If this bundle is in the process of being activated or deactivated then this method must wait for activation or deactivation 
      // to complete before continuing. If this does not occur in a reasonable time, a BundleException is thrown to indicate this bundle 
      // was unable to be stopped.

      // [TODO] If the STOP_TRANSIENT option is not set then then set this bundle's persistent autostart setting to to Stopped. 
      // When the Framework is restarted and this bundle's autostart setting is Stopped, this bundle must not be automatically started. 

      // If this bundle's state is not STARTING or ACTIVE then this method returns immediately
      if (getState() != Bundle.STARTING && getState() != Bundle.ACTIVE)
         return;

      // This bundle's state is set to STOPPING
      // A bundle event of type BundleEvent.STOPPING is fired
      int priorState = getState();
      changeState(STOPPING);

      // If this bundle's state was ACTIVE prior to setting the state to STOPPING, 
      // the BundleActivator.stop(org.osgi.framework.BundleContext) method of this bundle's BundleActivator, if one is specified, is called. 
      // If that method throws an exception, this method must continue to stop this bundle and a BundleException must be thrown after completion 
      // of the remaining steps.
      Throwable rethrow = null;
      if (priorState == Bundle.ACTIVE)
      {
         if (bundleActivator != null && getBundleContext() != null)
         {
            try
            {
               if (bundleActivator instanceof ModuleActivatorBridge)
               {
                  bundleActivator.stop(getBundleContextInternal());
               }
               else
               {
                  bundleActivator.stop(getBundleContext());
               }
            }
            catch (Throwable t)
            {
               rethrow = t;
            }
         }
      }

      // Any services registered by this bundle must be unregistered
      for (ServiceState serviceState : getRegisteredServicesInternal())
         serviceState.unregister();

      // [TODO] Any listeners registered by this bundle must be removed

      // If this bundle's state is UNINSTALLED, because this bundle was uninstalled while the 
      // BundleActivator.stop method was running, a BundleException must be thrown
      if (getState() == Bundle.UNINSTALLED)
         throw new BundleException("Bundle uninstalled during activator stop: " + this);

      // This bundle's state is set to RESOLVED
      // A bundle event of type BundleEvent.STOPPED is fired
      destroyBundleContext();
      changeState(RESOLVED);

      if (rethrow != null)
         throw new BundleException("Error during stop of bundle: " + this, rethrow);
   }

   @Override
   void updateInternal(InputStream input) throws BundleException
   {
      // Not checking that the bundle is uninstalled as that already happens in super.update()
      boolean restart = false;
      int state = getState();
      if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING)
      {
         // If this bundle's state is ACTIVE, STARTING  or STOPPING, this bundle is stopped as described in the Bundle.stop method. 
         // If Bundle.stop throws an exception, the exception is rethrown terminating the update.
         stopInternal(Bundle.STOP_TRANSIENT);
         if (state != Bundle.STOPPING)
            restart = true;
      }
      unresolve();

      try
      {
         createNewBundleRevision(input);
      }
      catch (Exception e)
      {
         // If the Framework is unable to install the updated version of this bundle, the original 
         // version of this bundle must be restored and a BundleException must be thrown after 
         // completion of the remaining steps.
         BundleException be = new BundleException("Problem updating bundle");
         be.initCause(e);
         
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

   public void createNewRevision() throws BundleException
   {
      try
      {
         createNewBundleRevision(null);
      }
      catch (Exception e)
      {
         throw new BundleException("Problem creating new revision of " + this, e);
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
   private void createNewBundleRevision(InputStream input) throws Exception
   {
      BundleManager bm = getBundleManager();
      URL locationURL;

      // If the specified InputStream is null, the Framework must create the InputStream from 
      // which to read the updated bundle by interpreting, in an implementation dependent manner, 
      // this bundle's Bundle-UpdateLocation Manifest header, if present, or this bundle's 
      // original location.
      if (input == null)
      {
         String ul = getOSGiMetaData().getHeader(Constants.BUNDLE_UPDATELOCATION);
         if (ul != null)
            input = new URL(ul).openStream();
      }

      if (input != null)
         locationURL = bm.storeBundleStream(input);
      else
         locationURL = currentRevision.getRootFile().getStreamURL();

      BundleDeploymentPlugin plugin = bm.getPlugin(BundleDeploymentPlugin.class);
      VirtualFile newRootFile = AbstractVFS.getRoot(locationURL);
      Deployment dep = plugin.createDeployment(newRootFile, getLocation());
      OSGiMetaData md = plugin.createOSGiMetaData(dep);
      dep.addAttachment(OSGiMetaData.class, md);

      if (md.getBundleSymbolicName().equals(getSymbolicName()) == false)
         log.infof("Ignoring update of symbolic name: %s", md.getBundleSymbolicName());

      BundleRevision newRev = new BundleRevision(this, dep, revisionCounter.getAndIncrement());
      oldRevisions.add(currentRevision);
      currentRevision = newRev;
      getResolverPlugin().addRevision(currentRevision);
   }

   @Override
   void uninstallInternal() throws BundleException
   {
      BundleManager bundleManager = getBundleManager();
      if (bundleManager.getBundleById(getBundleId()) == null)
         throw new BundleException("Not installed: " + this);

      // If this bundle's state is ACTIVE, STARTING or STOPPING, this bundle is stopped 
      // as described in the Bundle.stop method.
      int state = getState();
      if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING)
      {
         try
         {
            stopInternal(0);
         }
         catch (Exception ex)
         {
            // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is
            // fired containing the exception
            bundleManager.fireError(this, "Error stopping bundle: " + this, ex);
         }
      }

      bundleManager.uninstallBundleState(this);
   }

   public void unresolve() throws BundleException
   {
      assertNotUninstalled();

      changeState(Bundle.INSTALLED);
   }

   // Methods delegated to the current revision.
   @Override
   public URL getResource(String name)
   {
      return currentRevision.getResource(name);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Class loadClass(String name) throws ClassNotFoundException
   {
      return currentRevision.loadClass(name);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getResources(String name) throws IOException
   {
      return currentRevision.getResources(name);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration getEntryPaths(String path)
   {
      return currentRevision.getEntryPaths(path);
   }

   @Override
   public URL getEntry(String path)
   {
      return currentRevision.getEntry(path);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Enumeration findEntries(String path, String filePattern, boolean recurse)
   {
      return currentRevision.findEntries(path, filePattern, recurse);
   }

   @Override
   URL getLocalizationEntry(String entryPath)
   {
      return currentRevision.getLocalizationEntry();
   }

   @Override
   public Version getVersion()
   {
      return currentRevision.getVersion();
   }

   @Override
   OSGiMetaData getOSGiMetaData()
   {
      return currentRevision.getOSGiMetaData();
   }

   @Override
   public XModule getResolverModule()
   {
      return currentRevision.getResolverModule();
   }

   @Override
   public List<XModule> getAllResolverModules()
   {
      List<XModule> allModules = new ArrayList<XModule>(oldRevisions.size() + 1);
      for (Revision rev : oldRevisions)
         allModules.add(rev.getResolverModule());

      allModules.add(currentRevision.getResolverModule());
      return allModules;
   }

   @Override
   public VirtualFile getRootFile()
   {
      return currentRevision.getRootFile();
   }
}
