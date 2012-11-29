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

import java.io.IOException;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.BundleStorageImpl;

/**
 * A simple implementation of a BundleStorage
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class BundleStoragePlugin extends AbstractIntegrationService<BundleStorage> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final boolean firstInit;

    public BundleStoragePlugin(boolean firstInit) {
        super(IntegrationServices.BUNDLE_STORAGE);
        this.firstInit = firstInit;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleStorage> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        super.start(startContext);
        try {
            BundleStorage bundleStorage = getValue();
            BundleManager bundleManager = injectedBundleManager.getValue();
            bundleStorage.initialize(bundleManager.getProperties(), firstInit);
        } catch (IOException ex) {
            throw new StartException(ex);
        }
    }

    @Override
    protected BundleStorage createServiceValue(StartContext startContext) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        return new BundleStorageImpl(bundleManager);
    }
}