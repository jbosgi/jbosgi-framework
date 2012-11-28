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

import java.util.concurrent.TimeUnit;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.framework.internal.LockManagerImpl;

/**
 * The plugin for the {@link LockManager}.
 *
 * @author thomas.diesler@jboss.com
 * @since 22-Nov-2012
 */
public class LockManagerPlugin extends AbstractIntegrationService<LockManager> implements LockManager {

    private LockManager lockManager;

    public LockManagerPlugin() {
        super(IntegrationServices.LOCK_MANAGER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<LockManager> builder) {
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        lockManager = new LockManagerImpl();
    }

    @Override
    public LockManager getValue() throws IllegalStateException {
        return this;
    }

    @Override
    public <T extends LockableItem> T getItemForType(Class<T> type) {
        return lockManager.getItemForType(type);
    }

    @Override
    public LockContext getCurrentLockContext() {
        return lockManager.getCurrentLockContext();
    }

    @Override
    public LockContext lockItems(Method method, LockableItem... items) {
        return lockManager.lockItems(method, items);
    }

    @Override
    public LockContext lockItems(Method method, long timeout, TimeUnit unit, LockableItem... items) {
        return lockManager.lockItems(method, timeout, unit, items);
    }

    @Override
    public void unlockItems(LockContext context) {
        lockManager.unlockItems(context);
    }
}
