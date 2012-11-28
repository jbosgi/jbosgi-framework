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

import java.util.List;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.wiring.BundleWire;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
public interface ModuleManager {

    /**
     * Get the module with the given identifier
     * @return The module or null
     */
    Module getModule(ModuleIdentifier identifier);

    /**
     * Get the bundle for the given class
     * @return The bundle or null
     */
    XBundle getBundleState(Class<?> clazz);

    /**
     * Load the module for the given identifier
     * @throws ModuleLoadException If the module cannot be loaded
     */
    Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException;

    /**
     * Remove the module with the given identifier
     */
    void removeModule(XBundleRevision brev, ModuleIdentifier identifier);

    Module getFrameworkModule();

    ModuleIdentifier getModuleIdentifier(XBundleRevision brev);

    ModuleIdentifier addModule(XBundleRevision brev, List<BundleWire> wires);
}