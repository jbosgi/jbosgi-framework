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
package org.jboss.osgi.framework.loading;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.modules.LocalLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.Resource;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.AbstractRevision;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.bundle.HostBundle;
import org.jboss.osgi.framework.plugin.ModuleManagerPlugin;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;

/**
 * A fallback loader that takes care of dynamic class loads.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class HostBundleFallbackLoader implements LocalLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(HostBundleFallbackLoader.class);

   private static ThreadLocal<Map<String, AtomicInteger>> dynamicLoadAttempts;
   private final ModuleManagerPlugin moduleManager;
   private final BundleManager bundleManager;
   private final ModuleIdentifier identifier;
   
   public HostBundleFallbackLoader(HostBundle hostBundle, ModuleIdentifier identifier)
   {
      this.identifier = identifier;

      bundleManager = hostBundle.getBundleManager();
      moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
   }

   @Override
   public Class<?> loadClassLocal(String className, boolean resolve)
   {
      // Try to load the class dynamically
      String matchingPattern = findMatchingDynamicImportPattern(className);
      if (matchingPattern != null)
      {
         Class<?> result = loadClassDynamically(className);
         if (result != null)
            return result;
      }

      return null;
   }

   private Class<?> loadClassDynamically(String className)
   {
      Class<?> result;

      if (dynamicLoadAttempts == null)
         dynamicLoadAttempts = new ThreadLocal<Map<String, AtomicInteger>>();

      Map<String, AtomicInteger> mapping = dynamicLoadAttempts.get();
      boolean removeThreadLocalMapping = false;
      try
      {
         if (mapping == null)
         {
            mapping = new HashMap<String, AtomicInteger>();
            dynamicLoadAttempts.set(mapping);
            removeThreadLocalMapping = true;
         }

         AtomicInteger recursiveDepth = mapping.get(className);
         if (recursiveDepth == null)
            mapping.put(className, recursiveDepth = new AtomicInteger());

         if (recursiveDepth.incrementAndGet() == 1)
         {
            result = findInResolvedModules(className);
            if (result != null)
               return result;

            result = findInUnresolvedModules(className);
            if (result != null)
               return result;
         }
      }
      finally
      {
         if (removeThreadLocalMapping == true)
         {
            dynamicLoadAttempts.remove();
         }
         else
         {
            AtomicInteger recursiveDepth = mapping.get(className);
            if (recursiveDepth.decrementAndGet() == 0)
               mapping.remove(className);
         }
      }

      return null;
   }

   private String findMatchingDynamicImportPattern(String className)
   {
      AbstractRevision bundleRev = moduleManager.getBundleRevision(identifier);
      XModule resModule = bundleRev.getResolverModule();
      List<XPackageRequirement> dynamicRequirements = resModule.getDynamicPackageRequirements();
      if (dynamicRequirements.isEmpty())
         return null;

      String foundMatch = null;

      for (XPackageRequirement dynreq : dynamicRequirements)
      {
         String pattern = dynreq.getName();
         if (pattern.equals("*"))
         {
            foundMatch = pattern;
            break;
         }

         if (pattern.endsWith(".*"))
            pattern = pattern.substring(0, pattern.length() - 2);

         pattern = pattern.replace('.', '/');
         String path = VFSUtils.getPathFromClassName(className);
         if (path.startsWith(pattern))
         {
            foundMatch = pattern;
            break;
         }
      }

      if (foundMatch != null)
         log.tracef("Found match for class [%s] with Dynamic-ImportPackage pattern: %s", className, foundMatch);
      else
         log.tracef("Class [%s] does not match Dynamic-ImportPackage patterns", className);

      return foundMatch;
   }

   private Class<?> findInResolvedModules(String className)
   {
      log.tracef("Attempt to find class dynamically in resolved modules ...");

      // Iterate over all registered modules
      for (ModuleIdentifier aux : moduleManager.getModuleIdentifiers())
      {
         // Try to load the class from the candidate
         try
         {
            Module candidate = moduleManager.getModule(aux);

            log.tracef("Attempt to find class dynamically [%s] in %s ...", className, candidate);

            ModuleClassLoader classLoader = candidate.getClassLoader();
            Class<?> result = classLoader.loadClass(className);

            log.tracef("Found class [%s] in %s", className, candidate);

            return result;
         }
         catch (ClassNotFoundException ex)
         {
            // ignore
         }
      }

      return null;
   }

   private Class<?> findInUnresolvedModules(String className)
   {
      log.tracef("Attempt to find class dynamically in unresolved modules ...");

      // Iteraterate over all bundles in state INSTALLED
      for (Bundle aux : bundleManager.getBundles())
      {
         if (aux.getState() != Bundle.INSTALLED)
            continue;

         // Attempt to resolve the bundle
         AbstractBundle bundle = AbstractBundle.assertBundleState(aux);
         if (bundle.ensureResolved(false) == false)
            continue;

         // Create and load the module. This should not fail for resolved bundles.
         ModuleIdentifier identifier = bundle.getModuleIdentifier();
         Module candidate = moduleManager.getModule(identifier);

         // Try to load the class from the now resolved module
         try
         {
            log.tracef("Attempt to find class dynamically [%s] in %s ...", className, candidate);

            ModuleClassLoader classLoader = candidate.getClassLoader();
            Class<?> result = classLoader.loadClass(className);

            log.tracef("Found class [%s] in %s", className, candidate);

            return result;
         }
         catch (ClassNotFoundException ex)
         {
            // ignore
         }
      }

      return null;
   }

   @Override
   public List<Resource> loadResourceLocal(String name)
   {
      return Collections.emptyList();
   }

   @Override
   public Resource loadResourceLocal(String root, String name)
   {
      return null;
   }
}
