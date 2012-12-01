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
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.internal.BundleStartLevelImpl;

/**
 * An implementation of the {@link BundleStartLevelSupport} service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 19-Nov-2012
 */
public class BundleStartLevelPlugin extends AbstractIntegrationService<BundleStartLevelSupport>  {

    private final InjectedValue<StartLevelSupport> injectedStartLevel = new InjectedValue<StartLevelSupport>();

    public BundleStartLevelPlugin() {
        super(IntegrationServices.BUNDLE_START_LEVEL_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleStartLevelSupport> builder) {
        builder.addDependency(IntegrationService.START_LEVEL_SUPPORT, StartLevelSupport.class, injectedStartLevel);
        builder.setInitialMode(Mode.ON_DEMAND);
    }


    @Override
    protected BundleStartLevelSupport createServiceValue(StartContext startContext) throws StartException {
        return new BundleStartLevelImpl(injectedStartLevel.getValue());
    }
}
