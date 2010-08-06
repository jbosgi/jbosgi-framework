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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.InternalBundle;
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
import org.jboss.osgi.resolver.XWire;
import org.jboss.osgi.spi.NotImplementedException;
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
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      XModule resModule = bundleState.getResolverModule();
      if (resModule.isResolved() == false)
         return null;

      List<ExportedPackage> result = new ArrayList<ExportedPackage>();
      for (XPackageCapability cap : resModule.getPackageCapabilities())
      {
         ExportedPackage exp = new ExportedPackageImpl(cap);
         result.add(exp);
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
   public void refreshPackages(Bundle[] bundles)
   {
      // TODO use a separate thread
      // TODO if bundles == null


      FrameworkEventsPlugin eventsPlugin = getPlugin(FrameworkEventsPlugin.class);
      if (bundles == null)
         bundles = getBundleManager().getSystemContext().getBundles();

      Map<InternalBundle, XModule> refreshMap = new HashMap<InternalBundle, XModule>();
      for (Bundle b : bundles)
      {
         if (b.getBundleId() == 0)
            continue;

         InternalBundle ab = InternalBundle.assertBundleState(b);
         refreshMap.put(ab, ab.getResolverModule());
      }

      Set<InternalBundle> stopBundles = new HashSet<InternalBundle>();
      Set<InternalBundle> refreshBundles = new HashSet<InternalBundle>(refreshMap.keySet());

      // Compute all depending bundles that need to be stopped and unresolved.
      for (AbstractBundle ab : getBundleManager().getBundles())
      {
         if (ab instanceof InternalBundle == false)
            continue;
         InternalBundle ib = (InternalBundle)ab;

         XModule rm = ib.getResolverModule();
         List<XWire> wires = rm.getWires();
         if (wires == null)
            continue;

         for (XWire wire : wires)
         {
            if (refreshMap.containsValue(wire.getExporter()))
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
      for (InternalBundle ib : refreshMap.keySet())
      {
         int state = ib.getState();
         if (state == Bundle.ACTIVE || state == Bundle.STARTING)
         {
            stopBundles.add(ib);
         }
      }

      List<InternalBundle> stopList = new ArrayList<InternalBundle>(stopBundles);
      List<InternalBundle> refreshList = new ArrayList<InternalBundle>(refreshBundles);

      StartLevelPlugin startLevel = getOptionalPlugin(StartLevelPlugin.class);
      BundleStartLevelComparator startLevelComparator = new BundleStartLevelComparator(startLevel);
      Collections.sort(stopList, startLevelComparator);
      Collections.sort(refreshList, startLevelComparator);

      for (ListIterator<InternalBundle> it = stopList.listIterator(stopList.size()); it.hasPrevious();)
      {
         InternalBundle ib = it.previous();
         try
         {
            ib.stop(Bundle.STOP_TRANSIENT);
         }
         catch (BundleException e)
         {
            eventsPlugin.fireFrameworkEvent(ib, FrameworkEvent.ERROR, e);
         }
      }

      for (ListIterator<InternalBundle> it = refreshList.listIterator(refreshList.size()); it.hasPrevious();)
      {
         InternalBundle ib = it.previous();
         try
         {
            if (ib.getState() != Bundle.UNINSTALLED)
               ib.unresolve();
            ib.ensureNewRevision();
         }
         catch (BundleException e)
         {
            eventsPlugin.fireFrameworkEvent(ib, FrameworkEvent.ERROR, e);
         }
      }

      for (InternalBundle ib : refreshList)
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

      for (InternalBundle b : stopList)
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

   @Override
   public boolean resolveBundles(Bundle[] bundles)
   {
      // Get the list of unresolved bundles
      List<AbstractBundle> unresolved = new ArrayList<AbstractBundle>();
      if (bundles == null)
      {
         for (AbstractBundle aux : getBundleManager().getBundles())
         {
            if (aux.getState() == Bundle.INSTALLED)
               unresolved.add(aux);
         }
      }
      else
      {
         for (Bundle aux : bundles)
         {
            if (aux.getState() == Bundle.INSTALLED)
               unresolved.add(AbstractBundle.assertBundleState(aux));
         }
      }
      log.debug("resolve bundles: " + unresolved);

      // Resolve the bundles through the resolver plugin
      ResolverPlugin resolver = getPlugin(ResolverPlugin.class);
      List<AbstractBundle> resolved = resolver.resolve(unresolved);
      boolean allResolved = unresolved.size() == resolved.size();

      return allResolved;
   }

   @Override
   public RequiredBundle[] getRequiredBundles(String symbolicName)
   {
      List<RequiredBundle> result = new ArrayList<RequiredBundle>();
      for (AbstractBundle aux : getBundleManager().getBundles())
      {
         XModule resModule = aux.getResolverModule();
         for (XRequireBundleRequirement req : resModule.getBundleRequirements())
         {
            String name = req.getName();
            if (symbolicName == null || name.equals(symbolicName))
               result.add (new RequiredBundleImpl(req));
         }
      }
      return result.toArray(new RequiredBundle[result.size()]);
   }

   @Override
   public Bundle[] getBundles(String symbolicName, String versionRange)
   {
      throw new NotImplementedException();
   }

   @Override
   public Bundle[] getFragments(Bundle bundle)
   {
      // [TODO] PackageAdmin.getFragments
      return null;
   }

   @Override
   public Bundle[] getHosts(Bundle bundle)
   {
      // [TODO] PackageAdmin.getHosts
      return null;
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
      return plugin.getBundle(identifier).getBundleWrapper();
   }

   @Override
   public int getBundleType(Bundle bundle)
   {
      // TODO support Fragments
      return 0;
   }

   static class ExportedPackageImpl implements ExportedPackage
   {
      private XPackageCapability cap;

      ExportedPackageImpl(XPackageCapability cap)
      {
         this.cap = cap;
      }

      @Override
      public String getName()
      {
         return cap.getName();
      }

      @Override
      public Bundle getExportingBundle()
      {
         Bundle bundle = cap.getModule().getAttachment(Bundle.class);
         return bundle;
      }

      @Override
      public Bundle[] getImportingBundles()
      {
         XModule module = cap.getModule();
         if (module.isResolved() == false)
            return null;
         
         Set<XRequirement> reqset = cap.getWiredRequirements();
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
         return cap.getVersion().toString();
      }

      @Override
      public Version getVersion()
      {
         return cap.getVersion();
      }

      @Override
      public boolean isRemovalPending()
      {
         return false;
      }
   }

   static class RequiredBundleImpl implements RequiredBundle
   {
      private XRequireBundleRequirement bundleRequirement;

      public RequiredBundleImpl(XRequireBundleRequirement bundleRequirement)
      {
         this.bundleRequirement = bundleRequirement;
      }

      @Override
      public String getSymbolicName()
      {
         return bundleRequirement.getName();
      }

      @Override
      public Bundle getBundle()
      {
         throw new NotImplementedException();
      }

      @Override
      public Bundle[] getRequiringBundles()
      {
         throw new NotImplementedException();
      }

      @Override
      public Version getVersion()
      {
         throw new NotImplementedException();
      }

      @Override
      public boolean isRemovalPending()
      {
         return false;
      }
   }

   private static class BundleStartLevelComparator implements Comparator<InternalBundle>
   {
      private final StartLevel startLevel;

      private BundleStartLevelComparator(StartLevel startLevelService)
      {
         this.startLevel = startLevelService;
      }

      @Override
      public int compare(InternalBundle o1, InternalBundle o2)
      {
         if (startLevel == null)
            return 0;

         int sl1 = o1.getStartLevel();
         int sl2 = o2.getStartLevel();
         return sl1 < sl2 ? -1 : (sl1 == sl2 ? 0 : 1);
      }
   }
}