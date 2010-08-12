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
package org.jboss.osgi.container.plugin.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.AbstractRevision;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.DeploymentBundle;
import org.jboss.osgi.container.bundle.FragmentRevision;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.container.plugin.NativeCodePlugin;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.NativeLibraryMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResolverCallback;
import org.jboss.osgi.resolver.XResolverException;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.resolver.XWire;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * The resolver plugin.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2009
 */
public class ResolverPluginImpl extends AbstractPlugin implements ResolverPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(ResolverPluginImpl.class);

   // The resolver delegate
   private final XResolver resolver;
   private final NativeCodePlugin nativeCodePlugin;
   private final ModuleManagerPlugin moduleManager;

   public ResolverPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      resolver = XResolverFactory.getResolver();
      nativeCodePlugin = getOptionalPlugin(NativeCodePlugin.class);
      moduleManager = getPlugin(ModuleManagerPlugin.class);
   }

   @Override
   public XResolver getResolver()
   {
      return resolver;
   }

   @Override
   public void addModule(XModule resModule)
   {
      resolver.addModule(resModule);
   }

   @Override
   public void removeModule(XModule resModule)
   {
      resolver.removeModule(resModule);
   }

   @Override
   public void resolve(XModule resModule) throws BundleException
   {
      List<XModule> resolved = new ArrayList<XModule>();
      resolver.setCallbackHandler(new ResolverCallback(resolved));
      try
      {
         resolver.resolve(resModule);
      }
      catch (XResolverException ex)
      {
         throw new BundleException("Cannot resolve bundle resModule: " + resModule, ex);
      }

      // Load the resolved module
      applyResolverResults(resolved);
   }

   @Override
   public boolean resolveAll(Set<XModule> resModules)
   {
      // Get the list of unresolved modules
      Set<XModule> unresolved = new LinkedHashSet<XModule>();
      if (resModules == null)
      {
         for (AbstractBundle aux : getBundleManager().getBundles())
         {
            if (aux.getState() == Bundle.INSTALLED)
               unresolved.add(aux.getResolverModule());
         }
      }
      else
      {
         for (XModule aux : resModules)
         {
            if (!aux.isResolved())
               unresolved.add(aux);
         }
      }

      List<XModule> resolved = new ArrayList<XModule>();
      resolver.setCallbackHandler(new ResolverCallback(resolved));

      // Resolve the modules
      log.debug("Resolve modules: " + unresolved);
      boolean allResolved = resolver.resolveAll(unresolved);
      
      // Report resolver errors
      if (allResolved == false)
      {
         for (XModule resModule : unresolved)
         {
            if (resModule.isResolved() == false)
            {
               XResolverException ex = resModule.getAttachment(XResolverException.class);
               log.error("Cannot resolve: " + resModule, ex);
            }
         }
      }
      
      // Apply resolver results
      applyResolverResults(resolved);
      
      return allResolved;
   }

   private void applyResolverResults(List<XModule> resolved)
   {
      // For every resolved host bundle create the {@link ModuleSpec}
      createModuleSpecs(resolved);
      
      // For every resolved host bundle load the module. This creates the {@link ModuleClassLoader}
      loadModules(resolved);
      
      for (XModule aux : resolved)
      {
         if (aux.isFragment() == true)
         {
            FragmentRevision fragRev = (FragmentRevision)aux.getAttachment(AbstractRevision.class);
            fragRev.attachToHost();
         }
      }
      
      // Resolve native code libraries if there are any
      resolveNativeCodeLibraries(resolved);
      
      // Change the bundle state to RESOLVED
      setBundleToResolved(resolved);
   }

   private void setBundleToResolved(List<XModule> resolved)
   {
      for (XModule aux : resolved)
      {
         Bundle bundle = aux.getAttachment(Bundle.class);
         AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
         bundleState.changeState(Bundle.RESOLVED);
      }
   }

   private void resolveNativeCodeLibraries(List<XModule> resolved)
   {
      for (XModule aux : resolved)
      {
         if (aux.getModuleId() != 0)
         {
            Bundle bundle = aux.getAttachment(Bundle.class);
            DeploymentBundle bundleState = DeploymentBundle.assertBundleState(bundle);
            Deployment deployment = bundleState.getDeployment();

            // Resolve the native code libraries, if there are any
            NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
            if (nativeCodePlugin != null && libMetaData != null)
               nativeCodePlugin.resolveNativeCode(bundleState);
         }
      }
   }

   private void loadModules(List<XModule> resolved)
   {
      for (XModule aux : resolved)
      {
         if (aux.isFragment() == false)
         {
            ModuleIdentifier identifier = ModuleManager.getModuleIdentifier(aux);
            try
            {
               moduleManager.loadModule(identifier);
            }
            catch (ModuleLoadException ex)
            {
               throw new IllegalStateException("Cannot load module: " + identifier, ex);
            }
         }
      }
   }

   private void createModuleSpecs(List<XModule> resolved)
   {
      for (XModule aux : resolved)
      {
         if (aux.isFragment() == false)
            moduleManager.createModuleSpec(aux);
      }
   }

   class ResolverCallback implements XResolverCallback
   {
      private List<XModule> resolved;

      ResolverCallback(List<XModule> resolved)
      {
         this.resolved = resolved;
      }

      @Override
      public void markResolved(XModule module)
      {
         // Construct debug message
         StringBuffer buffer = new StringBuffer("Mark resolved: " + module);
         for (XWire wire : module.getWires())
            buffer.append("\n " + wire.toString());
         
         log.debug(buffer);
         resolved.add(module);
      }
   }
}