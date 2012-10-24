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
package org.jboss.osgi.framework.internal;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.BundleLifecyclePlugin;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.BundleException;

/**
 * A plugin that manages bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
final class DefaultBundleLifecyclePlugin extends AbstractIntegrationService<BundleLifecyclePlugin> implements BundleLifecyclePlugin {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();

    DefaultBundleLifecyclePlugin() {
        super(IntegrationServices.BUNDLE_LIFECYCLE_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleLifecyclePlugin> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void install(Deployment dep, DefaultHandler handler) throws BundleException {
        handler.install(injectedBundleManager.getValue(), dep);
    }

    @Override
    public void start(XBundle bundle, int options, DefaultHandler handler) throws BundleException {
        handler.start(bundle, options);
    }

    @Override
    public void stop(XBundle bundle, int options, DefaultHandler handler) throws BundleException {
        handler.stop(bundle, options);
    }

    @Override
    public void uninstall(XBundle bundle, DefaultHandler handler) {
        handler.uninstall(bundle);
    }

    @Override
    public BundleLifecyclePlugin getValue() {
        return this;
    }
}