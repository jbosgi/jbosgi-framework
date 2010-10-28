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

import java.util.List;
import java.util.Set;

/**
 * A plugin that provides the configured list of system packages.
 *
 * @author thomas.diesler@jboss.com
 * @since 24-Aug-2009
 */
public interface SystemPackagesPlugin extends Plugin
{
   /**
    * Get the list of defined boot delegation packages
    *
    * @return The list of defined system packages
    */
   List<String> getBootDelegationPackages();

   /**
    * Return whether the given package name is a boot delegation package.
    *
    * @param name The package name
    * @return True if the given package name is a boot delegation package.
    */
   boolean isBootDelegationPackage(String name);

   /**
    * Get the list of defined system packages
    *
    * @return The list of defined system packages
    */
   List<String> getSystemPackages();

   /**
    * Return whether the given package name is a system package.
    *
    * @param name The package name with optional version qualifier
    * @return True if the given package name is system package.
    */
   boolean isSystemPackage(String name);

   /**
    * Get the list of packages provided by the framework
    *
    * @return The list of framework provided packages
    */
   List<String> getFrameworkPackages();

   /**
    * Return whether the given package name is a framework package.
    *
    * @param name The package name with optional version qualifier
    * @return True if the given package name is system package.
    */
   boolean isFrameworkPackage(String name);

   /**
    * Get the list of paths that the framework exports
    *
    * @return The list of framework exported paths
    */
   Set<String> getExportedPaths();
}