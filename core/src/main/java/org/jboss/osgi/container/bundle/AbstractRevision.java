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

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.container.plugin.ModuleManagerPlugin;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.resolver.XResolverFactory;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * The base class for Bundle Revision implementations. Currently the only subclass is 
 * the {@link HostRevision} class, but once fragments are supported it is expected that 
 * there will also be a <tt>FragmentRevision</tt> subclass.<p/>
 * 
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
public abstract class AbstractRevision implements Revision
{
   static final Logger log = Logger.getLogger(AbstractRevision.class);

   // Ordinary revision IDs start at 1, as 0 is the System Bundle Revision ID.
   private static final AtomicInteger globalRevisionCounter = new AtomicInteger(1);

   private final int globalRevisionId = globalRevisionCounter.getAndIncrement();
   
   private final Deployment deployment;
   private final DeploymentBundle bundleState;
   private final XModule resolverModule;
   private final OSGiMetaData metadata;
   private final int revisionId;
   private final Version version;

   // Cache commonly used plugins
   private final ModuleManagerPlugin moduleManager;
   
   AbstractRevision(DeploymentBundle bundleState, Deployment deployment, int revisionId) throws BundleException
   {
      this.bundleState = bundleState;
      this.deployment = deployment;
      this.revisionId = revisionId;
      
      this.metadata = deployment.getAttachment(OSGiMetaData.class);
      if (metadata == null)
         throw new IllegalArgumentException("Null metadata");
      
      // Create the resolver module
      XModuleBuilder builder = XResolverFactory.getModuleBuilder();
      resolverModule = builder.createModule(getGlobalRevisionId(), getOSGiMetaData());
      resolverModule.addAttachment(AbstractRevision.class, this);
      resolverModule.addAttachment(Bundle.class, bundleState);
      
      this.version = metadata.getBundleVersion();

      this.moduleManager = getBundleManager().getPlugin(ModuleManagerPlugin.class);
   }

   @Override
   public int getGlobalRevisionId()
   {
      return globalRevisionId;
   }

   @Override
   public int getRevisionId()
   {
      return revisionId;
   }

   @Override
   public XModule getResolverModule()
   {
      return resolverModule;
   }

   ModuleClassLoader getModuleClassLoader()
   {
      ModuleIdentifier identifier = bundleState.getModuleIdentifier();
      Module module = moduleManager.getModule(identifier);
      return module != null ? module.getClassLoader() : null;
   }
   
   BundleManager getBundleManager()
   {
      return bundleState.getBundleManager();
   }

   DeploymentBundle getBundleState()
   {
      return bundleState;
   }

   Deployment getDeployment()
   {
      return deployment;
   }
   
   String getLocation()
   {
      return deployment.getLocation();
   }

   VirtualFile getContentRoot()
   {
      return deployment.getRoot();
   }

   OSGiMetaData getOSGiMetaData()
   {
      return metadata;
   }

   Version getVersion()
   {
      return version;
   }

   abstract Class<?> loadClass(String name) throws ClassNotFoundException;

   abstract URL getResource(String name);

   abstract Enumeration<URL> getResources(String name) throws IOException;

   Enumeration<String> getEntryPaths(String path)
   {
      getBundleState().assertNotUninstalled();
      try
      {
         return getContentRoot().getEntryPaths(path);
      }
      catch (IOException ex)
      {
         return null;
      }
   }

   URL getEntry(String path)
   {
      getBundleState().assertNotUninstalled();
      try
      {
         VirtualFile child = getContentRoot().getChild(path);
         return child != null ? child.toURL() : null;
      }
      catch (IOException ex)
      {
         log.error("Cannot get entry: " + path, ex);
         return null;
      }
   }

   Enumeration<URL> findEntries(String path, String pattern, boolean recurse)
   {
      getBundleState().assertNotUninstalled();
      try
      {
         return getContentRoot().findEntries(path, pattern, recurse);
      }
      catch (IOException ex)
      {
         return null;
      }
   }

   URL getLocalizationEntry()
   {
      return null;
   }
}
