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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;

/**
 * The {@link XBundleRevision} install/uninstall handler
 *
 * @author thomas.diesler@jboss.com
 * @since 12-May-2013
 */
final class FrameworkEnvironment extends AbstractIntegrationService<FrameworkEnvironment> {

    final static ServiceName SERVICE_NAME = IntegrationServices.INTERNAL_BASE_NAME.append("FrameworkEnvironment");
    
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();

    FrameworkEnvironment() {
        super(SERVICE_NAME);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkEnvironment> builder) {
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected FrameworkEnvironment createServiceValue(StartContext startContext) throws StartException {
        return this;
    }

    void installResources(XBundleRevision brev) {
        if (brev == null)
            throw MESSAGES.illegalArgumentNull("brev");

        LockContext lockContext = lockResources(Method.INSTALL, brev);
        try {
            injectedEnvironment.getValue().installResources(brev);
        } finally {
            unlockResources(lockContext);
        }
    }

    void uninstallResources(XBundleRevision brev) {
        if (brev == null)
            throw MESSAGES.illegalArgumentNull("brev");

        LockContext lockContext = lockResources(Method.UNINSTALL, brev);
        try {
            injectedEnvironment.getValue().uninstallResources(brev);
        } finally {
            unlockResources(lockContext);
        }
    }

    private LockContext lockResources(Method method, XBundleRevision brev) {
        LockManager lockManager = injectedLockManager.getValue();
        FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
        return lockManager.lockItems(method, wireLock, (LockableItem) brev.getBundle());
    }

    private void unlockResources(LockContext context) {
        LockManager lockManager = injectedLockManager.getValue();
        lockManager.unlockItems(context);
    }
}
