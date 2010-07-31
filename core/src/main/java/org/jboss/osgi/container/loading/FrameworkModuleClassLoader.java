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
package org.jboss.osgi.container.loading;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.AssertionSetting;
import org.jboss.modules.Module;
import org.jboss.modules.Module.Flag;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.plugin.SystemPackagesPlugin;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;

/**
 * A {@link ModuleClassLoader} that only loads framework defined classes/resources.
 * 
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public class FrameworkModuleClassLoader extends ModuleClassLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(FrameworkModuleClassLoader.class);

   private Set<String> exportedPaths;
   private BundleManager bundleManager;
   private SystemPackagesPlugin systemPackages;
   private XModule resModule;

   public FrameworkModuleClassLoader(BundleManager bundleManager, XModule resModule, Module module)
   {
      super(module, Collections.<Flag> emptySet(), AssertionSetting.INHERIT, null);
      this.bundleManager = bundleManager;
      this.systemPackages = bundleManager.getPlugin(SystemPackagesPlugin.class);
      this.resModule = resModule;
   }

   @Override
   protected Set<String> getFilteredLocalPaths()
   {
      if (exportedPaths == null)
      {
         exportedPaths = new HashSet<String>();

         // Add bootdelegation paths
         SystemPackagesPlugin plugin = bundleManager.getPlugin(SystemPackagesPlugin.class);
         List<String> bootDelegationPackages = plugin.getBootDelegationPackages();
         for (String packageName : bootDelegationPackages)
         {
            if (packageName.endsWith(".*"))
               packageName = packageName.substring(0, packageName.length() - 2);

            exportedPaths.add(packageName.replace('.', File.separatorChar));
         }

         // Add package capabilities exported by the framework
         for (XPackageCapability cap : resModule.getPackageCapabilities())
            exportedPaths.add(cap.getName().replace('.', File.separatorChar));
      }
      return exportedPaths;
   }

   @Override
   protected Class<?> findClass(String className, boolean exportsOnly) throws ClassNotFoundException
   {
      // Check if we have already loaded it..
      Class<?> result = findLoadedClass(className);
      if (result != null)
         return result;

      return findClassInternal(className);
   }

   @Override
   protected Class<?> loadClassLocal(String className, boolean exportOnly) throws ClassNotFoundException
   {
      return findClassInternal(className);
   }

   private Class<?> findClassInternal(String className) throws ClassNotFoundException
   {
      boolean traceEnabled = log.isTraceEnabled();
      if (traceEnabled)
         log.trace("Attempt to find framework class [" + className + "] ...");

      // Delegate to framework loader for boot delegation 
      String packageName = className.substring(0, className.lastIndexOf('.'));
      if (systemPackages.isBootDelegationPackage(packageName))
      {
         if (traceEnabled)
            log.trace("Load class through boot delegation [" + className + "] ...");
         
         return getSystemClassLoader().loadClass(className);
      }

      String path = getPathFromClassName(className);
      if (getFilteredLocalPaths().contains(path))
      {
         Class<?> result = null;
         try
         {
            result = findSystemClass(className);
            if (result != null)
            {
               if (traceEnabled)
                  log.trace("Found framework class [" + className + "]");
               return result;
            }
         }
         catch (ClassNotFoundException ex)
         {
            if (traceEnabled)
               log.trace("Cannot find framework class [" + className + "]");
         }
      }
      else
      {
         if (traceEnabled)
            log.trace("Cannot find filtered class [" + className + "]");
      }

      throw new ClassNotFoundException(className);
   }

   @Override
   protected URL getLocalResource(String resourcePath, boolean exportOnly)
   {
      return getSystemResource(resourcePath);
   }
}
