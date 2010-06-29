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

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Constants;

/**
 * OSGi meta data that can constructed dynamically.
 * 
 * @author Thomas.Diesler@jboss.com
 * @since 04-Jun-2010
 */
public class OSGiMetaDataBuilder
{
   private DynamicOSGiMetaData metadata;
   private List<String> importPackages = new ArrayList<String>();
   private List<String> exportPackages = new ArrayList<String>();
   private List<String> dynamicImportPackages = new ArrayList<String>();

   public static OSGiMetaDataBuilder createBuilder(String symbolicName)
   {
      return new OSGiMetaDataBuilder(symbolicName);
   }

   private OSGiMetaDataBuilder(String symbolicName)
   {
      metadata = new DynamicOSGiMetaData(symbolicName);
   }

   public OSGiMetaDataBuilder setBundleManifestVersion(int version)
   {
      metadata.addMainAttribute(Constants.BUNDLE_MANIFESTVERSION, "" + version);
      return this;
   }

   public OSGiMetaDataBuilder setBundleActivator(String value)
   {
      metadata.addMainAttribute(Constants.BUNDLE_ACTIVATOR, value);
      return this;
   }

   public OSGiMetaDataBuilder addImportPackages(Class<?>... packages)
   {
      for (Class<?> aux : packages)
         addImportPackages(aux.getPackage().getName());

      return this;
   }

   public OSGiMetaDataBuilder addImportPackages(String... packages)
   {
      for (String aux : packages)
         importPackages.add(aux);

      return this;
   }

   public OSGiMetaDataBuilder addExportPackages(Class<?>... packages)
   {
      for (Class<?> aux : packages)
         addExportPackages(aux.getPackage().getName());

      return this;
   }

   public OSGiMetaDataBuilder addExportPackages(String... packages)
   {
      for (String aux : packages)
         exportPackages.add(aux);

      return this;
   }

   public OSGiMetaDataBuilder addDynamicImportPackages(Class<?>... packages)
   {
      for (Class<?> aux : packages)
         addDynamicImportPackages(aux.getPackage().getName());

      return this;
   }

   public OSGiMetaDataBuilder addDynamicImportPackages(String... packages)
   {
      for (String aux : packages)
         dynamicImportPackages.add(aux);

      return this;
   }

   public OSGiMetaDataBuilder addMainAttribute(String key, String value)
   {
      metadata.addMainAttribute(key, value);
      return this;
   }

   public OSGiMetaData getOSGiMetaData()
   {
      // Export-Package
      if (exportPackages.size() > 0)
      {
         StringBuffer value = new StringBuffer();
         for (int i = 0; i < exportPackages.size(); i++)
         {
            value.append(i > 0 ? "," : "");
            value.append(exportPackages.get(i));
         }
         metadata.addMainAttribute(Constants.EXPORT_PACKAGE, value.toString());
      }

      // Import-Package
      if (importPackages.size() > 0)
      {
         StringBuffer value = new StringBuffer();
         for (int i = 0; i < importPackages.size(); i++)
         {
            value.append(i > 0 ? "," : "");
            value.append(importPackages.get(i));
         }
         metadata.addMainAttribute(Constants.IMPORT_PACKAGE, value.toString());
      }

      // DynamicImport-Package
      if (dynamicImportPackages.size() > 0)
      {
         StringBuffer value = new StringBuffer();
         for (int i = 0; i < dynamicImportPackages.size(); i++)
         {
            value.append(i > 0 ? "," : "");
            value.append(dynamicImportPackages.get(i));
         }
         metadata.addMainAttribute(Constants.DYNAMICIMPORT_PACKAGE, value.toString());
      }
      return metadata;
   }
}
