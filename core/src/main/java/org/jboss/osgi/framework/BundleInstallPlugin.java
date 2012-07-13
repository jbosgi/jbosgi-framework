/*
 * #%L
 * JBossOSGi Framework Core
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
package org.jboss.osgi.framework;

import org.jboss.msc.service.Service;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.BundleException;

/**
 * A handler for bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Mar-2011
 */
public interface BundleInstallPlugin extends Service<BundleInstallPlugin> {

    /**
     * Install the bundle service for the given deployment.
     */
    void installBundle(Deployment dep) throws BundleException;

    /**
     * Uninstall the bundle associated with the given deployment.
     */
    void uninstallBundle(Deployment dep);
}