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

import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;

/**
 * An extension to the environment.
 *
 * @author thomas.diesler@jboss.com
 * @since 11-Apr-2013
 */
public interface XLockableEnvironment extends XEnvironment {

    LockContext lockResources(Method method, XResource... resources);

    void unlockResources(LockContext context);

    void installResources(XResource[] resources, boolean aquireLock);

    void uninstallResources(XResource[] resources, boolean aquireLock);
}
