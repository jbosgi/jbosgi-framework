/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

    private ServiceState delegate;

    ServiceRegistrationWrapper(ServiceState serviceState) {
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
