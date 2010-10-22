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
package org.jboss.osgi.framework.plugin.internal;

//$Id$

import java.util.Set;

import org.jboss.osgi.deployment.interceptor.AbstractLifecycleInterceptor;
import org.jboss.osgi.deployment.interceptor.InvocationContext;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptor;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * The lifecycle interceptor that verifies that deployments ending in '.war'
 * have a WEB-INF/web.xml descriptor.
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Oct-2009
 */
public class WebXMLVerifierInterceptor extends AbstractPlugin implements LifecycleInterceptor
{
   private LifecycleInterceptor delegate;

   public WebXMLVerifierInterceptor(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public void initPlugin()
   {
      delegate = new AbstractLifecycleInterceptor()
      {
         public void invoke(int state, InvocationContext context) throws LifecycleInterceptorException
         {
            if (state == Bundle.STARTING)
            {
               try
               {
                  VirtualFile root = context.getRoot();
                  if (root != null)
                  {
                     VirtualFile webXML = root.getChild("/WEB-INF/web.xml");
                     String contextPath = (String)context.getBundle().getHeaders().get("Web-ContextPath");
                     boolean isWebApp = contextPath != null || root.getName().endsWith(".war");
                     if (isWebApp == true && webXML == null)
                        throw new LifecycleInterceptorException("Cannot obtain web.xml from: " + root.toURL());
                  }
               }
               catch (RuntimeException rte)
               {
                  throw rte;
               }
               catch (Exception ex)
               {
                  throw new LifecycleInterceptorException("Cannot check for web.xml", ex);
               }
            }
         }
      };
   }
   
   @Override
   public void destroyPlugin()
   {
      delegate = null;
   }

   @Override
   public void startPlugin()
   {
      BundleContext sysContext = getBundleManager().getSystemContext();
      sysContext.registerService(LifecycleInterceptor.class.getName(), delegate, null);
   }

   public Set<Class<?>> getInput()
   {
      return delegate.getInput();
   }

   public Set<Class<?>> getOutput()
   {
      return delegate.getOutput();
   }

   public int getRelativeOrder()
   {
      return delegate.getRelativeOrder();
   }

   public void invoke(int state, InvocationContext context) throws LifecycleInterceptorException
   {
      delegate.invoke(state, context);
   }
}