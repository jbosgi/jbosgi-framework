package org.jboss.osgi.framework.internal;
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

import java.util.Dictionary;

import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * ServiceRegistrationWrapper
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
final class ServiceRegistrationWrapper implements ServiceRegistration {

    private ServiceStateImpl delegate;

    ServiceRegistrationWrapper(ServiceStateImpl serviceState) {
        assert serviceState != null : "Null serviceState";
        this.delegate = serviceState;
    }

    @Override
    public ServiceReference getReference() {
        return delegate.getReference();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void setProperties(Dictionary properties) {
        delegate.setProperties(properties);
    }

    @Override
    public void unregister() {
        delegate.unregister();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ServiceRegistrationWrapper == false)
            return false;
        if (obj == this)
            return true;
        ServiceRegistrationWrapper other = (ServiceRegistrationWrapper) obj;
        return delegate.equals(other.delegate);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
