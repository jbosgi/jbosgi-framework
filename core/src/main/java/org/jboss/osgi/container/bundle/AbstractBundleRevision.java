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
package org.jboss.osgi.container.bundle;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

/**
 * The base of all ordinary {@link Bundle} implementations.
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public abstract class AbstractBundleRevision
{
   private final InternalBundle internalBundle;
   private final OSGiMetaData metadata;
   private final Version version;

   // Cache commonly used plugins
   private ModuleManagerPlugin modulePlugin;

   AbstractBundleRevision(InternalBundle internalBundle, Deployment dep)
   {
      this.internalBundle = internalBundle;
      this.metadata = dep.getAttachment(OSGiMetaData.class);
      this.version = metadata.getBundleVersion();

      if (metadata == null)
         throw new IllegalArgumentException("Null metadata");
   }

   ModuleClassLoader getBundleClassLoader()
   {
      ModuleIdentifier identifier = internalBundle.getModuleIdentifier();
      Module module = getModuleManagerPlugin().getModule(identifier);
      return module != null ? module.getClassLoader() : null;
   }

   public BundleManager getBundleManager()
   {
      return internalBundle.getBundleManager();
   }

   public InternalBundle getInternalBundle()
   {
      return internalBundle;
   }

   ModuleManagerPlugin getModuleManagerPlugin()
   {
      if (modulePlugin == null)
         modulePlugin = getBundleManager().getPlugin(ModuleManagerPlugin.class);
      return modulePlugin;
   }

   OSGiMetaData getOSGiMetaData()
   {
      return metadata;
   }

   Version getVersion()
   {
      return version;
   }

   abstract XModule getResolverModule();

   abstract URL getResource(String name);

   public abstract VirtualFile getRootFile();

   abstract Class<?> loadClass(String name) throws ClassNotFoundException;

   abstract Enumeration<URL> getResources(String name) throws IOException;

   abstract Enumeration<String> getEntryPaths(String path);

   abstract URL getEntry(String path);

   abstract Enumeration<URL> findEntries(String path, String filePattern, boolean recurse);

   abstract URL getLocalizationEntry();
}
