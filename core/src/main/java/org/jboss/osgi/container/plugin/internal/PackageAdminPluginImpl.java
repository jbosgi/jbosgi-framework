/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.osgi.container.plugin.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.DeploymentBundle;
import org.jboss.osgi.container.bundle.FragmentBundle;
import org.jboss.osgi.container.bundle.FragmentRevision;
import org.jboss.osgi.container.bundle.HostBundle;
import org.jboss.osgi.container.bundle.HostRevision;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.FrameworkEventsPlugin;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.container.plugin.PackageAdminPlugin;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.container.plugin.StartLevelPlugin;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XRequireBundleRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.resolver.XVersionRange;
import org.jboss.osgi.resolver.XWire;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;
import org.osgi.service.startlevel.StartLevel;

/**
 * A plugin manages the Framework's system packages.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2010
 */
public class PackageAdminPluginImpl extends AbstractPlugin implements PackageAdminPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(PackageAdminPluginImpl.class);

   private final Executor executor = Executors.newSingleThreadExecutor();
   private ResolverPlugin resolverPlugin;
   private ServiceRegistration registration;

   public PackageAdminPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public void startPlugin()
   {
      BundleContext sysContext = getBundleManager().getSystemContext();
      registration = sysContext.registerService(PackageAdmin.class.getName(), this, null);
      resolverPlugin = getPlugin(ResolverPlugin.class);
   }

   @Override
   public void stopPlugin()
   {
      if (registration != null)
      {
         registration.unregister();
         registration = null;
      }
   }

   @Override
   public ExportedPackage[] getExportedPackages(Bundle bundle)
   {
      if (bundle == null)
         return getAllExportedPackages();

      List<ExportedPackage> result = new ArrayList<ExportedPackage>();
      AbstractBundle ab = AbstractBundle.assertBundleState(bundle);
      for (XModule resModule : ab.getAllResolverModules())
      {
         if (resModule.isResolved() == false)
            continue;

         for (XPackageCapability cap : resModule.getPackageCapabilities())
         {
            ExportedPackage exp = new ExportedPackageImpl(cap);
            result.add(exp);
         }
      }

      if (result.size() == 0)
         return null; // a bit ugly, but the spec mandates this

      return result.toArray(new ExportedPackage[result.size()]);
   }

   private ExportedPackage[] getAllExportedPackages()
   {
      List<ExportedPackage> result = new ArrayList<ExportedPackage>();
      for (AbstractBundle ab : getBundleManager().getBundles())
      {
         ExportedPackage[] pkgs = getExportedPackages(ab);
         if (pkgs != null)
            result.addAll(Arrays.asList(pkgs));
      }

      return result.toArray(new ExportedPackage[result.size()]);
   }

   @Override
   public ExportedPackage[] getExportedPackages(String name)
   {
      if (name == null)
         throw new IllegalArgumentException("Null name");
      
      Set<ExportedPackage> result = new HashSet<ExportedPackage>();
      ResolverPlugin plugin = getBundleManager().getPlugin(ResolverPlugin.class);
      for (XModule mod : plugin.getResolver().getModules())
      {
         for (XCapability cap : mod.getCapabilities())
         {
            if (cap instanceof XPackageCapability)
            {
               if (name.equals(cap.getName()))
                  result.add(new ExportedPackageImpl((XPackageCapability)cap));
            }
         }
      }
      return result.toArray(new ExportedPackage[result.size()]);
   }

   @Override
   public ExportedPackage getExportedPackage(String name)
   {
      ExportedPackage bestMatch = null;
      for (ExportedPackage aux : getExportedPackages(name))
      {
         if (bestMatch == null)
            bestMatch = aux;
         if (aux.getVersion().compareTo(bestMatch.getVersion()) > 0)
            bestMatch = aux;
      }
      return bestMatch;
   }

   @Override
   public void refreshPackages(final Bundle[] bundlesToRefresh)
   {
      Runnable run = new Runnable()
      {
         @Override
         public void run()
         {
            Bundle[] bundles = bundlesToRefresh;
            FrameworkEventsPlugin eventsPlugin = getPlugin(FrameworkEventsPlugin.class);
            if (bundles == null)
            {
               List<Bundle> allBundles = new ArrayList<Bundle>();

               for (AbstractBundle ab : getBundleManager().getBundles())
                  allBundles.add(ab.getBundleWrapper());

               for (AbstractBundle ab : getBundleManager().getUninstalledBundles())
                  allBundles.add(ab.getBundleWrapper());

               bundles = allBundles.toArray(new Bundle[allBundles.size()]);
            }

            Map<XModule, HostBundle> refreshMap = new HashMap<XModule, HostBundle>();
            for (Bundle b : bundles)
            {
               if (b.getBundleId() == 0)
                  continue;

               HostBundle ib = HostBundle.assertBundleState(b);
               for (XModule resModule : ib.getAllResolverModules())
                  refreshMap.put(resModule, ib);
            }

            Set<HostBundle> stopBundles = new HashSet<HostBundle>();
            Set<HostBundle> refreshBundles = new HashSet<HostBundle>();
            Set<HostBundle> uninstallBundles = new HashSet<HostBundle>();
            
            for (HostBundle ib : refreshMap.values())
            {
               if (ib.getState() == Bundle.UNINSTALLED)
                  uninstallBundles.add(ib);
               else
                  refreshBundles.add(ib);
            }

            // Compute all depending bundles that need to be stopped and unresolved.
            for (AbstractBundle ab : getBundleManager().getBundles())
            {
               if (ab instanceof HostBundle == false)
                  continue;
               HostBundle ib = (HostBundle)ab;

               XModule rm = ib.getResolverModule();
               List<XWire> wires = rm.getWires();
               if (wires == null)
                  continue;

               for (XWire wire : wires)
               {
                  if (refreshMap.containsKey(wire.getExporter()))
                  {
                     // Bundles can be either ACTIVE or RESOLVED
                     int state = ib.getState();
                     if (state == Bundle.ACTIVE || state == Bundle.STARTING)
                     {
                        stopBundles.add(ib);
                     }
                     refreshBundles.add(ib);
                     break;
                  }
               }
            }

            // Add relevant bundles to be refreshed also to the stop list. 
            for (HostBundle ib : new HashSet<HostBundle>(refreshMap.values()))
            {
               int state = ib.getState();
               if (state == Bundle.ACTIVE || state == Bundle.STARTING)
               {
                  stopBundles.add(ib);
               }
            }

            List<HostBundle> stopList = new ArrayList<HostBundle>(stopBundles);
            List<HostBundle> refreshList = new ArrayList<HostBundle>(refreshBundles);

            StartLevelPlugin startLevel = getOptionalPlugin(StartLevelPlugin.class);
            BundleStartLevelComparator startLevelComparator = new BundleStartLevelComparator(startLevel);
            Collections.sort(stopList, startLevelComparator);
            Collections.sort(refreshList, startLevelComparator);

            for (ListIterator<HostBundle> it = stopList.listIterator(stopList.size()); it.hasPrevious();)
            {
               DeploymentBundle ib = it.previous();
               try
               {
                  ib.stop(Bundle.STOP_TRANSIENT);
               }
               catch (BundleException e)
               {
                  eventsPlugin.fireFrameworkEvent(ib, FrameworkEvent.ERROR, e);
               }
            }

            for (ListIterator<HostBundle> it = refreshList.listIterator(refreshList.size()); it.hasPrevious();)
            {
               HostBundle ib = it.previous();
               try
               {
                  ib.unresolve();
                  ib.createNewRevision();
               }
               catch (BundleException e)
               {
                  eventsPlugin.fireFrameworkEvent(ib, FrameworkEvent.ERROR, e);
               }
            }

            for (HostBundle ib : uninstallBundles)
               ib.remove();

            for (HostBundle ib : refreshList)
            {
               try
               {
                  ib.refresh();
               }
               catch (BundleException e)
               {
                  eventsPlugin.fireFrameworkEvent(ib, FrameworkEvent.ERROR, e);
               }
            }

            for (DeploymentBundle b : stopList)
            {
               try
               {
                  b.start(Bundle.START_TRANSIENT);
               }
               catch (BundleException e)
               {
                  eventsPlugin.fireFrameworkEvent(b, FrameworkEvent.ERROR, e);
               }
            }

            eventsPlugin.fireFrameworkEvent(getBundleManager().getSystemBundle(), FrameworkEvent.PACKAGES_REFRESHED, null);
         }
      };
      executor.execute(run);
   }

   @Override
   public boolean resolveBundles(Bundle[] bundles)
   {
      // Get the list of unresolved bundles
      Set<XModule> unresolved = new HashSet<XModule>();
      if (bundles == null)
      {
         for (AbstractBundle aux : getBundleManager().getBundles())
         {
            if (aux.getState() == Bundle.INSTALLED)
               unresolved.add(aux.getResolverModule());
         }
      }
      else
      {
         for (Bundle aux : bundles)
         {
            AbstractBundle bundleState = AbstractBundle.assertBundleState(aux);
            if (bundleState.getState() == Bundle.INSTALLED)
               unresolved.add(bundleState.getResolverModule());
         }
      }
      
      log.debug("Resolve bundles: " + unresolved);
      return resolverPlugin.resolveAll(unresolved);
   }

   @Override
   public RequiredBundle[] getRequiredBundles(String symbolicName)
   {
      Map<AbstractBundle, Collection<AbstractBundle>> matchingBundles =
            new HashMap<AbstractBundle, Collection<AbstractBundle>>();

      // Make a defensive copy to ensure thread safety as we are running through the list twice
      List<AbstractBundle> bundles = new ArrayList<AbstractBundle>(getBundleManager().getBundles());
      for (AbstractBundle aux : bundles)
      {
         if (aux.getSymbolicName().equals(symbolicName))
            matchingBundles.put(aux, new ArrayList<AbstractBundle>());
      }

      if (matchingBundles.size() == 0)
         return null;

      for (AbstractBundle aux : bundles)
      {
         XModule resModule = aux.getResolverModule();
         for (XRequireBundleRequirement req : resModule.getBundleRequirements())
         {
            if (req.getName().equals(symbolicName))
            {
               for (XWire wire : req.getModule().getWires())
               {
                  if (wire.getRequirement().equals(req))
                  {
                     XCapability wiredCap = wire.getCapability();
                     XModule module = wiredCap.getModule();
                     Bundle bundle = module.getAttachment(Bundle.class);
                     Collection<AbstractBundle> requiring = matchingBundles.get(bundle);
                     if (requiring != null)
                        requiring.add(aux);
                  }
               }
            }
         }
      }

      List<RequiredBundle> result = new ArrayList<RequiredBundle>(matchingBundles.size());
      for (Map.Entry<AbstractBundle, Collection<AbstractBundle>> entry : matchingBundles.entrySet())
         result.add(new RequiredBundleImpl(entry.getKey(), entry.getValue()));

      return result.toArray(new RequiredBundle[matchingBundles.size()]);
   }

   @Override
   public Bundle[] getBundles(String symbolicName, String versionRange)
   {
      Set<Bundle> bundles = new TreeSet<Bundle>(new Comparator<Bundle>()
      {
         // Makes sure that the bundles are sorted correctly in the returned array
         // Matching bundles with the highest version should come first.
         @Override
         public int compare(Bundle b1, Bundle b2)
         {
            // Reverse the version comparison order
            return b2.getVersion().compareTo(b1.getVersion());
         }
      });

      XVersionRange range = null;
      if (versionRange != null)
         range = XVersionRange.parse(versionRange);

      for (AbstractBundle ab : getBundleManager().getBundles())
      {
         Bundle b = AbstractBundle.assertBundleState(ab).getBundleWrapper();
         if (b.getSymbolicName().equals(symbolicName))
         {
            if (range == null)
               bundles.add(b);
            else
               if (range.isInRange(b.getVersion()))
                  bundles.add(b);
         }
      }

      if (bundles.size() == 0)
         return null;
      return bundles.toArray(new Bundle[bundles.size()]);
   }

   @Override
   public Bundle[] getFragments(Bundle bundle)
   {
      // If the specified bundle is a fragment then null is returned.
      // If the specified bundle is not resolved then null is returned      
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      if (bundle.getBundleId() == 0 || bundleState.isFragment() || !bundleState.isResolved())
         return null;
      
      HostBundle hostBundle = HostBundle.assertBundleState(bundleState);
      HostRevision curRevision = hostBundle.getCurrentRevision();
      
      List<Bundle> result = new ArrayList<Bundle>();
      for (FragmentRevision aux : curRevision.getAttachedFragments())
         result.add(aux.getBundleState().getBundleWrapper());
      
      if (result.isEmpty())
         return null;
      
      return result.toArray(new Bundle[result.size()]);
   }

   @Override
   public Bundle[] getHosts(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      if (bundleState.isFragment() == false)
         return null;
      
      FragmentBundle fragBundle = FragmentBundle.assertBundleState(bundleState);
      FragmentRevision curRevision = fragBundle.getCurrentRevision();
      
      List<Bundle> result = new ArrayList<Bundle>();
      for (HostRevision aux : curRevision.getAttachedHosts())
         result.add(aux.getBundleState().getBundleWrapper());
      
      if (result.isEmpty())
         return null;
      
      return result.toArray(new Bundle[result.size()]);
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Bundle getBundle(Class clazz)
   {
      if (clazz == null)
         throw new IllegalArgumentException("Null clazz");

      ClassLoader loader = clazz.getClassLoader();
      if (loader instanceof ModuleClassLoader == false)
      {
         log.error("Cannot obtain bundle for: " + loader);
         return null;
      }

      ModuleClassLoader moduleCL = (ModuleClassLoader)loader;
      Module module = moduleCL.getModule();
      ModuleIdentifier identifier = module.getIdentifier();
      ModuleManagerPlugin plugin = getPlugin(ModuleManagerPlugin.class);
      return plugin.getBundleState(identifier).getBundleWrapper();
   }

   @Override
   public int getBundleType(Bundle bundle)
   {
      // TODO support Fragments
      return 0;
   }

   static class ExportedPackageImpl implements ExportedPackage
   {
      private final XPackageCapability capability;

      ExportedPackageImpl(XPackageCapability cap)
      {
         capability = cap;
      }

      @Override
      public String getName()
      {
         return capability.getName();
      }

      @Override
      public Bundle getExportingBundle()
      {
         Bundle bundle = capability.getModule().getAttachment(Bundle.class);
         return AbstractBundle.assertBundleState(bundle).getBundleWrapper();
      }

      @Override
      public Bundle[] getImportingBundles()
      {
         XModule module = capability.getModule();
         if (module.isResolved() == false)
            return null;
         
         Set<XRequirement> reqset = capability.getWiredRequirements();
         if (reqset == null || reqset.isEmpty())
            return new Bundle[0];
         
         Set<Bundle> bundles = new HashSet<Bundle>();
         for (XRequirement req : reqset)
         {
            XModule reqmod = req.getModule();
            Bundle bundle = reqmod.getAttachment(Bundle.class);
            bundles.add(AbstractBundle.assertBundleState(bundle).getBundleWrapper());
         }
         return bundles.toArray(new Bundle[bundles.size()]);
      }

      @Override
      public String getSpecificationVersion()
      {
         return capability.getVersion().toString();
      }

      @Override
      public Version getVersion()
      {
         return capability.getVersion();
      }

      @Override
      public boolean isRemovalPending()
      {
         // TODO
         return false;
      }
   }

   static class RequiredBundleImpl implements RequiredBundle
   {
      private final Bundle requiredBundle;
      private final Bundle[] requiringBundles;

      public RequiredBundleImpl(AbstractBundle requiredBundle, Collection<AbstractBundle> requiringBundles)
      {
         this.requiredBundle = AbstractBundle.assertBundleState(requiredBundle).getBundleWrapper();

         List<Bundle> bundles = new ArrayList<Bundle>(requiringBundles.size());
         for (AbstractBundle ab : requiringBundles)
         {
            bundles.add(AbstractBundle.assertBundleState(ab).getBundleWrapper());
         }
         this.requiringBundles = bundles.toArray(new Bundle[bundles.size()]);
      }

      @Override
      public String getSymbolicName()
      {
         return requiredBundle.getSymbolicName();
      }

      @Override
      public Bundle getBundle()
      {
         return requiredBundle;
      }

      @Override
      public Bundle[] getRequiringBundles()
      {
         return requiringBundles;
      }

      @Override
      public Version getVersion()
      {
         return requiredBundle.getVersion();
      }

      @Override
      public boolean isRemovalPending()
      {
         return false;
      }
   }

   private static class BundleStartLevelComparator implements Comparator<HostBundle>
   {
      private final StartLevel startLevel;

      private BundleStartLevelComparator(StartLevel startLevelService)
      {
         this.startLevel = startLevelService;
      }

      @Override
      public int compare(HostBundle o1, HostBundle o2)
      {
         if (startLevel == null)
            return 0;

         int sl1 = o1.getStartLevel();
         int sl2 = o2.getStartLevel();
         return sl1 < sl2 ? -1 : (sl1 == sl2 ? 0 : 1);
      }
   }
}