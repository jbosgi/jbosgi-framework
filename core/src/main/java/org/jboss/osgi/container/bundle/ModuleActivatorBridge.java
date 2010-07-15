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
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.container.plugin.ServiceManagerPlugin;
import org.jboss.osgi.modules.ModuleActivator;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * A module activator that is called when the module 
 * gets loaded by the OSGi layer.
 * 
 * @author thomas.diesler@jboss.com
 * @since 13-Jul-2010
 */
public class ModuleActivatorBridge implements BundleActivator, ModuleActivator
{
   private BundleManager bundleManager;
   private ModuleActivator moduleActivator;
   
   @Override
   public void start(BundleContext context) throws Exception
   {
      AbstractBundle bundleState = ((AbstractBundleContext)context).getBundleInternal();
      bundleManager = bundleState.getBundleManager();
      
      ModuleManagerPlugin moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
      ModuleIdentifier identifier = bundleState.getModuleIdentifier();
      ModuleSpec moduleSpec = moduleManager.getModuleSpec(identifier);
      Module module = moduleManager.getModule(identifier);
      
      String mainClass = moduleSpec.getMainClass();
      try
      {
         Class<?> activatorClass = Module.loadClass(identifier, mainClass);
         moduleActivator = (ModuleActivator)activatorClass.newInstance();
      }
      catch (Exception ex)
      {
         throw new ModuleLoadException("Cannot load activator: " + mainClass);
      }

      ServiceManagerPlugin serviceManager = bundleManager.getPlugin(ServiceManagerPlugin.class);
      moduleActivator.start(serviceManager.getServiceContainer(), module);
   }

   @Override
   public void stop(BundleContext context) throws Exception
   {
      AbstractBundle bundleState = ((AbstractBundleContext)context).getBundleInternal();
      ModuleIdentifier identifier = bundleState.getModuleIdentifier();
      ModuleManagerPlugin moduleManager = bundleManager.getPlugin(ModuleManagerPlugin.class);
      Module module = moduleManager.getModule(identifier);
      
      ServiceManagerPlugin serviceManager = bundleManager.getPlugin(ServiceManagerPlugin.class);
      moduleActivator.stop(serviceManager.getServiceContainer(), module);
   }

   @Override
   public void start(ServiceContainer serviceContainer, Module module) throws ModuleLoadException
   {
   }

   @Override
   public void stop(ServiceContainer serviceContainer, Module module)
   {
   }
}
