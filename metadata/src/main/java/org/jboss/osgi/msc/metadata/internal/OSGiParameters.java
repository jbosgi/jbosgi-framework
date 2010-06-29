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

import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE;
import static org.osgi.framework.Constants.BUNDLE_VERSION_ATTRIBUTE;
import static org.osgi.framework.Constants.RESOLUTION_DIRECTIVE;
import static org.osgi.framework.Constants.RESOLUTION_MANDATORY;
import static org.osgi.framework.Constants.VERSION_ATTRIBUTE;
import static org.osgi.framework.Constants.VISIBILITY_DIRECTIVE;
import static org.osgi.framework.Constants.VISIBILITY_PRIVATE;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.jboss.osgi.msc.metadata.Parameter;
import org.jboss.osgi.msc.metadata.VersionRange;

/**
 * OSGi parameter values.
 * Util for transforming parameter info to actual useful values.
 *
 * @author <a href="mailto:ales.justin@jboss.com">Ales Justin</a>
 */
public class OSGiParameters
{
   protected static Logger log = Logger.getLogger(OSGiParameters.class);

   protected Map<String, Parameter> parameters;
   protected Map<String, Object> cachedAttributes;

   public OSGiParameters(Map<String, Parameter> parameters)
   {
      this.parameters = Collections.unmodifiableMap(parameters);
      this.cachedAttributes = new ConcurrentHashMap<String, Object>();
   }

   protected Map<String, Parameter> getParameters()
   {
      return parameters;
   }

   public VersionRange getVersion()
   {
      return get(VERSION_ATTRIBUTE, ValueCreatorUtil.VERSION_RANGE_VC);
   }

   public String getBundleSymbolicName()
   {
      return get(BUNDLE_SYMBOLICNAME_ATTRIBUTE, ValueCreatorUtil.STRING_VC);
   }

   public VersionRange getBundleVersion()
   {
      return get(BUNDLE_VERSION_ATTRIBUTE, ValueCreatorUtil.VERSION_RANGE_VC);
   }

   public String getVisibility()
   {
      return get(VISIBILITY_DIRECTIVE, ValueCreatorUtil.STRING_VC, VISIBILITY_PRIVATE);
   }

   public String getResolution()
   {
      return get(RESOLUTION_DIRECTIVE, ValueCreatorUtil.STRING_VC, RESOLUTION_MANDATORY);
   }

   protected <T> T get(String key, ValueCreator<T> creator)
   {
      return get(key, creator, null);
   }

   @SuppressWarnings({ "unchecked" })
   protected <T> T get(String key, ValueCreator<T> creator, T defaultValue)
   {
      T value = (T)cachedAttributes.get(key);
      if (value == null)
      {
         Parameter parameter = parameters.get(key);
         if (parameter != null)
         {
            Object paramValue = parameter.getValue();
            if(parameter.isCollection())
            {
               if (creator instanceof CollectionValueCreator)
               {
                  CollectionValueCreator<T> cvc = (CollectionValueCreator<T>)creator;
                  value = cvc.createValue((Collection<String>)paramValue);
               }
               else
               {
                  log.warn("Unable to create proper value from " + creator + " for parameter: " + parameter);
               }
            }
            else
            {
               value = creator.createValue(paramValue.toString());
            }
         }
         else if (defaultValue != null)
         {
            value = defaultValue;
         }
         if (value != null)
            cachedAttributes.put(key, value);
      }
      return value;
   }
}
