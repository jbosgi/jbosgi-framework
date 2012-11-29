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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.DeploymentProviderImpl;

/**
 * A plugin taht create bundle {@link Deployment} objects.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Jul-2010
 */
public class DeploymentProviderPlugin extends AbstractIntegrationService<DeploymentProvider> {

    public DeploymentProviderPlugin() {
        super(IntegrationServices.DEPLOYMENT_PROVIDER_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<DeploymentProvider> builder) {
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected DeploymentProvider createServiceValue(StartContext startContext) throws StartException {
        return new DeploymentProviderImpl();
    }
}
