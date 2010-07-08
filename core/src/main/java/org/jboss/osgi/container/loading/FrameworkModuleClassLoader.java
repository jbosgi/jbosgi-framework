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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.AssertionSetting;
import org.jboss.modules.Module;
import org.jboss.modules.Module.Flag;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.FrameworkState;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageCapability;
import org.osgi.framework.Constants;

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
   private XModule resModule;

   public FrameworkModuleClassLoader(BundleManager bundleManager, XModule resModule, Module module)
   {
      super(module, Collections.<Flag> emptySet(), AssertionSetting.INHERIT, null);
      this.bundleManager = bundleManager;
      this.resModule = resModule;
   }

   @Override
   protected Set<String> getExportedPaths()
   {
      if (exportedPaths == null)
      {
         exportedPaths = new HashSet<String>();

         // Add bootdelegation paths
         FrameworkState frameworkState = bundleManager.getFrameworkState();
         String bootDelegationProp = frameworkState.getProperty(Constants.FRAMEWORK_BOOTDELEGATION);
         if (bootDelegationProp != null)
         {
            String[] paths = bootDelegationProp.split(",");
            for (String path : paths)
            {
               if (path.endsWith(".*"))
                  path = path.substring(0, path.length() - 2);

               exportedPaths.add(path.replace('.', '/'));
            }
         }

         // Add package capabilities exported by the framework
         for (XPackageCapability cap : resModule.getPackageCapabilities())
            exportedPaths.add(cap.getName().replace('.', '/'));
      }
      return exportedPaths;
   }

   @Override
   protected Class<?> findClass(String className, boolean exportsOnly) throws ClassNotFoundException
   {
      // Check if we have already loaded it..
      Class<?> loadedClass = findLoadedClass(className);
      if (loadedClass != null)
      {
         return loadedClass;
      }
      
      boolean traceEnabled = log.isTraceEnabled();

      String path = getPathFromClassName(className);
      if (getExportedPaths().contains(path))
      {
         if (traceEnabled)
            log.trace("Attempt to find framework class [" + className + "] ...");

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
            log.trace("Cannot load filtered class [" + className + "]");
      }

      throw new ClassNotFoundException(className);
   }
}
