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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes.Name;

import org.jboss.osgi.msc.metadata.internal.AbstractOSGiMetaData;
import org.osgi.framework.Constants;

/**
 * OSGi meta data that can constructed dynamically.
 * 
 * This is needed for deployments that are not backed by a valid OSGi Manifest.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 04-Jun-2010
 */
class DynamicOSGiMetaData extends AbstractOSGiMetaData implements Externalizable
{
   private Map<Name, String> attributes = new LinkedHashMap<Name, String>();
   
   public DynamicOSGiMetaData(String symbolicName)
   {
      addMainAttribute(Constants.BUNDLE_SYMBOLICNAME, symbolicName);
      addMainAttribute(Constants.BUNDLE_MANIFESTVERSION, "2");
   }

   public void addMainAttribute(String key, String value)
   {
      attributes.put(new Name(key), value);
   }
   
   @Override
   public Map<Name, String> getMainAttributes()
   {
      return Collections.unmodifiableMap(attributes);
   }

   @Override
   public String getMainAttribute(String key)
   {
      return getMainAttributes().get(new Name(key));
   }


   @Override
   public void writeExternal(ObjectOutput out) throws IOException
   {
      out.writeObject(attributes);
   }

   @Override
   @SuppressWarnings("unchecked")
   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
   {
      attributes = (Map<Name, String>)in.readObject();
   }
}
