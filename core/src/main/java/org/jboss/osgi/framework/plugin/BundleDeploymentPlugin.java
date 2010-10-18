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

import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.bundle.BundleStorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.BundleException;

/**
 * A plugin the handles Bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Jul-2010
 */
public interface BundleDeploymentPlugin extends Plugin
{
   /**
    * Create a {@link Deployment} from the given bundle storage.
    * @param storageState The bundle storage to be associated with the deployment
    * @throws BundleException If the given root file does not
    */
   Deployment createDeployment(BundleStorageState storageState) throws BundleException;

   /**
    * Create a {@link Deployment} from the given module identifier.
    * @param identifier The root file pointing to one of the supported bundle formats
    * @param location The bundle location to be associated with the deployment
    * @throws BundleException If the given root file does not
    */
   Deployment createDeployment(ModuleIdentifier identifier) throws BundleException;

   /**
    * Creates {@link OSGiMetaData} from the deployment.
    * @return The OSGiMetaData
    * @throws BundleException If OSGiMetaData could not be constructed from the deployment
    */
   OSGiMetaData createOSGiMetaData(Deployment dep) throws BundleException;
}