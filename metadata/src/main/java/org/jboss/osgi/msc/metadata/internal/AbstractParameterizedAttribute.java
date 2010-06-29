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
package org.jboss.osgi.msc.metadata.internal;

import java.util.Collections;
import java.util.Map;

import org.jboss.osgi.msc.metadata.Parameter;
import org.jboss.osgi.msc.metadata.ParameterizedAttribute;

/**
 * Parameter attribute impl.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public class AbstractParameterizedAttribute extends AbstractAttributeAware implements ParameterizedAttribute
{
   private static final long serialVersionUID = 1l;
   
   protected Map<String, Parameter> attributes;
   
   protected Map<String, Parameter> directives;

   public AbstractParameterizedAttribute(String attribute, Map<String, Parameter> attributes, Map<String, Parameter> directives)
   {
      super(attribute);
      if (attributes == null)
         attributes = Collections.emptyMap();
      this.attributes = attributes;
      if (directives == null)
         directives = Collections.emptyMap(); 
      this.directives = directives;
   }

   public Map<String, Parameter> getAttributes()
   {
      return attributes;
   }

   public Parameter getAttribute(String name)
   {
      return attributes.get(name);
   }

   public <T> T getAttributeValue(String name, Class<T> type)
   {
      return getAttributeValue(name, null, type);
   }

   public <T> T getAttributeValue(String name, T defaultValue, Class<T> type)
   {
      Parameter parameter = getAttribute(name);
      if (parameter == null)
         return defaultValue;
      if (parameter.isCollection())
         throw new IllegalArgumentException("Duplicate " + name + " attribute.");
      Object value = parameter.getValue();
      if (value == null)
         return defaultValue;
      return type.cast(value);
   }

   public Map<String, Parameter> getDirectives()
   {
      return directives;
   }

   public Parameter getDirective(String name)
   {
      return directives.get(name);
   }

   public <T> T getDirectiveValue(String name, Class<T> type)
   {
      return getDirectiveValue(name, null, type);
   }

   public <T> T getDirectiveValue(String name, T defaultValue, Class<T> type)
   {
      Parameter parameter = getDirective(name);
      if (parameter == null)
         return defaultValue;
      if (parameter.isCollection())
         throw new IllegalArgumentException("Duplicate " + name + " directive.");
      Object value = parameter.getValue();
      if (value == null)
         return defaultValue;
      return type.cast(value);
   }
}
