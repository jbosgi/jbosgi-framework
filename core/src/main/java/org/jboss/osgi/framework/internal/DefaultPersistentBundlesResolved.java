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

import java.util.Set;

import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.PersistentBundlesResolved;
import org.jboss.osgi.framework.StorageState;

/**
 * Default implementation for the RESOLVED step of the {@link PersistentBundlesPlugin}.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Apr-2012
 */
class DefaultPersistentBundlesResolved extends PersistentBundlesResolved {

    private final Set<StorageState> storageStates;
    
    public DefaultPersistentBundlesResolved(Set<StorageState> storageStates) {
        this.storageStates = storageStates;
    }

    @Override
    protected boolean allServicesAdded(Set<ServiceName> trackedServices) {
        return storageStates.size() == trackedServices.size();
    }
}