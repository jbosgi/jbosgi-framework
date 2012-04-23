/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework;

import java.util.Map;
import java.util.Set;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

/**
 * Integration point for {@link Bundle} management.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Mar-2011
 */
public interface BundleManager extends Service<BundleManager> {

    /**
     * Get a bundle by id
     *
     * Note, this will get the bundle regadless of its state.
     * i.e. The returned bundle may have been UNINSTALLED
     *
     * @param bundleId The identifier of the bundle
     * @return The bundle or null if there is no bundle with that id
     */
    Bundle getBundleById(long bundleId);

    /**
     * Get a bundle by location
     *
     * @param location the location of the bundle
     * @return the bundle or null if there is no bundle with that location
     */
    Bundle getBundleByLocation(String location);

    /**
     * Get the set of bundles that are in one of the given states.
     * If the states pattern is null, it returns all registered bundles.
     *
     * @param states The binary or combination of states or null
     */
    Set<Bundle> getBundles(Integer states);

    /**
     * Get the set of bundles with the given symbolic name and version
     *
     * Note, this will get bundles regadless of their state. i.e. The returned bundles may have been UNINSTALLED
     *
     * @param symbolicName The bundle symbolic name
     * @param versionRange The optional bundle version
     * @return The bundles or an empty list if there is no bundle with that name and version
     */
    Set<Bundle> getBundles(String symbolicName, String versionRange);

    /**
     * Get the system bundle
     * @return the system bundle or null if the framework has not reached the {@link Services#SYSTEM_BUNDLE} state
     */
    Bundle getSystemBundle();

    /**
     * True the framework has reached the {@link Services#FRAMEWORK_ACTIVE} state
     */
    boolean isFrameworkActive();

    /**
     * Install a bundle from the given deployment
     *
     * @param deployment The bundle deployment
     * @param listener An optional listener on the INSTALL service
     * @return The name of the INSTALL service
     */
    ServiceName installBundle(ServiceTarget serviceTarget, Deployment deployment, ServiceListener<Bundle> listener) throws BundleException;

    /**
     * Uninstall the given deployment
     */
    void uninstallBundle(Deployment dep);

    /**
     * Get the service container
     */
    ServiceContainer getServiceContainer();

    /**
     * Returns the framework properties merged with the System properties.
     * @return The effective framework properties in a map
     */
    Map<String, Object> getProperties();

    /**
     * Get a framework property
     * @return The properties value or the given default
     */
    Object getProperty(String key);
}