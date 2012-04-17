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
package org.jboss.osgi.framework;

import java.util.Map;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * Default implementation for the COMPLETE step of the {@link AutoInstallProvider}.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
public class AutoInstallProviderComplete extends AbstractInstallComplete {

    public AutoInstallProviderComplete(Map<ServiceName, Deployment> installedBundles) {
        super(installedBundles);
    }

    public void install(ServiceTarget serviceTarget) {
        ServiceBuilder<Void> builder = serviceTarget.addService(IntegrationServices.AUTOINSTALL_PROVIDER_COMPLETE, this);
        builder.addDependency(Services.PACKAGE_ADMIN, PackageAdmin.class, injectedPackageAdmin);
        builder.addDependency(IntegrationServices.AUTOINSTALL_PROVIDER);
        builder.addDependencies(installedBundles.keySet());
        addAdditionalDependencies(builder);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }
}
