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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.bundle.DeploymentBundle;
import org.jboss.osgi.container.bundle.ModuleManager;
import org.jboss.osgi.container.bundle.Revision;
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
   private final ModuleManagerPlugin moduleManger;

   public ResolverPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      resolver = XResolverFactory.getResolver();
      resolver.setCallbackHandler(new ResolverCallback());
      nativeCodePlugin = getOptionalPlugin(NativeCodePlugin.class);
      moduleManger = getPlugin(ModuleManagerPlugin.class);
   }

   @Override
   public XResolver getResolver()
   {
      return resolver;
   }

   @Override
   public void addRevision(Revision revision)
   {
      XModule resolverModule = revision.getResolverModule();
      resolverModule.addAttachment(Revision.class, revision);
      resolver.addModule(resolverModule);
   }

   @Override
   public void removeRevision(Revision revision)
   {
      XModule resolverModule = revision.getResolverModule();
      resolver.removeModule(resolverModule);
   }
   
   @Override
   public void resolve(Revision revision) throws BundleException
   {
      XModule resModule = revision.getResolverModule();
      try
      {
         resolver.resolve(resModule);
      }
      catch (XResolverException ex)
      {
         throw new BundleException("Cannot resolve bundle revision: " + revision, ex);
      }

      // Load the resolved module
      resolveModuleInFramework(resModule);
   }

   @Override
   public List<Revision> resolve(List<Revision> revisions)
   {
      // Get the list of unresolved modules
      Set<XModule> unresolved = new LinkedHashSet<XModule>();
      if (revisions == null)
      {
         for (AbstractBundle aux : getBundleManager().getBundles())
         {
            if (aux.getState() == Bundle.INSTALLED)
               unresolved.add(aux.getResolverModule());
         }
      }
      else
      {
         for (Revision aux : revisions)
         {
            XModule resModule = aux.getResolverModule();
            if (!resModule.isResolved())
               unresolved.add(resModule);
         }
      }
      log.debug("Resolve revisions: " + unresolved);

      // Resolve the modules and report resolver errors
      Set<XModule> resolved = resolver.resolveAll(unresolved);
      for (XModule resModule : unresolved)
      {
         if (resModule.isResolved() == false)
         {
            XResolverException ex = resModule.getAttachment(XResolverException.class);
            log.error("Cannot resolve: " + resModule, ex);
         }
      }

      // Convert results into revisions
      List<Revision> result = new ArrayList<Revision>();
      for (XModule resModule : resolved)
      {
         Revision rev = resModule.getAttachment(Revision.class);
         resolveModuleInFramework(resModule);
         result.add(rev);
      }
      return Collections.unmodifiableList(result);
   }

   private void resolveModuleInFramework(XModule resModule)
   {
      ModuleIdentifier identifier = ModuleManager.getModuleIdentifier(resModule);
      try
      {
         moduleManger.loadModule(identifier);
      }
      catch (ModuleLoadException ex)
      {
         throw new IllegalStateException("Cannot load module: " + identifier, ex);
      }
      
      if (resModule.getModuleId() != 0)
      {
         Bundle bundle = resModule.getAttachment(Bundle.class);
         DeploymentBundle bundleState = DeploymentBundle.assertBundleState(bundle);
         Deployment deployment = bundleState.getDeployment();
         
         // Resolve the native code libraries, if there are any
         NativeLibraryMetaData libMetaData = deployment.getAttachment(NativeLibraryMetaData.class);
         if (nativeCodePlugin != null && libMetaData != null)
            nativeCodePlugin.resolveNativeCode(bundleState);
      }
   }
   
   class ResolverCallback implements XResolverCallback
   {
      private ModuleManagerPlugin moduleManager;

      @Override
      public boolean acquireGlobalLock()
      {
         return true;
      }

      @Override
      public void releaseGlobalLock()
      {
         // do nothing
      }

      @Override
      public void markResolved(XModule resModule)
      {
         if (moduleManager == null)
            moduleManager = getPlugin(ModuleManagerPlugin.class);

         log.debug("Mark resolved: " + resModule);
         moduleManager.createModuleSpec(resModule);

         // Change the bundle state to RESOLVED
         Bundle bundle = resModule.getAttachment(Bundle.class);
         AbstractBundle bundleState = AbstractBundle.assertBundleState(bundle);
         bundleState.changeState(Bundle.RESOLVED);
      }
   }
}