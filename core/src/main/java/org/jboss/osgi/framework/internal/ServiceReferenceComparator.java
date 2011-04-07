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

import java.util.Comparator;

import org.osgi.framework.ServiceReference;

/**
 * If this ServiceReference and the specified ServiceReference have the same service id they are equal. This ServiceReference is
 * less than the specified ServiceReference if it has a lower service ranking and greater if it has a higher service ranking.
 * 
 * Otherwise, if this ServiceReference and the specified ServiceReference have the same service ranking, this ServiceReference
 * is less than the specified ServiceReference if it has a higher service id and greater if it has a lower service id.
 * 
 * @author thomas.diesler@jboss.com
 * @since 26-Jul-2010
 */
final class ServiceReferenceComparator implements Comparator<ServiceReference> {

    private static final Comparator<ServiceReference> INSTANCE = new ServiceReferenceComparator();

    static Comparator<ServiceReference> getInstance() {
        return INSTANCE;
    }

    // Hide ctor
    private ServiceReferenceComparator() {
    }

    @Override
    public int compare(ServiceReference ref1, ServiceReference ref2) {
        ServiceState s1 = ServiceState.assertServiceState((ServiceReference) ref1);
        ServiceState s2 = ServiceState.assertServiceState((ServiceReference) ref2);

        // If this ServiceReference and the specified ServiceReference have the same service id they are equal
        if (s1.getServiceId() == s2.getServiceId())
            return 0;

        // This ServiceReference is less than the specified ServiceReference if it has a lower service ranking
        // and greater if it has a higher service ranking.
        int thisRanking = s1.getServiceRanking();
        int otherRanking = s2.getServiceRanking();
        if (thisRanking != otherRanking)
            return thisRanking < otherRanking ? -1 : 1;

        // This ServiceReference is less than the specified ServiceReference if it has a higher service id
        // and greater if it has a lower service id
        long thisId = s1.getServiceId();
        long otherId = s2.getServiceId();
        return thisId > otherId ? -1 : 1;
    }
}
