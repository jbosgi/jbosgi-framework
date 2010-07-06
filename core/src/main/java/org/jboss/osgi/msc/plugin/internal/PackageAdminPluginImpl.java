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
package org.jboss.osgi.msc.plugin.internal;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.osgi.msc.bundle.AbstractBundle;
import org.jboss.osgi.msc.bundle.BundleManager;
import org.jboss.osgi.msc.plugin.AbstractServicePlugin;
import org.jboss.osgi.msc.plugin.PackageAdminPlugin;
import org.jboss.osgi.msc.plugin.ResolverPlugin;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.Bundle;
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
public class PackageAdminPluginImpl extends AbstractServicePlugin implements PackageAdminPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(PackageAdminPluginImpl.class);

   private ServiceRegistration registration;

   public PackageAdminPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public void startService()
   {
      registration = getSystemContext().registerService(PackageAdmin.class.getName(), this, null);
   }

   @Override
   public void stopService()
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
      XModule module = bundleState.getResolverModule();
      if (module.isResolved() == false)
         return null;

      List<ExportedPackage> result = new ArrayList<ExportedPackage>();
      for (XPackageCapability cap : module.getPackageCapabilities())
      {
         ExportedPackage exp = new ExportedPackageImpl(cap);
         result.add(exp);
      }
      return result.toArray(new ExportedPackage[result.size()]);
   }

   @Override
   public ExportedPackage[] getExportedPackages(String name)
   {
      throw new NotImplementedException();
   }

   @Override
   public ExportedPackage getExportedPackage(String name)
   {
      throw new NotImplementedException();
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
      throw new NotImplementedException();
   }

   @Override
   public Bundle[] getBundles(String symbolicName, String versionRange)
   {
      throw new NotImplementedException();
   }

   @Override
   public Bundle[] getFragments(Bundle bundle)
   {
      throw new NotImplementedException();
   }

   @Override
   public Bundle[] getHosts(Bundle bundle)
   {
      throw new NotImplementedException();
   }

   @Override
   @SuppressWarnings("rawtypes")
   public Bundle getBundle(Class clazz)
   {
      throw new NotImplementedException();
   }

   @Override
   public int getBundleType(Bundle bundle)
   {
      throw new NotImplementedException();
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
         throw new NotImplementedException();
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
}