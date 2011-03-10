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
package org.jboss.osgi.framework.plugin;

import java.util.Dictionary;
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.ServiceContainer;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.ServiceState;
import org.osgi.framework.Bundle;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * A plugin that manages OSGi services
 * 
 * @author thomas.diesler@jboss.com
 * @since 20-Jan-2010
 */
public interface ServiceManagerPlugin extends Plugin {

    /**
     * Get the {@link ServiceContainer}
     */
    ServiceContainer getServiceContainer();

    /**
     * Get the next service is from the manager
     */
    long getNextServiceId();

    /**
     * Registers the specified service object with the specified properties under the specified class names into the Framework.
     * A <code>ServiceRegistration</code> object is returned. The <code>ServiceRegistration</code> object is for the private use
     * of the bundle registering the service and should not be shared with other bundles. The registering bundle is defined to
     * be the context bundle.
     * 
     * @param clazzes The class names under which the service can be located.
     * @param service The service object or a <code>ServiceFactory</code> object.
     * @param properties The properties for this service.
     * @return A <code>ServiceRegistration</code> object for use by the bundle registering the service
     */
    @SuppressWarnings("rawtypes")
    ServiceState registerService(AbstractBundle bundleState, String[] clazzes, Object service, Dictionary properties);

    /**
     * Returns a <code>ServiceReference</code> object for a service that implements and was registered under the specified
     * class.
     * 
     * @param clazz The class name with which the service was registered.
     * @return A <code>ServiceReference</code> object, or <code>null</code>
     */
    ServiceState getServiceReference(AbstractBundle bundleState, String clazz);

    /**
     * Returns an array of <code>ServiceReference</code> objects. The returned array of <code>ServiceReference</code> objects
     * contains services that were registered under the specified class, match the specified filter expression.
     * 
     * If checkAssignable is true, the packages for the class names under which the services were registered match the context
     * bundle's packages as defined in {@link ServiceReference#isAssignableTo(Bundle, String)}.
     * 
     * 
     * @param clazz The class name with which the service was registered or <code>null</code> for all services.
     * @param filter The filter expression or <code>null</code> for all services.
     * @return A potentially empty list of <code>ServiceReference</code> objects.
     */
    List<ServiceState> getServiceReferences(AbstractBundle bundleState, String clazz, String filter, boolean checkAssignable) throws InvalidSyntaxException;

    /**
     * Returns the service object referenced by the specified <code>ServiceReference</code> object.
     * 
     * @param reference A reference to the service.
     * @return A service object for the service associated with <code>reference</code> or <code>null</code>
     */
    Object getService(AbstractBundle bundleState, ServiceState reference);

    /**
     * Releases the service object referenced by the specified <code>ServiceReference</code> object. If the context bundle's use
     * count for the service is zero, this method returns <code>false</code>. Otherwise, the context bundle's use count for the
     * service is decremented by one.
     * 
     * @param reference A reference to the service to be released.
     * @return <code>false</code> if the context bundle's use count for the service is zero or if the service has been
     *         unregistered; <code>true</code> otherwise.
     */
    boolean ungetService(AbstractBundle bundleState, ServiceState reference);

    /**
     * Returns this bundle's <code>ServiceReference</code> list for all services it has registered or <code>null</code> if this
     * bundle has no registered services.
     * 
     * @return A potentially empty list of <code>ServiceReference</code> objects.
     */
    List<ServiceState> getRegisteredServices(AbstractBundle bundleState);

    /**
     * Returns this bundle's <code>ServiceReference</code> list for all services it is using or returns <code>null</code> if
     * this bundle is not using any services. A bundle is considered to be using a service if its use count for that service is
     * greater than zero.
     * 
     * 
     * @return A potentially empty list of <code>ServiceReference</code> objects.
     * @throws IllegalStateException If this bundle has been uninstalled.
     */
    Set<ServiceState> getServicesInUse(AbstractBundle bundleState);

    /**
     * Returns the bundles that are using the service.
     * 
     * @return A set of bundles or an empty set.
     */
    Set<AbstractBundle> getUsingBundles(ServiceState serviceState);

    /**
     * Unregister the given service.
     */
    void unregisterService(ServiceState reference);
}