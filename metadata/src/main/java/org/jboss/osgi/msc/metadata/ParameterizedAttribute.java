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

import java.util.Map;

/**
 * Attribute with parameters.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 */
public interface ParameterizedAttribute extends AttributeAware
{
   /**
    * Get the attributes.
    *
    * @return the attributes or an empty map
    */
   Map<String, Parameter> getAttributes();

   /**
    * Get an attribute by name.
    *
    * @param name attributes's name
    * @return the attribute
    */
   Parameter getAttribute(String name);

   /**
    * Get an attribute value
    * 
    * @param <T> the expected type
    * @param name the name of the attribute
    * @param type the expected type
    * @return the attribute value
    */
   <T> T getAttributeValue(String name, Class<T> type);

   /**
    * Get a directive value
    * 
    * @param <T> the expected type
    * @param name the name of the directive
    * @param defaultValue the default value when no attribute is specified
    * @param type the expected type
    * @return the attribute value
    */
   <T> T getAttributeValue(String name, T defaultValue, Class<T> type);
   
   /**
    * Get the declerations
    *
    * @return the directives or an empty map
    */
   Map<String, Parameter> getDirectives();

   /**
    * Get a directive by name.
    *
    * @param name directive's name
    * @return the directive
    */
   Parameter getDirective(String name);

   /**
    * Get a directive value
    * 
    * @param <T> the expected type
    * @param name the name of the directive
    * @param type the expected type
    * @return the directive value
    */
   <T> T getDirectiveValue(String name, Class<T> type);

   /**
    * Get a directive value
    * 
    * @param <T> the expected type
    * @param name the name of the directive
    * @param defaultValue the default value when no directive is specified
    * @param type the expected type
    * @return the directive value
    */
   <T> T getDirectiveValue(String name, T defaultValue, Class<T> type);
}
