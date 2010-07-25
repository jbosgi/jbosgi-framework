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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.modules.AssertionSetting;
import org.jboss.modules.Module;
import org.jboss.modules.Module.Flag;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * A {@link ModuleClassLoader} that has OSGi semantics.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class OSGiModuleClassLoader extends ModuleClassLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(OSGiModuleClassLoader.class);

   private static ThreadLocal<Map<String, AtomicInteger>> dynamicLoadAttempts = new ThreadLocal<Map<String, AtomicInteger>>();
   private BundleManager bundleManager;
   private ModuleManagerPlugin moduleManager;
   private XModule resModule;

   public OSGiModuleClassLoader(BundleManager bundleManager, XModule resModule, Module module, ModuleSpec moduleSpec)
   {
      super(module, Collections.<Flag> emptySet(), AssertionSetting.INHERIT, moduleSpec.getContentLoader());
      this.bundleManager = bundleManager;
      this.moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
      this.resModule = resModule;

      setModuleLogger(new JBossLoggingModuleLogger(Logger.getLogger(ModuleClassLoader.class)));
   }

   @Override
   protected Class<?> findClass(String className, boolean exportsOnly) throws ClassNotFoundException
   {
      // Check if we have already loaded it..
      Class<?> loadedClass = findLoadedClass(className);
      if (loadedClass != null)
         return loadedClass;

      boolean traceEnabled = log.isTraceEnabled();
      if (traceEnabled)
         log.trace("Attempt to find class [" + className + "] in " + getModule() + " ...");

      // Try the Module delegation graph
      Class<?> result = null;
      try
      {
         result = super.findClass(className, exportsOnly);
         if (result != null)
         {
            if (traceEnabled)
               log.trace("Found class [" + className + "] in " + getModule());
            return result;
         }
      }
      catch (ClassNotFoundException ex)
      {
         if (traceEnabled)
            log.trace("Cannot find class [" + className + "] in " + getModule());
      }

      // Try to load the class dynamically
      Map<String, AtomicInteger> mapping = dynamicLoadAttempts.get();
      try
      {
         if (mapping == null)
         {
            mapping = new HashMap<String, AtomicInteger>();
            dynamicLoadAttempts.set(mapping);
         }
         
         AtomicInteger recursiveDepth = mapping.get(className);
         if (recursiveDepth == null)
            mapping.put(className, recursiveDepth = new AtomicInteger());
         
         if (recursiveDepth.incrementAndGet() == 1)
         {
            result = findClassDynamically(className);
            if (result != null)
               return result;
         }
      }
      finally
      {
         AtomicInteger recursiveDepth = mapping.get(className);
         if (recursiveDepth.decrementAndGet() == 0)
            mapping.remove(className);
      }

      throw new ClassNotFoundException(className);
   }

   private Class<?> findClassDynamically(String className)
   {
      Class<?> result = null;
      if (findMatchingDynamicImportPattern(className) != null)
      {
         result = findInResolvedModules(className);
         if (result == null)
            result = findInUnresolvedModules(className);
      }
      return result;
   }

   private String findMatchingDynamicImportPattern(String className)
   {
      String foundMatch = null;

      List<XPackageRequirement> dynamicRequirements = resModule.getDynamicPackageRequirements();
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

         pattern = pattern.replace('.', File.separatorChar);
         String path = getPathFromClassName(className);
         if (path.startsWith(pattern))
         {
            foundMatch = pattern;
            break;
         }
      }

      if (log.isTraceEnabled())
      {
         if (foundMatch != null)
            log.trace("Found match for class [" + className + "] with Dynamic-ImportPackage pattern: " + foundMatch);
         else
            log.trace("Class [" + className + "] does not match Dynamic-ImportPackage patterns");
      }

      return foundMatch;
   }

   private Class<?> findInResolvedModules(String className)
   {
      boolean traceEnabled = log.isTraceEnabled();
      if (traceEnabled)
         log.trace("Attempt to find class dynamically in resolved modules ...");

      // Iterate over all registered modules
      for (ModuleIdentifier aux : moduleManager.getModuleIdentifiers())
      {
         // Try to load the class from the candidate
         try
         {
            Module candidate = moduleManager.getModule(aux);

            if (traceEnabled)
               log.trace("Attempt to find class dynamically [" + className + "] in " + candidate + " ...");

            ModuleClassLoader classLoader = candidate.getClassLoader();
            Class<?> result = classLoader.loadClass(className);

            if (traceEnabled)
               log.trace("Found class [" + className + "] in " + candidate);

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
      boolean traceEnabled = log.isTraceEnabled();
      if (traceEnabled)
         log.trace("Attempt to find class dynamically in unresolved modules ...");

      // Iteraterate over all bundles in state INSTALLED
      ResolverPlugin resolver = bundleManager.getPlugin(ResolverPlugin.class);
      for (Bundle aux : bundleManager.getBundles())
      {
         if (aux.getState() != Bundle.INSTALLED)
            continue;

         // Attempt to resolve the bundle
         AbstractBundle bundleState = AbstractBundle.assertBundleState(aux);
         try
         {
            resolver.resolve(bundleState);
         }
         catch (BundleException ex)
         {
            continue;
         }

         // Create and load the module. This should not fail for resolved bundles.
         Module candidate;
         try
         {
            ModuleIdentifier identifier = ModuleManager.getModuleIdentifier(bundleState.getResolverModule());
            candidate = moduleManager.findModule(identifier);
         }
         catch (ModuleLoadException ex)
         {
            continue;
         }

         // Try to load the class from the now resolved module
         try
         {
            if (traceEnabled)
               log.trace("Attempt to find class dynamically [" + className + "] in " + candidate + " ...");

            ModuleClassLoader classLoader = candidate.getClassLoader();
            Class<?> result = classLoader.loadClass(className);

            if (traceEnabled)
               log.trace("Found class [" + className + "] in " + candidate);

            return result;
         }
         catch (ClassNotFoundException ex)
         {
            // ignore
         }
      }

      return null;
   }
}
