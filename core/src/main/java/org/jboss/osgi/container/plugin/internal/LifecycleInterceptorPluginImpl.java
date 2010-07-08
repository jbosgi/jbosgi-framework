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

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.AbstractBundle;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.plugin.AbstractServicePlugin;
import org.jboss.osgi.container.plugin.LifecycleInterceptorPlugin;
import org.jboss.osgi.deployment.interceptor.AbstractLifecycleInterceptorService;
import org.jboss.osgi.deployment.interceptor.InvocationContext;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorService;
import org.jboss.osgi.deployment.internal.InvocationContextImpl;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.spi.util.AttachmentSupport;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * A plugin that manages bundle lifecycle interceptors.
 * 
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public class LifecycleInterceptorPluginImpl extends AbstractServicePlugin implements LifecycleInterceptorPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(LifecycleInterceptorPluginImpl.class);

   private AbstractLifecycleInterceptorService delegate;
   private ServiceRegistration registration;

   public LifecycleInterceptorPluginImpl(final BundleManager bundleManager)
   {
      super(bundleManager);
   }

   public void startService()
   {
      BundleContext sysContext = getSystemContext();
      delegate = new AbstractLifecycleInterceptorService(sysContext)
      {
         @Override
         protected InvocationContext getInvocationContext(Bundle bundle)
         {
            long bundleId = bundle.getBundleId();
            AbstractBundle bundleState = getBundleManager().getBundleById(bundleId);
            if (bundleState == null)
               throw new IllegalStateException("Cannot obtain bundleState for: " + bundle);

            XModule unit = bundleState.getResolverModule();
            InvocationContext inv = unit.getAttachment(InvocationContext.class);
            if (inv == null)
            {
               BundleContext context = bundleState.getBundleManager().getSystemContext();
               LifecycleInterceptorAttachments att = new LifecycleInterceptorAttachments();
               inv = new InvocationContextImpl(context, bundle, bundleState.getRootFile(), att);
               unit.addAttachment(InvocationContext.class, inv);
            }
            return inv;
         }
      };

      registration = sysContext.registerService(LifecycleInterceptorService.class.getName(), delegate, null);
   }

   public void stopService()
   {
      if (registration != null)
      {
         registration.unregister();
         registration = null;
         delegate = null;
      }
   }

   public void handleStateChange(int state, Bundle bundle)
   {
      if (delegate != null)
         delegate.handleStateChange(state, bundle);
   }
   
   static class LifecycleInterceptorAttachments extends AttachmentSupport
   {
   }
}