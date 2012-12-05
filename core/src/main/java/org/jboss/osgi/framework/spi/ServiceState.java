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

import java.util.Dictionary;
import java.util.List;
import java.util.Set;

import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * The service implementation.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
public interface ServiceState<S> extends ServiceRegistration<S>, ServiceReference<S> {

    long getServiceId();

    S getScopedValue(XBundle bundle);

    void ungetScopedValue(XBundle bundle);

    ServiceRegistration<S> getRegistration();

    List<String> getClassNames();

    void unregisterInternal();

    Dictionary<String, ?> getPreviousProperties();

    XBundle getServiceOwner();

    void addUsingBundle(XBundle bundleState);

    void removeUsingBundle(XBundle bundle);

    Set<XBundle> getUsingBundlesInternal();

    int getServiceRanking();

    boolean isUnregistered();

    interface ValueProvider {
        boolean isFactoryValue();
        Object getValue();
    }
}