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
package org.jboss.osgi.container.bundle;

import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.modules.ModuleContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * A the context for Module/OSGi integration.
 * 
 * @author thomas.diesler@jboss.com
 * @since 05-Aug-2010
 */
class ModuleContextImpl implements ModuleContext
{
   private ServiceContainer serviceContainer;
   private Module module;
   private BundleContext systemContext;
   private Bundle bundle;
   
   ModuleContextImpl(ServiceContainer serviceContainer, Module module, BundleContext systemContext, Bundle bundle)
   {
      if (serviceContainer == null)
         throw new IllegalArgumentException("Null serviceContainer");
      if (module == null)
         throw new IllegalArgumentException("Null module");
      if (systemContext == null)
         throw new IllegalArgumentException("Null systemContext");
      if (bundle == null)
         throw new IllegalArgumentException("Null bundle");
      
      this.serviceContainer = serviceContainer;
      this.module = module;
      this.systemContext = systemContext;
      this.bundle = bundle;
   }

   @Override
   public ServiceName getServiceName(Class<?> service)
   {
      if (service == null)
         throw new IllegalArgumentException("Null service");
      
      return ServiceName.of(XSERVICE_PREFIX, service.getName());
   }

   @Override
   public ServiceContainer getServiceContainer()
   {
      return serviceContainer;
   }

   @Override
   public Module getModule()
   {
      return module;
   }

   @Override
   public BundleContext getSystemContext()
   {
      return systemContext;
   }

   @Override
   public Bundle getBundle()
   {
      return bundle;
   }
}
