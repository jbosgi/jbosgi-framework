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
package org.jboss.osgi.felix.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.felix.framework.Logger;
import org.apache.felix.framework.capabilityset.Attribute;
import org.apache.felix.framework.capabilityset.Capability;
import org.apache.felix.framework.capabilityset.Directive;
import org.apache.felix.framework.capabilityset.Requirement;
import org.apache.felix.framework.resolver.Module;
import org.apache.felix.framework.resolver.ResolveException;
import org.apache.felix.framework.resolver.Wire;
import org.apache.felix.framework.util.Util;
import org.apache.felix.framework.util.manifestparser.RequirementImpl;
import org.osgi.framework.Bundle;

/**
 * An implementation of the Resolver.
 * 
 * This implemantion should use no framework specific API.
 * It is the extension point for a framework specific Resolver.
 *  
 * @author thomas.diesler@jboss.com
 * @since 31-May-2010
 */
public abstract class AbstractResolverPlugin
{
   // TODO abstract logging
   private Logger logger;
   
   private AbstractResolver resolver;
   private AbstractResolverState resolverState;

   public AbstractResolverPlugin()
   {
      logger = getLogger();
      resolver = getAbstractResolver(logger);
      resolverState = getAbstractResolverState(logger);
   }

   protected Logger getLogger()
   {
      return new LoggerDelegate();
   }

   protected AbstractResolver getAbstractResolver(Logger logger)
   {
      return new AbstractResolver(logger);
   }

   protected AbstractResolverState getAbstractResolverState(Logger logger)
   {
      return new AbstractResolverState(logger);
   }

   public void addModule(AbstractModule module)
   {
      resolverState.addModule(module);
   }

   public void removeModule(AbstractModule module)
   {
      resolverState.removeModule(module);
   }

   public AbstractModule findHost(AbstractModule fragModule)
   {
      return (AbstractModule)resolverState.findHost(fragModule);
   }
   
   public void resolve(Module rootModule) throws ResolveException
   {
      if (!rootModule.isResolved())
      {
         // Acquire global lock.
         boolean locked = acquireGlobalLock();
         if (!locked)
            throw new ResolveException("Unable to acquire global lock for resolve.", rootModule, null);

         try
         {
            // If the root module to resolve is a fragment, then we
            // must find a host to attach it to and resolve the host
            // instead, since the underlying resolver doesn't know
            // how to deal with fragments.
            Module newRootModule = resolverState.findHost(rootModule);
            if (!Util.isFragment(newRootModule))
            {
               // Check singleton status.
               resolverState.checkSingleton(newRootModule);

               boolean repeat;
               do
               {
                  repeat = false;
                  try
                  {
                     // Resolve the module.
                     Map<Module, List<Wire>> wireMap = resolver.resolve(resolverState, newRootModule);

                     // Mark all modules as resolved.
                     markResolvedModules(wireMap);
                  }
                  catch (ResolveException ex)
                  {
                     Module fragment = ex.getFragment();
                     if (fragment != null && rootModule != fragment)
                     {
                        resolverState.detachFragment(newRootModule, fragment);
                        repeat = true;
                     }
                     else
                     {
                        throw ex;
                     }
                  }
               }
               while (repeat);
            }
         }
         finally
         {
            // Always release the global lock.
            releaseGlobalLock();
         }
      }
   }

   public Wire resolve(Module module, String pkgName) throws ResolveException
   {
      Wire candidateWire = null;
      // We cannot dynamically import if the module is not already resolved
      // or if it is not allowed, so check that first. Note: We check if the
      // dynamic import is allowed without holding any locks, but this is
      // okay since the resolver will double check later after we have
      // acquired the global lock below.
      if (module.isResolved() && isAllowedDynamicImport(module, pkgName))
      {
         // Acquire global lock.
         boolean locked = acquireGlobalLock();
         if (!locked)
            throw new ResolveException("Unable to acquire global lock for resolve.", module, null);

         try
         {
            // Double check to make sure that someone hasn't beaten us to
            // dynamically importing the package, which can happen if two
            // threads are racing to do so. If we have an existing wire,
            // then just return it instead.
            List<Wire> wires = module.getWires();
            for (int i = 0; (wires != null) && (i < wires.size()); i++)
            {
               if (wires.get(i).hasPackage(pkgName))
               {
                  return wires.get(i);
               }
            }

            Map<Module, List<Wire>> wireMap = resolver.resolve(resolverState, module, pkgName);

            if ((wireMap != null) && wireMap.containsKey(module))
            {
               List<Wire> dynamicWires = wireMap.remove(module);
               candidateWire = dynamicWires.get(0);

               // Mark all modules as resolved.
               markResolvedModules(wireMap);

               // Dynamically add new wire to importing module.
               if (candidateWire != null)
               {
                  wires = new ArrayList<Wire>(wires.size() + 1);
                  wires.addAll(module.getWires());
                  wires.add(candidateWire);
                  module.setWires(wires);
                  logger.log(Logger.LOG_DEBUG, "DYNAMIC WIRE: " + wires.get(wires.size() - 1));
               }
            }
         }
         finally
         {
            // Always release the global lock.
            releaseGlobalLock();
         }
      }

      return candidateWire;
   }

   public Set<Capability> getCandidates(Module reqModule, Requirement req, boolean obeyMandatory)
   {
      return resolverState.getCandidates(reqModule, req, obeyMandatory);
   }

   // This method duplicates a lot of logic from:
   // ResolverImpl.getDynamicImportCandidates()
   public boolean isAllowedDynamicImport(Module module, String pkgName)
   {
      // Unresolved modules cannot dynamically import, nor can the default
      // package be dynamically imported.
      if (!module.isResolved() || pkgName.length() == 0)
      {
         return false;
      }

      // If the module doesn't have dynamic imports, then just return
      // immediately.
      List<Requirement> dynamics = module.getDynamicRequirements();
      if ((dynamics == null) || (dynamics.size() == 0))
      {
         return false;
      }

      // If any of the module exports this package, then we cannot
      // attempt to dynamically import it.
      List<Capability> caps = module.getCapabilities();
      for (int i = 0; (caps != null) && (i < caps.size()); i++)
      {
         if (caps.get(i).getNamespace().equals(Capability.PACKAGE_NAMESPACE) && caps.get(i).getAttribute(Capability.PACKAGE_ATTR).getValue().equals(pkgName))
         {
            return false;
         }
      }
      // If any of our wires have this package, then we cannot
      // attempt to dynamically import it.
      List<Wire> wires = module.getWires();
      for (int i = 0; (wires != null) && (i < wires.size()); i++)
      {
         if (wires.get(i).hasPackage(pkgName))
         {
            return false;
         }
      }

      // Loop through the importer's dynamic requirements to determine if
      // there is a matching one for the package from which we want to
      // load a class.
      List<Directive> dirs = Collections.emptyList();
      List<Attribute> attrs = new ArrayList<Attribute>(1);
      attrs.add(new Attribute(Capability.PACKAGE_ATTR, pkgName, false));
      Requirement req = new RequirementImpl(module, Capability.PACKAGE_NAMESPACE, dirs, attrs);
      Set<Capability> candidates = resolverState.getCandidates(module, req, false);

      if (candidates.size() == 0)
      {
         return false;
      }

      return true;
   }

   private void markResolvedModules(Map<Module, List<Wire>> wireMap)
   {
      if (wireMap != null)
      {
         Iterator<Entry<Module, List<Wire>>> iter = wireMap.entrySet().iterator();
         // Iterate over the map to mark the modules as resolved and
         // update our resolver data structures.
         while (iter.hasNext())
         {
            Entry<Module, List<Wire>> entry = iter.next();
            Module module = entry.getKey();
            List<Wire> wires = entry.getValue();

            // Only add wires attribute if some exist; export
            // only modules may not have wires.
            for (int wireIdx = 0; wireIdx < wires.size(); wireIdx++)
            {
               logger.log(Logger.LOG_DEBUG, "WIRE: " + wires.get(wireIdx));
            }
            module.setWires(wires);

            // Resolve all attached fragments.
            List<Module> fragments = module.getFragments();
            for (int i = 0; (fragments != null) && (i < fragments.size()); i++)
            {
               fragments.get(i).setResolved();
               // Update the state of the module's bundle to resolved as well.
               markBundleResolved(fragments.get(i));
               logger.log(Logger.LOG_DEBUG, "FRAGMENT WIRE: " + fragments.get(i) + " -> hosted by -> " + module);
            }
            // Update the resolver state to show the module as resolved.
            module.setResolved();
            resolverState.moduleResolved(module);
            // Update the state of the module's bundle to resolved as well.
            markBundleResolved(module);
         }
      }
   }

   public abstract boolean acquireGlobalLock();

   public abstract void releaseGlobalLock();

   public abstract AbstractModule createModule(Bundle bundle);

   public abstract AbstractModule getModule(Bundle bundle);
   
   public abstract void markBundleResolved(Module module);
}