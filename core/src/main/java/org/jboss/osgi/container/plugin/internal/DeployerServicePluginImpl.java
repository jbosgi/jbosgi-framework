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

//$Id$

import java.util.Properties;

import org.jboss.logging.Logger;
import org.jboss.osgi.container.bundle.BundleManager;
import org.jboss.osgi.container.plugin.AbstractPlugin;
import org.jboss.osgi.container.plugin.DeployerServicePlugin;
import org.jboss.osgi.deployment.deployer.DefaultDeploymentRegistryService;
import org.jboss.osgi.deployment.deployer.DeployerService;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentRegistryService;
import org.jboss.osgi.deployment.deployer.SystemDeployerService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * A plugin that manages bundle deployments.
 * 
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public class DeployerServicePluginImpl extends AbstractPlugin implements DeployerServicePlugin
{
   // Provide logging
   final Logger log = Logger.getLogger(DeployerServicePluginImpl.class);

   private DeployerService delegate;

   public DeployerServicePluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public void startPlugin()
   {
      BundleContext context = getBundleManager().getSystemContext();
      delegate = new SystemDeployerService(context);

      DeploymentRegistryService registry = new DefaultDeploymentRegistryService(context);
      context.registerService(DeploymentRegistryService.class.getName(), registry, null);

      Properties props = new Properties();
      props.put("provider", "system");
      context.registerService(DeployerService.class.getName(), this, props);
   }

   public void deploy(Deployment[] bundleDeps) throws BundleException
   {
      delegate.deploy(bundleDeps);
   }

   public void undeploy(Deployment[] bundleDeps) throws BundleException
   {
      delegate.undeploy(bundleDeps);
   }
}