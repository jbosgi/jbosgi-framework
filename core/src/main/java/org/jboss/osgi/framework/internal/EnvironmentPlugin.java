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

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractBundleWiring;
import org.jboss.osgi.framework.spi.IntegrationService;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.spi.AbstractEnvironment;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;

/**
 * The default {@link XEnvironment} plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
final class EnvironmentPlugin extends AbstractEnvironment implements IntegrationService<XEnvironment> {

    private final InjectedValue<LockManagerPlugin> injectedLockManager = new InjectedValue<LockManagerPlugin>();

    EnvironmentPlugin() {
    }

    @Override
    public ServiceName getServiceName() {
        return Services.ENVIRONMENT;
    }

    @Override
    public ServiceController<XEnvironment> install(ServiceTarget serviceTarget, ServiceListener<Object> listener) {
        ServiceBuilder<XEnvironment> builder = serviceTarget.addService(Services.ENVIRONMENT, this);
        builder.addDependency(InternalServices.LOCK_MANAGER_PLUGIN, LockManagerPlugin.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.addListener(listener);
        return builder.install();
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public XEnvironment getValue() {
        return this;
    }

    @Override
    public void installResources(XResource... resources) {

        // Check that all installed resources are instances of {@link XBundleRevision} and have an associated {@link Bundle}
        for (XResource res : resources) {
            if (!(res instanceof XBundleRevision))
                throw MESSAGES.unsupportedResourceType(res);
            XBundleRevision brev = (XBundleRevision) res;
            if (brev.getBundle() == null)
                throw MESSAGES.cannotObtainBundleFromResource(res);
        }

        aquireFrameworkLock();
        try {
            super.installResources(resources);
        } finally {
            releaseFrameworkLock();
        }
    }

    @Override
    public synchronized void uninstallResources(XResource... resources) {
        aquireFrameworkLock();
        try {
            super.uninstallResources(resources);
        } finally {
            releaseFrameworkLock();
        }
    }

    @Override
    public void refreshResources(XResource... resources) {
        aquireFrameworkLock();
        try {
            super.refreshResources(resources);
        } finally {
            releaseFrameworkLock();
        }
    }

    @Override
    public Wiring createWiring(XResource res, List<Wire> required, List<Wire> provided) {
        XBundleRevision brev = (XBundleRevision) res;
        return new AbstractBundleWiring(brev, required, provided);
    }

    private void aquireFrameworkLock() {
        try {
            LockManagerPlugin lockManager = injectedLockManager.getValue();
            lockManager.aquireFrameworkLock();
        } catch (TimeoutException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void releaseFrameworkLock() {
        LockManagerPlugin lockManager = injectedLockManager.getValue();
        lockManager.releaseFrameworkLock();
    }
}
