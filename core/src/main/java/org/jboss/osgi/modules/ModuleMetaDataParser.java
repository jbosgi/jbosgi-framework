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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.modules.ModuleMetaData.Dependency;

/**
 * A properties based parser for {@link ModuleMetaData}.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 09-Aug-2010
 */
public final class ModuleMetaDataParser
{
   public static final String MODULE_IDENTIFIER = "Module-Identifier";
   public static final String MODULE_ACTIVATOR = "Module-Activator";
   public static final String MODULE_DEPENDENCIES = "Module-Dependencies";
   public static final String MODULE_EXPORTS = "Module-Exports";

   public ModuleMetaData parse(Reader reader) throws IOException
   {
      Properties props = new Properties();
      props.load(reader);
      return parse(props);
   }

   public ModuleMetaData parse(Properties props)
   {
      if (props == null)
         throw new IllegalArgumentException("Null props");

      String idprop = props.getProperty(MODULE_IDENTIFIER);
      ModuleIdentifier identifier = ModuleIdentifier.fromString(idprop);
      ModuleMetaDataImpl moduleMetaData = new ModuleMetaDataImpl(identifier);

      String moduleActivator = props.getProperty(MODULE_ACTIVATOR);
      if (moduleActivator != null)
         moduleMetaData.setModuleActivator(moduleActivator);

      String depsProp = props.getProperty(MODULE_DEPENDENCIES);
      if (depsProp != null)
      {
         List<Dependency> depList = new ArrayList<Dependency>();
         String[] depDefs = depsProp.split(",");
         for (String depDef : depDefs)
         {
            String[] depParts = depDef.split(" ");
            if (depParts.length == 0)
               throw new RuntimeException("Invalid dependency: " + depsProp);

            ModuleIdentifier dependencyId = ModuleIdentifier.fromString(depParts[0]);
            depList.add(new DependencyImpl(dependencyId));
         }
         moduleMetaData.setDependencies(depList.toArray(new Dependency[depList.size()]));
      }

      String pathsProp = props.getProperty(MODULE_EXPORTS);
      if (pathsProp != null)
      {
         String[] exportPaths = pathsProp.split(",");
         moduleMetaData.setExportPaths(exportPaths);
      }

      return moduleMetaData;
   }

   static class ModuleMetaDataImpl implements ModuleMetaData
   {
      private ModuleIdentifier identifier;
      private String moduleActivator;
      private Dependency[] dependencies;
      private String[] exportPaths;

      public ModuleMetaDataImpl(ModuleIdentifier identifier)
      {
         if (identifier == null)
            throw new IllegalArgumentException("Null identifier");
         this.identifier = identifier;
      }

      @Override
      public ModuleIdentifier getIdentifier()
      {
         return identifier;
      }

      @Override
      public String getModuleActivator()
      {
         return moduleActivator;
      }

      void setModuleActivator(String moduleActivator)
      {
         this.moduleActivator = moduleActivator;
      }

      @Override
      public Dependency[] getDependencies()
      {
         return dependencies;
      }

      void setDependencies(Dependency[] dependencies)
      {
         this.dependencies = dependencies;
      }

      @Override
      public String[] getExportPaths()
      {
         return exportPaths;
      }

      void setExportPaths(String[] exportPaths)
      {
         this.exportPaths = exportPaths;
      }
   }

   static class DependencyImpl implements Dependency
   {
      private final ModuleIdentifier identifier;

      public DependencyImpl(ModuleIdentifier identifier)
      {
         if (identifier == null)
            throw new IllegalArgumentException("Null identifier");
         this.identifier = identifier;
      }

      @Override
      public ModuleIdentifier getIdentifier()
      {
         return identifier;
      }
   }
}
