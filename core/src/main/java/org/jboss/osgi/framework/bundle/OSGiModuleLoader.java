package org.jboss.osgi.framework.bundle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;

final class OSGiModuleLoader extends ModuleLoader
{
   // The modules that are registered with this {@link ModuleLoader}
   private Map<ModuleIdentifier, OSGiModuleLoader.ModuleHolder> modules = Collections
         .synchronizedMap(new LinkedHashMap<ModuleIdentifier, OSGiModuleLoader.ModuleHolder>());

   @Override
   public ModuleSpec findModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      OSGiModuleLoader.ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getModuleSpec() : null;
   }

   @Override
   public Module preloadModule(ModuleIdentifier identifier) throws ModuleLoadException
   {
      OSGiModuleLoader.ModuleHolder holder = getModuleHolder(identifier);
      if (holder == null)
         throw new IllegalStateException("Cannot find module: " + identifier);

      Module module = holder.getModule();
      if (module == null)
      {
         module = super.preloadModule(identifier);
         holder.setModule(module);
      }
      return module;
   }

   void addModule(AbstractRevision bundleRev, ModuleSpec moduleSpec)
   {
      ModuleIdentifier identifier = moduleSpec.getModuleIdentifier();
      if (modules.get(identifier) != null)
         throw new IllegalStateException("Module already exists: " + identifier);
      modules.put(identifier, new ModuleHolder(bundleRev, moduleSpec));
   }

   void addModule(AbstractRevision bundleRev, Module module)
   {
      ModuleIdentifier identifier = module.getIdentifier();
      if (modules.get(identifier) != null)
         throw new IllegalStateException("Module already exists: " + identifier);

      modules.put(identifier, new ModuleHolder(bundleRev, module));
   }

   Module removeModule(ModuleIdentifier identifier)
   {
      OSGiModuleLoader.ModuleHolder moduleHolder = modules.remove(identifier);
      if (moduleHolder == null)
         return null;

      Module module = moduleHolder.module;
      if (module.getModuleLoader() == this)
         unloadModuleLocal(module);
      
      return module;
   }

   Set<ModuleIdentifier> getModuleIdentifiers()
   {
      return Collections.unmodifiableSet(modules.keySet());
   }

   AbstractRevision getBundleRevision(ModuleIdentifier identifier)
   {
      OSGiModuleLoader.ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getBundleRevision() : null;
   }

   AbstractBundle getBundleState(ModuleIdentifier identifier)
   {
      AbstractRevision bundleRev = getBundleRevision(identifier);
      return bundleRev != null ? bundleRev.getBundleState() : null;
   }

   Module getModule(ModuleIdentifier identifier)
   {
      OSGiModuleLoader.ModuleHolder holder = getModuleHolder(identifier);
      return holder != null ? holder.getModule() : null;
   }

   private OSGiModuleLoader.ModuleHolder getModuleHolder(ModuleIdentifier identifier)
   {
      OSGiModuleLoader.ModuleHolder holder = modules.get(identifier);
      return holder;
   }

   // A holder for the {@link ModuleSpec}  @{link Module} tuple
   static class ModuleHolder
   {
      private final AbstractRevision bundleRev;
      private ModuleSpec moduleSpec;
      private Module module;

      public ModuleHolder(AbstractRevision bundleRev, ModuleSpec moduleSpec)
      {
         if (bundleRev == null)
            throw new IllegalArgumentException("Null bundleRev");
         if (moduleSpec == null)
            throw new IllegalArgumentException("Null moduleSpec");
         this.bundleRev = bundleRev;
         this.moduleSpec = moduleSpec;
      }

      ModuleHolder(AbstractRevision bundleRev, Module module)
      {
         if (bundleRev == null)
            throw new IllegalArgumentException("Null bundleRev");
         if (module == null)
            throw new IllegalArgumentException("Null module");
         this.bundleRev = bundleRev;
         this.module = module;
      }

      AbstractRevision getBundleRevision()
      {
         return bundleRev;
      }

      ModuleSpec getModuleSpec()
      {
         return moduleSpec;
      }

      Module getModule()
      {
         return module;
      }

      void setModule(Module module)
      {
         this.module = module;
      }
   }
}