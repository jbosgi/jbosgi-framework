/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.spi;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;

/**
 * A plugin that creates bundle {@link Deployment} objects.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Jul-2010
 */
public interface DeploymentProvider {

    /**
     * Create a {@link Deployment} from the given bundle storage.
     *
     * @param storageState The bundle storage to be associated with the deployment
     * @throws BundleException If the given root file does not
     */
    Deployment createDeployment(StorageState storageState) throws BundleException;

    /**
     * Creates {@link OSGiMetaData} from the deployment.
     *
     * @return The OSGiMetaData
     * @throws BundleException If OSGiMetaData could not be constructed from the deployment
     */
    OSGiMetaData createOSGiMetaData(Deployment deployment) throws BundleException;

    Deployment createDeployment(String location, VirtualFile rootFile) throws BundleException;
}