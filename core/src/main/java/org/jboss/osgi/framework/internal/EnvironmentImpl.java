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

import java.util.ArrayList;
import java.util.List;

import org.jboss.osgi.framework.spi.AbstractBundleWiring;
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.resolver.spi.AbstractEnvironment;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;

/**
 * The default {@link XEnvironment} implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
public final class EnvironmentImpl extends AbstractEnvironment implements XEnvironment {

    private final LockManager lockManager;

    public EnvironmentImpl(LockManager lockManager) {
        this.lockManager = lockManager;
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
        try {
            FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
            lockContext = lockManager.lockItems(Method.UNINSTALL, getLockableItems(wireLock, resources));
            super.uninstallResources(resources);
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public Wiring createWiring(XResource res, List<Wire> required, List<Wire> provided) {
        XBundleRevision brev = (XBundleRevision) res;
        return new AbstractBundleWiring(brev, required, provided);
    }

    private LockableItem[] getLockableItems(LockableItem item, XResource... resources) {
        List<LockableItem> items = new ArrayList<LockableItem>();
        items.add(item);
        if (resources != null) {
            for (XResource res : resources) {
                XBundleRevision brev = (XBundleRevision) res;
                if (brev.getBundle() instanceof LockableItem) {
                    items.add((LockableItem) brev.getBundle());
                }
            }
        }
        return items.toArray(new LockableItem[items.size()]);
    }
}
