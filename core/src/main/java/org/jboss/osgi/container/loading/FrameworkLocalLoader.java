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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Resource;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.plugin.SystemPackagesPlugin;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.jboss.osgi.spi.NotImplementedException;

/**
 * A {@link LocalLoader} that only loads framework defined classes/resources.
 * 
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public class FrameworkLocalLoader implements LocalLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(FrameworkLocalLoader.class);

   private ClassLoader systemClassLoader;
   private Set<String> exportedPaths = new HashSet<String>();
   private SystemPackagesPlugin systemPackages;

   public FrameworkLocalLoader(BundleManager bundleManager, XModule resModule)
   {
      this.systemPackages = bundleManager.getPlugin(SystemPackagesPlugin.class);
      
      this.systemClassLoader = AccessController.doPrivileged(new PrivilegedAction<ClassLoader>()
      {
         public ClassLoader run()
         {
            return ClassLoader.getSystemClassLoader();
         }
      });

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

   public Set<String> getExportedPaths()
   {
      return Collections.unmodifiableSet(exportedPaths);
   }

   @Override
   public Class<?> loadClassLocal(String className, boolean exportOnly)
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

         try
         {
            return systemClassLoader.loadClass(className);
         }
         catch (ClassNotFoundException ex)
         {
            log.error("Cannot load class through boot delegation: " + className);
            return null;
         }
      }

      String path = getPathFromClassName(className);
      if (exportedPaths.contains(path))
      {
         Class<?> result = null;
         try
         {
            result = systemClassLoader.loadClass(className);
            if (traceEnabled)
               log.trace("Found framework class [" + className + "]");
            return result;
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

      return null;
   }

   @Override
   public List<Resource> loadResourceLocal(String name)
   {
      throw new NotImplementedException();
   }

   @Override
   public Resource loadResourceLocal(String root, String name)
   {
      throw new NotImplementedException();
   }

   private String getPathFromClassName(final String className)
   {
      int idx = className.lastIndexOf('.');
      return idx > -1 ? getPathFromPackageName(className.substring(0, idx)) : "";
   }
   
   private String getPathFromPackageName(String packageName)
   {
      return packageName.replace('.', File.separatorChar);
   }
}
