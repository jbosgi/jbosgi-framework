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
package org.jboss.osgi.framework.internal;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.launch.Framework;

/**
 * A service that activates the {@link Framework}.
 *
 * This service sets it's own mode to {@link Mode#ACTIVE} when started. The
 * effect is that the framework stays active when a service that depends on the
 * {@link FrameworkActivator} goes down.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class FrameworkActivator extends AbstractFrameworkService {

    private final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
    
    static void addService(ServiceTarget serviceTarget) {
        FrameworkActivator service = new FrameworkActivator();
        ServiceBuilder<FrameworkState> builder = serviceTarget.addService(Services.FRAMEWORK_ACTIVATOR, service);
        builder.addDependency(Services.FRAMEWORK_ACTIVE, FrameworkState.class, service.injectedFramework);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private FrameworkActivator() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        ServiceController<?> controller = context.getController();
        controller.setMode(Mode.ACTIVE);
    }

    @Override
    public FrameworkState getValue() {
        return injectedFramework.getValue();
    }

}