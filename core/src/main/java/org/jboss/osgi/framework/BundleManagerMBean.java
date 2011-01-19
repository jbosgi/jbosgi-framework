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

import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.osgi.framework.BundleException;

/**
 * The proprietary extension to Bundle management.
 *
 * [TODO] Remove when Arquillian supports XService tests
 *
 * @author thomas.diesler@jboss.com
 * @since 10-Nov-2010
 */
public interface BundleManagerMBean {

    /** The default object name: jboss.osgi:service=jmx,type=BundleManager */
    String OBJECT_NAME = "jboss.osgi:service=jmx,type=BundleManager";

    /**
     * Register the module with the given identifier with the OSGi layer
     * @return The bundle id that corresponds to the registered module
     */
    long installBundle(ModuleIdentifier identifier) throws BundleException, ModuleLoadException;
}
