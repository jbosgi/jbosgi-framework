/*
* JBoss, Home of Professional Open Source
* Copyright 2006, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.msc.metadata;

/**
 * Parameter interface - single value or set of values.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public interface Parameter
{
   /**
    * Get value.
    * Simple string value or collection of string values.
    *
    * @return value
    */
   Object getValue();

   /**
    * Add value.
    * 
    * @param value string to add
    */
   void addValue(String value);

   /**
    * Has multiple values.
    * Used when same name is used for multiple parameter values.
    * Value _can_ be casted into Collection<String>.
    *
    * @return true is returned type is Collection, otherwise false
    */
   boolean isCollection();
}
