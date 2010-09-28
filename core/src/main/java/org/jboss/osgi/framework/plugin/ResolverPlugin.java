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

// $Id$

import java.util.Set;

import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XModuleIdentity;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.BundleException;

/**
 * The resolver plugin.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2009
 */
public interface ResolverPlugin extends Plugin
{
   /**
    * Get the resolver instance
    */
   XResolver getResolver();

   /**
    * Get a new module builder instance
    */
   XModuleBuilder getModuleBuilder();

   /**
    * Add a module to the resolver.
    *
    * @param module the resolver module
    */
   void addModule(XModule module);

   /**
    * Remove a module from the resolver.
    *
    * @param module the resolver module
    */
   void removeModule(XModule module);

   /**
    * Get the module for the given id
    * @return The module or null
    */
   XModule getModuleById(XModuleIdentity moduleId);
   
   /**
    * Resolve the given modules.
    *
    * @param module the module to resolve
    * @return The set of resolved modules or an empty set
    * @throws BundleException If the resolver could not resolve the module
    */
   void resolve(XModule module) throws BundleException;

   /**
    * Resolve the given set of modules.
    *
    * @param modules the modules to resolve
    * @return True if all modules could be resolved
    */
   boolean resolveAll(Set<XModule> modules);
}