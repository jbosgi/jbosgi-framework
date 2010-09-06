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

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.container.plugin.ResolverPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * An abstract bundle revision. 
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public abstract class AbstractRevision
{
   static final Logger log = Logger.getLogger(AbstractRevision.class);

   private final int revisionCount;
   private final AbstractBundle bundleState;
   private final OSGiMetaData metadata;
   private XModule resolverModule;

   // Cache commonly used plugins
   private final ModuleManagerPlugin moduleManager;
   private final ResolverPlugin resolverPlugin;
   
   AbstractRevision(AbstractBundle bundleState, OSGiMetaData metadata, int revisionCount) throws BundleException
   {
      if (bundleState == null)
         throw new IllegalArgumentException("Null bundleState");
      if (metadata == null)
         throw new IllegalArgumentException("Null metadata");
      
      this.bundleState = bundleState;
      this.metadata = metadata;
      this.revisionCount = revisionCount;
      
      this.moduleManager = getBundleManager().getPlugin(ModuleManagerPlugin.class);
      this.resolverPlugin = getBundleManager().getPlugin(ResolverPlugin.class);
      
      // Create the resolver module
      refreshRevision(bundleState, metadata);
   }

   void refreshRevision(AbstractBundle bundleState, OSGiMetaData metadata) throws BundleException
   {
      boolean systemRev = (bundleState.getBundleId() == 0);
      int moduleId = systemRev ? 0 : resolverPlugin.createModuleId();
      
      XModuleBuilder builder = XResolverFactory.getModuleBuilder();
      resolverModule = builder.createModule(moduleId, metadata);
      resolverModule.addAttachment(AbstractRevision.class, this);
      resolverModule.addAttachment(Bundle.class, bundleState);
      refreshRevisionInternal(resolverModule);
   }

   abstract void refreshRevisionInternal(XModule resModule);

   public int getRevisionCount()
   {
      return revisionCount;
   }

   public XModule getResolverModule()
   {
      return resolverModule;
   }

   public AbstractBundle getBundleState()
   {
      return bundleState;
   }

   public ModuleIdentifier getModuleIdentifier()
   {
      return ModuleManager.getModuleIdentifier(getResolverModule());
   }

   public ModuleClassLoader getModuleClassLoader()
   {
      ModuleIdentifier identifier = getModuleIdentifier();
      Module module = moduleManager.getModule(identifier);
      return module != null ? module.getClassLoader() : null;
   }
   
   BundleManager getBundleManager()
   {
      return bundleState.getBundleManager();
   }

   OSGiMetaData getOSGiMetaData()
   {
      return metadata;
   }

   String getSymbolicName()
   {
      return bundleState.getSymbolicName();
   }
   
   Version getVersion()
   {
      return metadata.getBundleVersion();
   }

   abstract Class<?> loadClass(String name) throws ClassNotFoundException;

   abstract URL getResource(String name);

   abstract Enumeration<URL> getResources(String name) throws IOException;

   abstract Enumeration<String> getEntryPaths(String path);

   abstract URL getEntry(String path);

   abstract Enumeration<URL> findEntries(String path, String pattern, boolean recurse);

   abstract String getLocation();
   
   abstract URL getLocalizationEntry(String path);

   @Override
   public String toString()
   {
      return "Revision[" + getSymbolicName() + ":" + getVersion() + ":rev-" + revisionCount + "]";
   }
}
