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

import java.util.ArrayList;
import java.util.List;

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
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.IntegrationService;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.Method;
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

    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();

    EnvironmentPlugin() {
    }

    @Override
    public ServiceName getServiceName() {
        return Services.ENVIRONMENT;
    }

    @Override
    public ServiceController<XEnvironment> install(ServiceTarget serviceTarget, ServiceListener<Object> listener) {
        ServiceBuilder<XEnvironment> builder = serviceTarget.addService(Services.ENVIRONMENT, this);
        builder.addDependency(InternalServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
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

        LockContext lockContext = null;
        LockManager lockManager = injectedLockManager.getValue();
        try {
            FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
            lockContext = lockManager.lockItems(Method.INSTALL, getLockableItems(wireLock, resources));
            super.installResources(resources);
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public synchronized void uninstallResources(XResource... resources) {
        LockContext lockContext = null;
        LockManager lockManager = injectedLockManager.getValue();
        try {
            FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
            lockContext = lockManager.lockItems(Method.UNINSTALL, getLockableItems(wireLock, resources));
            super.uninstallResources(resources);
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public void refreshResources(XResource... resources) {
        super.refreshResources(resources);
    }

    @Override
    public Wiring createWiring(XResource res, List<Wire> required, List<Wire> provided) {
        XBundleRevision brev = (XBundleRevision) res;
        return new AbstractBundleWiring(brev, required, provided);
    }

    private LockManager.LockableItem[] getLockableItems(LockManager.LockableItem item, XResource... resources) {
        List<LockManager.LockableItem> items = new ArrayList<LockManager.LockableItem>();
        items.add(item);
        if (resources != null) {
            for (XResource res : resources) {
                XBundleRevision brev = (XBundleRevision) res;
                if (brev.getBundle() instanceof LockManager.LockableItem) {
                    items.add((LockManager.LockableItem) brev.getBundle());
                }
            }
        }
        return items.toArray(new LockManager.LockableItem[items.size()]);
    }
}
