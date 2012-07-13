/*
 * #%L
 * JBossOSGi Framework Core
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
package org.jboss.osgi.modules;

import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * A the context for Module/OSGi integration.
 * 
 * @author thomas.diesler@jboss.com
 * @since 05-Aug-2010
 */
public interface ModuleContext {

    /**
     * Get the ServiceName for the given service interface that an xservice implements.
     */
    ServiceName getServiceName(Class<?> service);

    ServiceContainer getServiceContainer();

    Module getModule();

    BundleContext getSystemContext();

    Bundle getBundle();
}
