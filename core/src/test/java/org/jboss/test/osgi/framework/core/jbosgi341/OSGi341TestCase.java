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
package org.jboss.test.osgi.framework.core.jbosgi341;

// $Id: $

import java.io.InputStream;

import org.jboss.osgi.testing.OSGiFrameworkTest;
import org.jboss.osgi.testing.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archives;
import org.jboss.shrinkwrap.api.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.event.EventAdmin;

/**
 * [JBOSGI-341] Endless loop at AS server startup
 *
 * https://jira.jboss.org/jira/browse/JBOSGI-341
 * 
 * @author thomas.diesler@jboss.com
 * @since 10-Jun-2010
 */
public class OSGi341TestCase extends OSGiFrameworkTest
{
   @After
   public void tearDown() throws Exception
   {
      shutdownFramework();
      super.tearDown();
   }

   @Test
   public void testCompendiumFirst() throws Exception
   {
      Bundle compendium = installCompendium();
      Bundle eventadmin = installEventAdmin();
      
      try
      {
         eventadmin.start();
         assertBundleState(Bundle.ACTIVE, eventadmin.getState());
         assertBundleState(Bundle.RESOLVED, compendium.getState());
      }
      finally
      {
         compendium.uninstall();
         eventadmin.uninstall();
      }
   }

   @Test
   public void testCompendiumLast() throws Exception
   {
      Bundle eventadmin = installEventAdmin();
      Bundle compendium = installCompendium();
      
      try
      {
         eventadmin.start();
         assertBundleState(Bundle.ACTIVE, eventadmin.getState());
         assertBundleState(Bundle.RESOLVED, compendium.getState());
      }
      finally
      {
         compendium.uninstall();
         eventadmin.uninstall();
      }
   }

   private Bundle installCompendium() throws Exception
   {
      final JavaArchive archive = Archives.create("jbosgi341-compendium", JavaArchive.class);
      archive.addClass(ConfigurationAdmin.class);
      archive.addClass(EventAdmin.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(ConfigurationAdmin.class, EventAdmin.class);
            builder.addImportPackages(ConfigurationAdmin.class.getPackage().getName() + ";resolution:=optional");
            builder.addImportPackages(EventAdmin.class.getPackage().getName() + ";resolution:=optional");
            builder.addDynamicImportPackages("*");
            return builder.openStream();
         }
      });
      
      return installBundle(archive);
   }


   private Bundle installEventAdmin() throws Exception
   {
      final JavaArchive archive = Archives.create("jbosgi341-eventadmin", JavaArchive.class);
      archive.addClass(EventAdmin.class);
      archive.setManifest(new Asset()
      {
         public InputStream openStream()
         {
            OSGiManifestBuilder builder = OSGiManifestBuilder.newInstance();
            builder.addBundleManifestVersion(2);
            builder.addBundleSymbolicName(archive.getName());
            builder.addExportPackages(EventAdmin.class);
            builder.addImportPackages(ConfigurationAdmin.class.getPackage().getName() + ";resolution:=optional");
            builder.addImportPackages(EventAdmin.class.getPackage().getName());
            builder.addDynamicImportPackages("org.osgi.service.log");
            return builder.openStream();
         }
      });
      
      return installBundle(archive);
   }
}
