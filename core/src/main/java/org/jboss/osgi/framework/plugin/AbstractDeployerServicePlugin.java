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

import java.util.Properties;

import org.jboss.osgi.deployment.deployer.DeployerService;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceRegistration;

/**
 * A plugin that manages bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public abstract class AbstractDeployerServicePlugin extends AbstractPlugin implements DeployerServicePlugin
{
   private DeployerService delegate;
   private ServiceRegistration registration;

   public AbstractDeployerServicePlugin(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   @Override
   public void initPlugin()
   {
      BundleContext context = getBundleManager().getSystemContext();
      delegate = getDeployerService(context);

      Properties props = new Properties();
      props.put("provider", "system");
      registration = context.registerService(DeployerService.class.getName(), this, props);
   }

   /**
    * Overwrite to provide the DeployerService implementation
    */
   protected abstract DeployerService getDeployerService(BundleContext sysContext);

   @Override
   public void stopPlugin()
   {
      registration.unregister();
      registration = null;
      delegate = null;
   }

   @Override
   public Bundle deploy(Deployment bundleDep) throws BundleException
   {
      assertServiceStarted();
      return delegate.deploy(bundleDep);
   }

   @Override
   public Bundle undeploy(Deployment bundleDep) throws BundleException
   {
      assertServiceStarted();
      return delegate.undeploy(bundleDep);
   }

   @Override
   public void deploy(Deployment[] bundleDeps) throws BundleException
   {
      assertServiceStarted();
      delegate.deploy(bundleDeps);
   }

   @Override
   public void undeploy(Deployment[] bundleDeps) throws BundleException
   {
      assertServiceStarted();
      delegate.undeploy(bundleDeps);
   }

   private void assertServiceStarted()
   {
      if (delegate == null)
         throw new IllegalStateException("DeployerService not started");
   }
}