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

import java.util.Set;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleSpec;
import org.jboss.osgi.framework.bundle.AbstractBundle;
import org.jboss.osgi.framework.bundle.AbstractRevision;
import org.jboss.osgi.framework.bundle.ModuleManager;
import org.jboss.osgi.resolver.XModule;

/**
 * The module manager plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 06-Jul-2009
 */
public interface ModuleManagerPlugin extends Plugin
{
   /**
    * Get the set of registered module idetifiers
    */
   Set<ModuleIdentifier> getModuleIdentifiers();

   /**
    * Create the module with the {@link ModuleManager}
    * @return The module identifier
    */
   ModuleSpec createModuleSpec(XModule resModule);

   /**
    * Get the module with the given identifier
    * @return The module or null
    */
   Module getModule(ModuleIdentifier identifier);

   /**
    * Get the bundle revision for the given identifier
    * @return The bundle revision or null
    */
   AbstractRevision getBundleRevision(ModuleIdentifier identifier);

   /**
    * Get the bundle for the given identifier
    * @return The bundle or null
    */
   AbstractBundle getBundleState(ModuleIdentifier identifier);

   /**
    * Load the module for the given identifier
    * @throws ModuleLoadException If the module cannot be loaded
    */
   Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException;

   /**
    * Remove the module with the given identifier
    * @return The module or null
    */
   Module removeModule(ModuleIdentifier identifier);
}