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

import java.util.Collection;

import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.hooks.service.ListenerHook.ListenerInfo;

/**
 * A plugin that manages {@link FrameworkListener}, {@link BundleListener}, {@link ServiceListener} and their associated
 * {@link FrameworkEvent}, {@link BundleEvent}, {@link ServiceEvent}.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public interface FrameworkEvents {

    void addBundleListener(XBundle bundle, BundleListener listener);

    void removeBundleListener(XBundle bundle, BundleListener listener);

    void removeBundleListeners(XBundle bundle);

    void removeAllBundleListeners();

    void addFrameworkListener(XBundle bundle, FrameworkListener listener);

    void removeFrameworkListener(XBundle bundle, FrameworkListener listener);

    void removeFrameworkListeners(XBundle bundle);

    void removeAllFrameworkListeners();

    void addServiceListener(XBundle bundle, ServiceListener listener, String filterstr) throws InvalidSyntaxException;

    void removeServiceListener(XBundle bundle, ServiceListener listener);

    void removeServiceListeners(XBundle bundle);

    void removeAllServiceListeners();

    void fireBundleEvent(XBundle bundle, int type);

    void fireBundleEvent(XBundle origin, XBundle bundle, int type);

    void fireFrameworkEvent(XBundle bundle, int type, Throwable th, FrameworkListener... listeners);

    void fireServiceEvent(XBundle bundle, int type, ServiceState<?> serviceState);

    Collection<ListenerInfo> getServiceListenerInfos(XBundle bundle);

}