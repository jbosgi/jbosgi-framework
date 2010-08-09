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
package org.jboss.osgi.modules;

import org.jboss.modules.ModuleIdentifier;

/**
 * The metadata that can be defined for a module to take part 
 * on xservice integration, which is the ability for
 * 
 * <ul>
 * <li>an OSGi Bundle to depend on a Module (and vice versa)</li>
 * <li>an OSGi Service to access a MSC Service (and vice versa)</li>
 * </ul>
 * 
 * @author thomas.diesler@jboss.com
 * @since 09-Aug-2010
 */
public interface ModuleMetaData
{
   /**
    * The mandatory module identifier.
    */
   ModuleIdentifier getIdentifier();

   /**
    * The optional {@link ModuleActivator}.
    */
   String getModuleActivator();
   
   /**
    * The optional module dependencies.
    */
   Dependency[] getDependencies();

   /**
    * The optional set of export paths.
    */
   String[] getExportPaths();

   /**
    * A module dependency
    */
   interface Dependency
   {
      /**
       * The mandatory module identifier.
       */
      ModuleIdentifier getIdentifier();
   }
}
