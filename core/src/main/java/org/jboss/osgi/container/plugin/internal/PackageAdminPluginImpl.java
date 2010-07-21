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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.container.plugin.PackageAdminPlugin;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.resolver.XCapability;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.resolver.XRequireBundleRequirement;
import org.jboss.osgi.resolver.XRequirement;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;

/**
 * A plugin manages the Framework's system packages.
 * 
 * @author thomas.diesler@jboss.com
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
      throw new NotImplementedException();
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
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      if (bundleState.isFragment() == true)
         return null;

      // [TODO] PackageAdmin.getFragments
      return null;
   }

   @Override
   public Bundle[] getHosts(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      if (bundleState.isFragment() == false)
         return null;

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
      long moduleId = plugin.getBundle(identifier).getBundleId();
      return getBundleManager().getSystemContext().getBundle(moduleId);
   }

   @Override
   public int getBundleType(Bundle bundle)
   {
      AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
      return bundleState.isFragment() ? BUNDLE_TYPE_FRAGMENT : 0;
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
}