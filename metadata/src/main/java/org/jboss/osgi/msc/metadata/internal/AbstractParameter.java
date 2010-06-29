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

import java.util.Collection;
import java.util.HashSet;

import org.jboss.osgi.msc.metadata.Parameter;

/**
 * Parameter impl.
 * It uses [Hash]Set to hold the values.
 * So duplicate values (by hash) will be ignored.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public class AbstractParameter implements Parameter
{
   protected Collection<String> values;

   public AbstractParameter()
   {
      super();
      values = new HashSet<String>();
   }

   public AbstractParameter(String parameter)
   {
      this();
      addValue(parameter);
   }

   public void addValue(String value)
   {
      values.add(value);
   }

   public Object getValue()
   {
      if (values.isEmpty())
         return null;
      else if (values.size() == 1)
         return values.iterator().next();
      else
         return values;
   }

   public boolean isCollection()
   {
      return values.size() > 1;
   }
}
