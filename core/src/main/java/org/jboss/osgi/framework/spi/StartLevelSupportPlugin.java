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

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.internal.StartLevelSupportImpl;

/**
 * An implementation of the {@link StartLevelSupport} service.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @author Thomas.Diesler@jboss.com
 */
public class StartLevelSupportPlugin extends ExecutorServicePlugin<StartLevelSupport> {

    private final InjectedValue<FrameworkEvents> injectedFrameworkEvents = new InjectedValue<FrameworkEvents>();

    public StartLevelSupportPlugin() {
        super(IntegrationServices.START_LEVEL_SUPPORT, "StartLevel Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<StartLevelSupport> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(IntegrationServices.FRAMEWORK_EVENTS, FrameworkEvents.class, injectedFrameworkEvents);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected StartLevelSupport createServiceValue(StartContext startContext) throws StartException {
        FrameworkEvents events = injectedFrameworkEvents.getValue();
        return new StartLevelSupportImpl(getBundleManager(), events, getExecutorService(), new AtomicBoolean(false));
    }
}
