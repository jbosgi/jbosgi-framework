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
package org.jboss.osgi.msc.bundle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleContentLoader;
import org.jboss.modules.ModuleContentLoader.Builder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.msc.loading.VirtualFileResourceLoader;
import org.jboss.osgi.msc.metadata.OSGiMetaData;

/**
 * Build the {@link ModuleSpec} from {@link OSGiMetaData}.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
class ModuleManager extends ModuleLoader
{
   /** The registered manager plugins */
   private Map<ModuleIdentifier, Module> modules = Collections.synchronizedMap(new LinkedHashMap<ModuleIdentifier, Module>());
   
   ModuleManager(BundleManager bundleManager)
   {
   }

   static ModuleSpec createModuleSpec(HostBundle bundleState)
   {
      String symbolicName = bundleState.getSymbolicName();
      String version = bundleState.getVersion().toString();
      ModuleIdentifier moduleIdentifier = new ModuleIdentifier("jbosgi", symbolicName, version);
      ModuleSpec moduleSpec = new ModuleSpec(moduleIdentifier);
      
      Builder builder = ModuleContentLoader.build();
      builder.add("/", new VirtualFileResourceLoader(bundleState.getRootFile()));
      moduleSpec.setContentLoader(builder.create());
      
      return moduleSpec;
   }
   
   @Override
   protected Module findModule(ModuleIdentifier moduleIdentifier) throws ModuleLoadException
   {
      return modules.get(moduleIdentifier);
   }

   Module createModule(HostBundle bundleState) throws ModuleLoadException
   {
      ModuleSpec moduleSpec = bundleState.getModuleSpec();
      Module module = defineModule(moduleSpec);
      modules.put(module.getIdentifier(), module);
      return bundleState.setModule(module);
   }
   
   Module destroyModule(HostBundle bundleState) throws ModuleLoadException
   {
      Module module = bundleState.getModule();
      if (module == null)
         throw new IllegalStateException("Cannot obtain module for: " + bundleState);
      
      modules.remove(module.getIdentifier());
      bundleState.resetModule();
      return module;
   }
}
