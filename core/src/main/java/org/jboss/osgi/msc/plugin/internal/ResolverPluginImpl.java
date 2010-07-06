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

//$Id$

import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.osgi.msc.bundle.BundleManager;
import org.jboss.osgi.msc.plugin.AbstractPlugin;
import org.jboss.osgi.msc.plugin.ResolverPlugin;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResolverCallback;
import org.jboss.osgi.resolver.XResolverException;
import org.jboss.osgi.resolver.XResolverFactory;

/**
 * A simple implementation of a BundleStorage
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class ResolverPluginImpl extends AbstractPlugin implements ResolverPlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(ResolverPluginImpl.class);
   
   // The resolver delegate
   private XResolver delegate;

   public ResolverPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
      delegate = XResolverFactory.getResolver();
   }
   
   public void addModule(XModule module)
   {
      delegate.addModule(module);
   }

   public XModule removeModule(long moduleId)
   {
      return delegate.removeModule(moduleId);
   }

   public List<XModule> getModules()
   {
      return delegate.getModules();
   }

   public XModule findModuleById(long moduleId)
   {
      return delegate.findModuleById(moduleId);
   }

   public void resolve(XModule rootModule) throws XResolverException
   {
      delegate.resolve(rootModule);
   }

   public List<XModule> resolve(List<XModule> modules)
   {
      return delegate.resolve(modules);
   }

   public void setCallbackHandler(XResolverCallback callback)
   {
      delegate.setCallbackHandler(callback);
   }
}