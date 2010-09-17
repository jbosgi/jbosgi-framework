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
package org.jboss.osgi.framework.plugin;

//$Id$

import org.jboss.osgi.framework.bundle.BundleManager;

/**
 * The base class of all framework plugins.
 * 
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public abstract class AbstractPlugin implements Plugin
{
   private BundleManager bundleManager;

   public AbstractPlugin(BundleManager bundleManager)
   {
      if (bundleManager == null)
         throw new IllegalArgumentException("Null bundleManager");
      
      this.bundleManager = bundleManager;
   }
   
   public BundleManager getBundleManager()
   {
      return bundleManager;
   }

   @Override
   public void initPlugin()
   {
      // do nothing
   }

   @Override
   public void startPlugin()
   {
      // do nothing
   }

   @Override
   public void stopPlugin()
   {
      // do nothing
   }

   @Override
   public void destroyPlugin()
   {
      // do nothing
   }

   public <T extends Plugin> T getPlugin(Class<T> clazz)
   {
      return bundleManager.getPlugin(clazz);
   }
   
   public <T extends Plugin> T getOptionalPlugin(Class<T> clazz)
   {
      return bundleManager.getOptionalPlugin(clazz);
   }
}