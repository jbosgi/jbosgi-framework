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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Version;

/**
 * The base class for Bundle Revision implementations. Currently the only subclass is 
 * the {@link BundleRevision} class, but once fragments are supported it is expected that 
 * there will also be a <tt>FragmentRevision</tt> subclass.<p/>
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public abstract class AbstractBundleRevision implements Revision
{
   // Ordinary revision IDs start at 1, as 0 is the System Bundle Revision ID.
   private static final AtomicInteger revisionIDCounter = new AtomicInteger(1);

   private final InternalBundle internalBundle;
   private final OSGiMetaData metadata;
   private final int id = revisionIDCounter.getAndIncrement();
   // The revision increases every time a bundle gets updated
   private final int revision;
   private final Version version;

   // Cache commonly used plugins
   private ModuleManagerPlugin modulePlugin;

   AbstractBundleRevision(InternalBundle internalBundle, Deployment dep, int revision)
   {
      this.internalBundle = internalBundle;
      this.metadata = dep.getAttachment(OSGiMetaData.class);
      this.revision = revision;
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

   @Override
   public int getRevisionID()
   {
      return id;
   }

   @Override
   public int getRevision()
   {
      return revision;
   }

   public Version getVersion()
   {
      return version;
   }

   abstract URL getResource(String name);

   abstract List<VirtualFile> getContentRoots();

   abstract Class<?> loadClass(String name) throws ClassNotFoundException;

   abstract Enumeration<URL> getResources(String name) throws IOException;

   abstract Enumeration<String> getEntryPaths(String path);

   abstract URL getEntry(String path);

   abstract Enumeration<URL> findEntries(String path, String filePattern, boolean recurse);

   abstract URL getLocalizationEntry();
}
