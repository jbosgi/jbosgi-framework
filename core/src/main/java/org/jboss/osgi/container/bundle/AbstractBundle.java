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
import java.security.Permission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.container.plugin.LifecycleInterceptorPlugin;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.container.plugin.ServiceManagerPlugin;
import org.jboss.osgi.metadata.CaseInsensitiveDictionary;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * This is the internal base class for all bundles and fragments, including
 * the System Bundle.<p/> 
 * 
 * The logic related to loading of classes and resources is delegated to the current {@link HostRevision}. 
 * As bundles can be updated there can be multiple bundle revisions.<p/>
 * 
 * The {@link AbstractBundle} can contain multiple revisions: the current revision and any number of old revisions.
 * This relates to updating of bundles. When a bundle is updated a new revision is created and assigned to the current revision. 
 * However, the previous revision is kept available until {@link PackageAdmin#refreshPackages(Bundle[])} is called.<p/>
 * 
 * Common Bundle functionality is implemented in this base class, such as service
 * reference counting and state management. 
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public abstract class AbstractBundle implements Bundle
{
   private static final Logger log = Logger.getLogger(AbstractBundle.class);

   private final BundleManager bundleManager;
   private final String symbolicName;
   private final long bundleId;
   private final AtomicInteger bundleState = new AtomicInteger(UNINSTALLED);
   private final List<AbstractRevision> revisions = new CopyOnWriteArrayList<AbstractRevision>();

   private AbstractBundleContext bundleContext;
   private BundleWrapper bundleWrapper;
   private long lastModified = System.currentTimeMillis();
   private List<ServiceState> registeredServices;
   private Map<ServiceState, AtomicInteger> usedServices;

   // Cache commonly used plugins
   private FrameworkEventsPlugin eventsPlugin;
   private LifecycleInterceptorPlugin interceptorPlugin;
   private ResolverPlugin resolverPlugin;
   private ServiceManagerPlugin servicePlugin;

   AbstractBundle(BundleManager bundleManager, String symbolicName)
   {
      if (bundleManager == null)
         throw new IllegalArgumentException("Null bundleManager");
      if (symbolicName == null)
         throw new IllegalArgumentException("Null symbolicName");

      // strip-off the directives
      if (symbolicName.indexOf(';') > 0)
         symbolicName = symbolicName.substring(0, symbolicName.indexOf(';'));

      this.bundleManager = bundleManager;
      this.symbolicName = symbolicName;

      boolean systemBundle = symbolicName.equals(Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
      this.bundleId = (systemBundle ? 0 : bundleManager.getNextBundleId());
   }

   /**
    * Assert that the given bundle is an instance of AbstractBundle
    * @throws IllegalArgumentException if the given bundle is not an instance of AbstractBundle
    */
   public static AbstractBundle assertBundleState(Bundle bundle)
   {
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");

      if (bundle instanceof BundleWrapper)
         bundle = ((BundleWrapper)bundle).getBundleState();

      if (bundle instanceof AbstractBundle == false)
         throw new IllegalArgumentException("Not an AbstractBundle: " + bundle);

      return (AbstractBundle)bundle;
   }

   public abstract boolean isFragment();

   public abstract boolean ensureResolved();

   abstract AbstractBundleContext createContextInternal();

   /**
    * This method returns all the resolver modules of the bundle, including
    * those of revisions that may since have been updated. These obsolete
    * resolver modules disappear when PackageAdmin.refreshPackages() is called.
    * @return A list of all the resolver modules
    */
   public abstract List<XModule> getAllResolverModules();

   @Override
   public BundleContext getBundleContext()
   {
      return bundleContext != null ? bundleContext.getContextWrapper() : null;
   }

   BundleContext getBundleContextInternal()
   {
      return bundleContext;
   }

   void createBundleContext()
   {
      if (bundleContext != null)
         throw new IllegalStateException("BundleContext already available");
      bundleContext = createContextInternal();
   }

   void destroyBundleContext()
   {
      // The BundleContext object is only valid during the execution of its context bundle; 
      // that is, during the period from when the context bundle is in the STARTING, STOPPING, and ACTIVE bundle states. 
      // If the BundleContext  object is used subsequently, an IllegalStateException must be thrown. 
      // The BundleContext object must never be reused after its context bundle is stopped.
      bundleContext.destroy();
      bundleContext = null;
   }

   @Override
   public long getBundleId()
   {
      return bundleId;
   }

   public BundleManager getBundleManager()
   {
      return bundleManager;
   }

   @Override
   public String getSymbolicName()
   {
      return symbolicName;
   }

   public String getCanonicalName()
   {
      return getSymbolicName() + ":" + getVersion();
   }

   public boolean isResolved()
   {
      return getResolverModule().isResolved();
   }

   public boolean isUninstalled()
   {
      return getState() == Bundle.UNINSTALLED;
   }

   public void addToResolver()
   {
      XModule resModule = getResolverModule();
      getResolverPlugin().addModule(resModule);
   }

   public void removeFromResolver()
   {
      for (AbstractRevision abr : getRevisions())
      {
         XModule resModule = abr.getResolverModule();
         getResolverPlugin().removeModule(resModule);
      }
      clearRevisions();
   }

   boolean hasActiveWires()
   {
      XModule resModule = getResolverModule();
      if (resModule.isResolved() == false)
         return false;

      for (XCapability cap : resModule.getCapabilities())
      {
         Set<XRequirement> wiredReqs = cap.getWiredRequirements();
         for (XRequirement req : wiredReqs)
         {
            Bundle bundle = req.getModule().getAttachment(Bundle.class);
            AbstractBundle importer = AbstractBundle.assertBundleState(bundle);
            if (importer.getState() != Bundle.UNINSTALLED)
               return true;
         }
      }
      return false;
   }

   void addRevision(AbstractRevision rev)
   {
      revisions.add(0, rev);
   }

   public AbstractRevision getCurrentRevision()
   {
      return revisions.get(0);
   }

   public List<AbstractRevision> getRevisions()
   {
      return Collections.unmodifiableList(revisions);
   }

   public void clearRevisions()
   {
      AbstractRevision rev = getCurrentRevision();
      revisions.clear();
      revisions.add(rev);
   }

   public void addRegisteredService(ServiceState serviceState)
   {
      log.debug("Add registered service [" + serviceState + "] to: " + this);

      synchronized (this)
      {
         if (registeredServices == null)
            registeredServices = new CopyOnWriteArrayList<ServiceState>();
      }
      registeredServices.add(serviceState);
   }

   public void removeRegisteredService(ServiceState serviceState)
   {
      log.debug("Remove registered service [" + serviceState + "] from: " + this);

      if (registeredServices != null)
         registeredServices.remove(serviceState);
   }

   public List<ServiceState> getRegisteredServicesInternal()
   {
      if (registeredServices == null)
         return Collections.emptyList();

      return Collections.unmodifiableList(registeredServices);
   }

   @Override
   public ServiceReference[] getRegisteredServices()
   {
      assertNotUninstalled();
      List<ServiceState> registeredServices = getRegisteredServicesInternal();
      if (registeredServices.isEmpty())
         return null;

      List<ServiceReference> srefs = new ArrayList<ServiceReference>();
      for (ServiceState serviceState : registeredServices)
         srefs.add(serviceState.getReference());

      return srefs.toArray(new ServiceReference[srefs.size()]);
   }

   public void addServiceInUse(ServiceState serviceState)
   {
      log.debug("Add service in use [" + serviceState + "] to: " + this);

      AtomicInteger count;
      synchronized (this)
      {
         if (usedServices == null)
            usedServices = new ConcurrentHashMap<ServiceState, AtomicInteger>();

         count = usedServices.get(serviceState);
         if (count == null)
            usedServices.put(serviceState, count = new AtomicInteger());
      }
      count.incrementAndGet();
   }

   public int removeServiceInUse(ServiceState serviceState)
   {
      log.debug("Remove service in use [" + serviceState + "] from: " + this);

      AtomicInteger count;
      synchronized (this)
      {
         if (usedServices == null)
            return -1;

         count = usedServices.get(serviceState);
         if (count == null)
            return -1;

         if (count.decrementAndGet() == 0)
            usedServices.remove(serviceState);
      }
      return count.get();
   }

   public Set<ServiceState> getServicesInUseInternal()
   {
      if (usedServices == null)
         return Collections.emptySet();

      return Collections.unmodifiableSet(usedServices.keySet());
   }

   @Override
   public ServiceReference[] getServicesInUse()
   {
      assertNotUninstalled();
      Set<ServiceState> servicesInUse = getServicesInUseInternal();
      if (servicesInUse.isEmpty())
         return null;

      List<ServiceReference> srefs = new ArrayList<ServiceReference>();
      for (ServiceState serviceState : servicesInUse)
         srefs.add(serviceState.getReference());

      return srefs.toArray(new ServiceReference[srefs.size()]);
   }

   public FrameworkEventsPlugin getFrameworkEventsPlugin()
   {
      if (eventsPlugin == null)
         eventsPlugin = bundleManager.getPlugin(FrameworkEventsPlugin.class);
      return eventsPlugin;
   }

   public LifecycleInterceptorPlugin getLifecycleInterceptorPlugin()
   {
      if (interceptorPlugin == null)
         interceptorPlugin = bundleManager.getPlugin(LifecycleInterceptorPlugin.class);
      return interceptorPlugin;
   }

   ResolverPlugin getResolverPlugin()
   {
      if (resolverPlugin == null)
         resolverPlugin = getBundleManager().getPlugin(ResolverPlugin.class);
      return resolverPlugin;
   }

   ServiceManagerPlugin getServiceManagerPlugin()
   {
      if (servicePlugin == null)
         servicePlugin = bundleManager.getPlugin(ServiceManagerPlugin.class);
      return servicePlugin;
   }

   public Bundle getBundleWrapper()
   {
      if (bundleWrapper == null)
         bundleWrapper = createBundleWrapper();
      return bundleWrapper;
   }

   BundleWrapper createBundleWrapper()
   {
      return new BundleWrapper(this);
   }

   public void changeState(int state)
   {
      int previous = getState();

      // Get the corresponding bundle event type
      int bundleEventType;
      switch (state)
      {
         case Bundle.STARTING:
            bundleEventType = BundleEvent.STARTING;
            break;
         case Bundle.ACTIVE:
            bundleEventType = BundleEvent.STARTED;
            break;
         case Bundle.STOPPING:
            bundleEventType = BundleEvent.STOPPING;
            break;
         case Bundle.UNINSTALLED:
            bundleEventType = BundleEvent.UNINSTALLED;
            break;
         case Bundle.INSTALLED:
         {
            if (previous == Bundle.RESOLVED)
               bundleEventType = BundleEvent.UNRESOLVED;
            else
               bundleEventType = BundleEvent.INSTALLED;
            break;
         }
         case Bundle.RESOLVED:
         {
            if (previous == Bundle.STOPPING)
               bundleEventType = BundleEvent.STOPPED;
            else
               bundleEventType = BundleEvent.RESOLVED;
            break;
         }
         default:
            throw new IllegalArgumentException("Unknown bundle state: " + state);
      }

      // Invoke the bundle lifecycle interceptors
      if (getBundleManager().isFrameworkActive() && getBundleId() != 0)
         getLifecycleInterceptorPlugin().handleStateChange(state, getBundleWrapper());

      bundleState.set(state);

      // Fire the bundle event
      if (getBundleManager().isFrameworkActive())
         getFrameworkEventsPlugin().fireBundleEvent(this, bundleEventType);
   }

   @Override
   public int getState()
   {
      return bundleState.get();
   }

   @Override
   public void start(int options) throws BundleException
   {
      assertNotUninstalled();
      startInternal(options);
   }

   @Override
   public void start() throws BundleException
   {
      assertNotUninstalled();
      startInternal(0);
   }

   abstract void startInternal(int options) throws BundleException;

   @Override
   public void stop(int options) throws BundleException
   {
      assertNotUninstalled();
      stopInternal(options);
   }

   @Override
   public void stop() throws BundleException
   {
      assertNotUninstalled();
      stop(0);
   }

   abstract void stopInternal(int options) throws BundleException;

   @Override
   public void update(InputStream input) throws BundleException
   {
      assertNotUninstalled();
      updateInternal(input);

      // A bundle is considered to be modified when it is installed, updated or uninstalled.
      lastModified = System.currentTimeMillis();
   }

   @Override
   public void update() throws BundleException
   {
      assertNotUninstalled();
      updateInternal(null);

      // A bundle is considered to be modified when it is installed, updated or uninstalled.
      lastModified = System.currentTimeMillis();
   }

   abstract void updateInternal(InputStream input) throws BundleException;

   @Override
   public void uninstall() throws BundleException
   {
      assertNotUninstalled();

      if (bundleManager.getBundleById(getBundleId()) == null)
         throw new BundleException("Not installed: " + this);

      uninstallInternal();

      // A bundle is considered to be modified when it is installed, updated or uninstalled.
      lastModified = System.currentTimeMillis();
   }

   abstract void uninstallInternal() throws BundleException;

   @Override
   public boolean hasPermission(Object permission)
   {
      assertNotUninstalled();
      if (permission == null || permission instanceof Permission == false)
         return false;

      SecurityManager sm = System.getSecurityManager();
      if (sm == null)
         return true;

      // [TODO] AbstractBundle.hasPermission
      return true;
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Dictionary getHeaders()
   {
      // If the specified locale is null then the locale returned 
      // by java.util.Locale.getDefault is used.
      return getHeaders(null);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Dictionary getHeaders(String locale)
   {
      // Get the raw (unlocalized) manifest headers
      Dictionary<String, String> rawHeaders = getOSGiMetaData().getHeaders();

      // If the specified locale is the empty string, this method will return the 
      // raw (unlocalized) manifest headers including any leading "%"
      if ("".equals(locale))
         return rawHeaders;

      // If the specified locale is null then the locale 
      // returned by java.util.Locale.getDefault is used
      if (locale == null)
         locale = Locale.getDefault().toString();

      // Get the localization base name
      String baseName = rawHeaders.get(Constants.BUNDLE_LOCALIZATION);
      if (baseName == null)
         baseName = Constants.BUNDLE_LOCALIZATION_DEFAULT_BASENAME;

      // Get the resource bundle URL for the given base and locale
      URL entryURL = getLocalizationEntry(baseName, locale);

      // If the specified locale entry could not be found fall back to the default locale entry
      if (entryURL == null)
      {
         String defaultLocale = Locale.getDefault().toString();
         entryURL = getLocalizationEntry(baseName, defaultLocale);
      }

      // Read the resource bundle
      ResourceBundle resBundle = null;
      if (entryURL != null)
      {
         try
         {
            resBundle = new PropertyResourceBundle(entryURL.openStream());
         }
         catch (IOException ex)
         {
            throw new IllegalStateException("Cannot read resouce bundle: " + entryURL, ex);
         }
      }

      Dictionary<String, String> locHeaders = new Hashtable<String, String>();
      Enumeration<String> e = rawHeaders.keys();
      while (e.hasMoreElements())
      {
         String key = e.nextElement();
         String value = rawHeaders.get(key);
         if (value.startsWith("%"))
            value = value.substring(1);

         if (resBundle != null)
         {
            try
            {
               value = resBundle.getString(value);
            }
            catch (MissingResourceException ex)
            {
               // ignore
            }
         }

         locHeaders.put(key, value);
      }

      return new CaseInsensitiveDictionary(locHeaders);
   }

   private URL getLocalizationEntry(String baseName, String locale)
   {
      // The Framework searches for localization entries by appending suffixes to
      // the localization base name according to a specified locale and finally
      // appending the .properties suffix. If a translation is not found, the locale
      // must be made more generic by first removing the variant, then the country
      // and finally the language until an entry is found that contains a valid translation.

      String entryPath = baseName + "_" + locale + ".properties";

      URL entryURL = getLocalizationEntry(entryPath);
      while (entryURL == null)
      {
         if (entryPath.equals(baseName + ".properties"))
            break;

         int lastIndex = locale.lastIndexOf('_');
         if (lastIndex > 0)
         {
            locale = locale.substring(0, lastIndex);
            entryPath = baseName + "_" + locale + ".properties";
         }
         else
         {
            entryPath = baseName + ".properties";
         }

         // The bundle's class loader is not used to search for localization entries. Only
         // the contents of the bundle and its attached fragments are searched.
         entryURL = getLocalizationEntry(entryPath);
      }
      return entryURL;
   }

   /**
    * The framework must search for localization entries using the follow-
    * ing search rules based on the bundle type:
    *
    * fragment bundle - If the bundle is a resolved fragment, then the search
    *   for localization data must delegate to the attached host bundle with the
    *   highest version. If the fragment is not resolved, then the framework
    *   must search the fragment's JAR for the localization entry.
    *
    * other bundle - The framework must first search in the bundle’s JAR for
    *   the localization entry. If the entry is not found and the bundle has fragments, 
    *   then the attached fragment JARs must be searched for the localization entry.
    */
   URL getLocalizationEntry(String entryPath)
   {
      return getCurrentRevision().getLocalizationEntry(entryPath);
   }

   public ModuleIdentifier getModuleIdentifier()
   {
      return getCurrentRevision().getModuleIdentifier();
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
   public Version getVersion()
   {
      return getCurrentRevision().getVersion();
   }

   public OSGiMetaData getOSGiMetaData()
   {
      return getCurrentRevision().getOSGiMetaData();
   }

   public XModule getResolverModule()
   {
      return getCurrentRevision().getResolverModule();
   }

   @Override
   public long getLastModified()
   {
      // A bundle is considered to be modified when it is installed, updated or uninstalled.
      return lastModified;
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Map getSignerCertificates(int signersType)
   {
      throw new NotImplementedException();
   }

   /**
    * Assert that the bundle context is still valid
    * @throws IllegalStateException when the context is no longer valid
    */
   void assertValidBundleContext()
   {
      if (getBundleContext() == null)
         throw new IllegalStateException("Invalid bundle context: " + this);
   }

   /**
    * Assert that the bundle context is not uninstalled
    * @throws IllegalStateException when the bundle is uninstalled
    */
   void assertNotUninstalled()
   {
      if (getState() == Bundle.UNINSTALLED)
         throw new IllegalStateException("Bundle uninstalled: " + this);
   }

   @Override
   public int hashCode()
   {
      return (int)getBundleId() * 51;
   }

   @Override
   public boolean equals(Object obj)
   {
      if (obj instanceof AbstractBundle == false)
         return false;
      if (obj == this)
         return true;

      AbstractBundle other = (AbstractBundle)obj;
      return getBundleId() == other.getBundleId();
   }

   @Override
   public String toString()
   {
      return getCanonicalName();
   }
}
