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
/*
 * Copyright (c) OSGi Alliance (2000, 2008). All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.osgi.util.tracker;

import org.osgi.framework.ServiceReference;

/**
 * The <code>ServiceTrackerCustomizer</code> interface allows a
 * <code>ServiceTracker</code> to customize the service objects that are
 * tracked. A <code>ServiceTrackerCustomizer</code> is called when a service is
 * being added to a <code>ServiceTracker</code>. The
 * <code>ServiceTrackerCustomizer</code> can then return an object for the
 * tracked service. A <code>ServiceTrackerCustomizer</code> is also called when
 * a tracked service is modified or has been removed from a
 * <code>ServiceTracker</code>.
 * 
 * <p>
 * The methods in this interface may be called as the result of a
 * <code>ServiceEvent</code> being received by a <code>ServiceTracker</code>.
 * Since <code>ServiceEvent</code>s are synchronously delivered by the
 * Framework, it is highly recommended that implementations of these methods do
 * not register (<code>BundleContext.registerService</code>), modify (
 * <code>ServiceRegistration.setProperties</code>) or unregister (
 * <code>ServiceRegistration.unregister</code>) a service while being
 * synchronized on any object.
 * 
 * <p>
 * The <code>ServiceTracker</code> class is thread-safe. It does not call a
 * <code>ServiceTrackerCustomizer</code> while holding any locks.
 * <code>ServiceTrackerCustomizer</code> implementations must also be
 * thread-safe.
 * 
 * @ThreadSafe
 * @version $Revision: 5874 $
 */
public interface ServiceTrackerCustomizer {
	/**
	 * A service is being added to the <code>ServiceTracker</code>.
	 * 
	 * <p>
	 * This method is called before a service which matched the search
	 * parameters of the <code>ServiceTracker</code> is added to the
	 * <code>ServiceTracker</code>. This method should return the service object
	 * to be tracked for the specified <code>ServiceReference</code>. The
	 * returned service object is stored in the <code>ServiceTracker</code> and
	 * is available from the <code>getService</code> and
	 * <code>getServices</code> methods.
	 * 
	 * @param reference The reference to the service being added to the
	 *        <code>ServiceTracker</code>.
	 * @return The service object to be tracked for the specified referenced
	 *         service or <code>null</code> if the specified referenced service
	 *         should not be tracked.
	 */
	public Object addingService(ServiceReference reference);

	/**
	 * A service tracked by the <code>ServiceTracker</code> has been modified.
	 * 
	 * <p>
	 * This method is called when a service being tracked by the
	 * <code>ServiceTracker</code> has had it properties modified.
	 * 
	 * @param reference The reference to the service that has been modified.
	 * @param service The service object for the specified referenced service.
	 */
	public void modifiedService(ServiceReference reference, Object service);

	/**
	 * A service tracked by the <code>ServiceTracker</code> has been removed.
	 * 
	 * <p>
	 * This method is called after a service is no longer being tracked by the
	 * <code>ServiceTracker</code>.
	 * 
	 * @param reference The reference to the service that has been removed.
	 * @param service The service object for the specified referenced service.
	 */
	public void removedService(ServiceReference reference, Object service);
}
