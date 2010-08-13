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
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.jboss.modules.AssertionSetting;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ResourceLoader;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.AbstractRevision;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XPackageRequirement;
import org.osgi.framework.Bundle;

/**
 * An OSGi extention to the {@link ModuleClassLoader}.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public class ModuleClassLoaderExt extends ModuleClassLoader
{
   // Provide logging
   private static final Logger log = Logger.getLogger(ModuleClassLoaderExt.class);

   private static ThreadLocal<Map<String, AtomicInteger>> dynamicLoadAttempts;
   private final ModuleManager moduleManager;
   private final BundleManager bundleManager;
   private final AbstractRevision bundleRev;
   
   // List of native library providers 
   private volatile List<NativeLibraryProvider> nativeLibraries;
   
   public ModuleClassLoaderExt(Module module, AssertionSetting setting, Collection<ResourceLoader> resourceLoaders)
   {
      super(module, setting, resourceLoaders);
      moduleManager = (ModuleManager)module.getModuleLoader();
      bundleManager = moduleManager.getBundleManager();
      
      bundleRev = moduleManager.getBundleRevision(module.getIdentifier());
   }

   public void addNativeLibrary(NativeLibraryProvider libProvider)
   {
      if (nativeLibraries == null)
         nativeLibraries = new CopyOnWriteArrayList<NativeLibraryProvider>();
      
      nativeLibraries.add(libProvider);
   }
   
   @Override
   protected String findLibrary(String libname)
   {
      List<NativeLibraryProvider> list = nativeLibraries;
      if (list == null)
         return null;
      
      NativeLibraryProvider libProvider = null;
      for (NativeLibraryProvider aux : list)
      {
         if (libname.equals(aux.getLibraryName()))
         {
            libProvider = aux;
            break;
         }
      }
      
      if (libProvider == null)
         return null;
      
      File libfile;
      try
      {
         libfile = libProvider.getLibraryLocation();
      }
      catch (IOException ex)
      {
         log.error("Cannot privide native library location for: " + libname, ex);
         return null;
      }
      
      return libfile.getAbsolutePath();
   }

   @Override
   protected Class<?> findClass(String className, boolean exportsOnly, boolean resolve) throws ClassNotFoundException
   {
      Class<?> result = null;
      try
      {
         result = super.findClass(className, exportsOnly, resolve);
         if (result != null)
            return result;
      }
      catch (ClassNotFoundException ex)
      {
         // ignore
      }
      
      // Try to load the class dynamically
      String matchingPattern = findMatchingDynamicImportPattern(className);
      if (matchingPattern != null)
      {
         result = loadClassDynamically(className);
         if (result != null)
            return result;
      }
      
      throw new ClassNotFoundException(className + " from [" + getModule() + "]");
   }

   private Class<?> loadClassDynamically(String className) throws ClassNotFoundException
   {
      Class<?> result;
      
      if (dynamicLoadAttempts == null)
         dynamicLoadAttempts  = new ThreadLocal<Map<String, AtomicInteger>>();
      
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
      
      throw new ClassNotFoundException(className);
   }

   private String findMatchingDynamicImportPattern(String className)
   {
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

         pattern = pattern.replace('.', File.separatorChar);
         String path = ModuleManager.getPathFromClassName(className);
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
      for (Bundle aux : bundleManager.getBundles())
      {
         if (aux.getState() != Bundle.INSTALLED)
            continue;

         // Attempt to resolve the bundle
         AbstractBundle bundle = AbstractBundle.assertBundleState(aux);
         if (bundle.ensureResolved() == false)
            continue;

         // Create and load the module. This should not fail for resolved bundles.
         ModuleIdentifier identifier = ModuleManager.getModuleIdentifier(bundle.getResolverModule());
         Module candidate = moduleManager.getModule(identifier);

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
