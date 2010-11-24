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

import org.jboss.osgi.deployment.deployer.DeployerService;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.SystemDeployerService;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.AbstractDeployerServicePlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * The {@link DeployerServicePlugin} that is installed by default.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Nov-2010
 */
public class DefaultDeployerServicePlugin extends AbstractDeployerServicePlugin
{
   public DefaultDeployerServicePlugin(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   protected DeployerService getDeployerService(BundleContext context)
   {
      DeployerService service = new SystemDeployerService(context)
      {
         @Override
         protected Bundle installBundle(Deployment dep) throws BundleException
         {
            AbstractBundle bundleState = getBundleManager().installBundle(dep);
            return bundleState.getBundleWrapper();
         }

         //@Override
         //protected void uninstallBundle(Deployment dep, Bundle bundle) throws BundleException
         //{
         //   getBundleManager().uninstallBundle(dep);
         //}
      };
      return service;
   }
}