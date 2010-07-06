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
package org.jboss.osgi.msc.plugin.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.msc.bundle.AbstractBundle;
import org.jboss.osgi.msc.bundle.BundleManager;
import org.jboss.osgi.msc.plugin.AbstractPlugin;
import org.jboss.osgi.msc.plugin.ModuleManagerPlugin;
import org.jboss.osgi.msc.plugin.ResolverPlugin;
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
 * @since 06-Jul-2009
 */
public class ResolverPluginImpl extends AbstractPlugin implements ResolverPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(ResolverPluginImpl.class);

   // The resolver delegate
   private XResolver resolver;

   public ResolverPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      resolver = XResolverFactory.getResolver();
      resolver.setCallbackHandler(new ResolverCallback());
   }

   @Override
   public void addBundle(AbstractBundle bundleState)
   {
      XModule resolverModule = bundleState.getResolverModule();
      resolverModule.addAttachment(Bundle.class, bundleState);
      resolver.addModule(resolverModule);
   }

   @Override
   public void removeBundle(AbstractBundle bundleState)
   {
      XModule resolverModule = bundleState.getResolverModule();
      if (resolver.removeModule(resolverModule) == null)
         throw new IllegalStateException("Cannot remove bundle: " + bundleState);
   }

   @Override
   public void resolve(AbstractBundle bundleState) throws BundleException
   {
      XModule resModule = bundleState.getResolverModule();
      try
      {
         resolver.resolve(resModule);
      }
      catch (XResolverException ex)
      {
         throw new BundleException("Cannot resolve bundle: " + bundleState, ex);
      }
   }

   @Override
   public List<AbstractBundle> resolve(List<AbstractBundle> bundles)
   {
      // Get the list of unresolved modules
      List<XModule> unresolved = new ArrayList<XModule>();
      if (bundles == null)
      {
         for (AbstractBundle aux : getBundleManager().getBundles())
         {
            if (aux.getState() == Bundle.INSTALLED)
               unresolved.add(aux.getResolverModule());
         }
      }
      else
      {
         for (AbstractBundle aux : bundles)
         {
            if (aux.getState() == Bundle.INSTALLED)
               unresolved.add(aux.getResolverModule());
         }
      }
      log.debug("Resolve bundles: " + unresolved);

      // Resolve the modules and report resolver errors
      List<XModule> resolved = resolver.resolve(unresolved);
      for (XModule resModule : unresolved)
      {
         if (resModule.isResolved() == false)
         {
            XResolverException ex = resModule.getAttachment(XResolverException.class);
            log.error("Cannot resolve: " + resModule, ex);
         }
      }

      // Convert results into bundles
      List<AbstractBundle> result = new ArrayList<AbstractBundle>();
      for (XModule resModule : resolved)
      {
         Bundle bundle = resModule.getAttachment(Bundle.class);
         result.add(AbstractBundle.assertBundleState(bundle));
      }
      return Collections.unmodifiableList(result);
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
      public void markResolved(XModule module)
      {
         if (moduleManager == null)
            moduleManager = getPlugin(ModuleManagerPlugin.class);

         try
         {
            log.debug("Mark resolved: " + module);
            moduleManager.loadModule(module);
         }
         catch (ModuleLoadException ex)
         {
            log.error("Cannot load module: " + module, ex);
         }
      }
   }
}