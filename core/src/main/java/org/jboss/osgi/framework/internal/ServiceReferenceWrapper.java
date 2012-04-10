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

import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

/**
 * ServiceReferenceWrapper
 * 
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
final class ServiceReferenceWrapper implements ServiceReference {

    private ServiceState delegate;

    ServiceReferenceWrapper(ServiceState serviceState) {
        assert serviceState != null : "Null serviceState";
        this.delegate = serviceState;
    }

    ServiceState getServiceState() {
        return delegate;
    }

    public Object getProperty(String key) {
        return delegate.getProperty(key);
    }

    public String[] getPropertyKeys() {
        return delegate.getPropertyKeys();
    }

    public Bundle getBundle() {
        return delegate.getBundle();
    }

    public Bundle[] getUsingBundles() {
        return delegate.getUsingBundles();
    }

    public boolean isAssignableTo(Bundle bundle, String className) {
        return delegate.isAssignableTo(bundle, className);
    }

    public int compareTo(Object reference) {
        return delegate.compareTo(reference);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ServiceReferenceWrapper == false)
            return false;
        if (obj == this)
            return true;
        ServiceReferenceWrapper other = (ServiceReferenceWrapper) obj;
        return delegate.equals(other.delegate);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
