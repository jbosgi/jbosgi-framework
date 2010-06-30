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

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleContentLoader;
import org.jboss.modules.ModuleContentLoader.Builder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleLoaderSelector;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.msc.loading.VirtualFileResourceLoader;
import org.jboss.osgi.msc.metadata.OSGiMetaData;
import org.jboss.osgi.vfs.VirtualFile;

/**
 * Build the {@link ModuleSpec} from {@link OSGiMetaData}.
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
class ModuleManager extends ModuleLoader implements ModuleLoaderSelector
{
   // Provide logging
   private static final Logger log = Logger.getLogger(ModuleManager.class);
   
   // The registered modules
   private Map<ModuleIdentifier, Module> modules = Collections.synchronizedMap(new LinkedHashMap<ModuleIdentifier, Module>());
   
   ModuleManager(BundleManager bundleManager)
   {
      // Make sure this ModuleLoader is used
      Module.setModuleLoaderSelector(this);
   }

   ModuleSpec createModuleSpec(OSGiMetaData metadata, VirtualFile rootFile)
   {
      String symbolicName = metadata.getBundleSymbolicName();
      String version = metadata.getBundleVersion();
      ModuleIdentifier moduleIdentifier = new ModuleIdentifier("jbosgi", symbolicName, version);
      ModuleSpec moduleSpec = new ModuleSpec(moduleIdentifier);
      
      Builder builder = ModuleContentLoader.build();
      builder.add("/", new VirtualFileResourceLoader(rootFile));
      moduleSpec.setContentLoader(builder.create());
      
      return moduleSpec;
   }
   
   @Override
   public ModuleLoader getCurrentLoader()
   {
      return this;
   }
   
   @Override
   protected Module findModule(ModuleIdentifier moduleIdentifier) throws ModuleLoadException
   {
      return modules.get(moduleIdentifier);
   }

   Module createModule(ModuleSpec moduleSpec) throws ModuleLoadException
   {
      Module module = defineModule(moduleSpec);
      modules.put(module.getIdentifier(), module);
      return module;
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
