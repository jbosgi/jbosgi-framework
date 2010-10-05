/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
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
package org.jboss.osgi.framework.plugin.internal;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.BundleDeploymentPlugin;
import org.jboss.osgi.framework.plugin.ResolverPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XModuleBuilder;
import org.jboss.osgi.spi.util.BundleInfo;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * A plugin the handles Bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Jul-2010
 */
public class BundleDeploymentPluginImpl extends AbstractPlugin implements BundleDeploymentPlugin
{
   // Provide logging
   private static final Logger log = Logger.getLogger(BundleDeploymentPluginImpl.class);

   public BundleDeploymentPluginImpl(BundleManager bundleManager)
   {
      super(bundleManager);
   }

   /**
    * Create a {@link Deployment} from the given virtual file.
    * @param rootFile The root file pointing to one of the supported bundle formats
    * @param location The bundle location to be associated with the deployment
    * @throws BundleException If the given root file does not
    */
   public Deployment createDeployment(VirtualFile rootFile, String location) throws BundleException
   {
      if (rootFile == null)
         throw new IllegalArgumentException("Null rootFile");

      // Check if we have a valid OSGi Manifest
      try
      {
         BundleInfo info = BundleInfo.createBundleInfo(rootFile, location);
         Deployment dep = DeploymentFactory.createDeployment(info);
         OSGiMetaData metadata = toOSGiMetaData(dep, info);
         dep.addAttachment(OSGiMetaData.class, metadata);
         dep.addAttachment(BundleInfo.class, info);
         return dep;
      }
      catch (NumberFormatException nfe)
      {
         throw new BundleException("Invalid OSGi version:", nfe);
      }
      catch (BundleException ex)
      {
         log.debugf("Not a valid OSGi manifest: %s", ex.getMessage());
      }

      // Check if we have META-INF/jbosgi-xservice.properties
      OSGiMetaData metadata = getXServiceMetaData(rootFile);
      if (metadata != null)
      {
         String symbolicName = metadata.getBundleSymbolicName();
         Version version = metadata.getBundleVersion();
         Deployment dep = DeploymentFactory.createDeployment(rootFile, location, symbolicName, version);
         dep.addAttachment(OSGiMetaData.class, metadata);
         return dep;
      }

      throw new BundleException("Cannot create deployment from: " + rootFile);
   }

   @Override
   public Deployment createDeployment(ModuleIdentifier identifier) throws BundleException
   {
      if (identifier == null)
         throw new IllegalArgumentException("Null identifier");

      // Check if we have a single root file
      VirtualFile rootFile = getModuleRepositoryEntry(identifier);
      if (rootFile != null)
      {
         try
         {
            // Check if this is a valid OSGi deployment
            String location = identifier.getName() + ":" + identifier.getSlot();
            return createDeployment(rootFile, location);
         }
         catch (BundleException ex)
         {
            // Ignore, the rootFile is not a valid deployment
         }
      }

      // Check if the module can be loaded
      Module module;
      try
      {
         ModuleLoader loader = Module.getCurrentLoader();
         module = loader.loadModule(identifier);
      }
      catch (ModuleLoadException ex)
      {
         throw new BundleException("Cannot load module: " + identifier, ex);
      }

      // Do a sanity check that this is not an OSGi bundle
      ModuleClassLoader classLoader = module.getClassLoader();
      InputStream inStream = classLoader.getResourceAsStream(JarFile.MANIFEST_NAME);
      if (inStream != null)
      {
         try
         {
            Manifest manifest = new Manifest();
            manifest.read(inStream);
            if (BundleInfo.isValidateBundleManifest(manifest))
               throw new BundleException("Cannot install bundle from loaded module: " + identifier);
         }
         catch (IOException ex)
         {
            throw new BundleException("Cannot read mainfest from: " + identifier, ex);
         }
      }

      String symbolicName = identifier.getName();
      Version version;
      try
      {
         version = Version.parseVersion(identifier.getSlot());
      }
      catch (IllegalArgumentException ex)
      {
         version = Version.emptyVersion;
      }

      // Build the resolver capabilities, which exports every package
      ResolverPlugin resolverPlugin = getPlugin(ResolverPlugin.class);
      XModuleBuilder builder = resolverPlugin.getModuleBuilder();
      builder.createModule(symbolicName, version, 0);
      builder.addBundleCapability(symbolicName, version);
      for (String path : module.getExportedPaths())
      {
         if (path.startsWith("/"))
            path = path.substring(1);
         if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
         if (path.startsWith("META-INF"))
            continue;

         String packageName = path.replace('/', '.');
         builder.addPackageCapability(packageName, null, null);
      }
      XModule resModule = builder.getModule();
      resModule.addAttachment(Module.class, module);

      String location = identifier.getName() + ":" + identifier.getSlot();
      Deployment dep = DeploymentFactory.createDeployment(location, symbolicName, version);
      dep.addAttachment(XModule.class, resModule);
      dep.addAttachment(Module.class, module);
      return dep;
   }

   /**
    * Get virtual file for the singe jar that corresponds to the given identifier
    * @return The root virtual or null
    */
   private VirtualFile getModuleRepositoryEntry(ModuleIdentifier identifier)
   {
      File rootPath = new File(getBundleManager().getProperty("module.path").toString());
      String identifierPath = identifier.getName().replace('.', File.separatorChar) + File.separator + identifier.getSlot();
      File moduleDir = new File(rootPath + File.separator + identifierPath);
      if (moduleDir.isDirectory() == false)
      {
         log.warnf("Cannot obtain module directory: %s", moduleDir);
         return null;
      }

      String[] files = moduleDir.list(new FilenameFilter()
      {
         @Override
         public boolean accept(File dir, String name)
         {
            return name.endsWith(".jar");
         }
      });
      if (files.length == 0)
      {
         log.warnf("Cannot find module jar in: %s", moduleDir);
         return null;
      }
      if (files.length > 1)
      {
         log.warnf("Multiple module jars in: %s", moduleDir);
         return null;
      }

      File moduleFile = new File(moduleDir + File.separator + files[0]);
      if (moduleFile.exists() == false)
      {
         log.warnf("Module file does not exist: %s", moduleFile);
         return null;
      }

      try
      {
         return AbstractVFS.toVirtualFile(moduleFile.toURI().toURL());
      }
      catch (IOException ex)
      {
         log.errorf(ex, "Cannot obtain root file: %s", moduleFile);
         return null;
      }
   }

   @Override
   public OSGiMetaData createOSGiMetaData(Deployment dep) throws BundleException
   {
      // #1 check if the Deployment already contains a OSGiMetaData
      OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
      if (metadata != null)
         return metadata;

      // #2 check if the Deployment contains valid BundleInfo
      BundleInfo info = dep.getAttachment(BundleInfo.class);
      if (info != null)
         metadata = toOSGiMetaData(dep, info);

      // #3 we support deployments that contain XModule
      XModule resModule = dep.getAttachment(XModule.class);
      if (metadata == null && resModule != null)
         metadata = toOSGiMetaData(dep, resModule);

      // #4 check if we have a valid OSGi manifest
      if (metadata == null)
      {
         VirtualFile rootFile = dep.getRoot();
         String location = dep.getLocation();
         try
         {
            info = BundleInfo.createBundleInfo(rootFile, location);
            metadata = toOSGiMetaData(dep, info);
         }
         catch (BundleException ex)
         {
            // ignore
         }
      }

      // #5 check if we have META-INF/jbosgi-xservice.properties
      if (metadata == null)
      {
         VirtualFile rootFile = dep.getRoot();
         metadata = getXServiceMetaData(rootFile);
      }

      if (metadata == null)
         throw new BundleException("Not a valid OSGi deployment: " + dep);

      dep.addAttachment(OSGiMetaData.class, metadata);
      return metadata;
   }

   private OSGiMetaData getXServiceMetaData(VirtualFile rootFile)
   {
      // Try jbosgi-xservice.properties
      try
      {
         VirtualFile child = rootFile.getChild("META-INF/jbosgi-xservice.properties");
         if (child != null)
         {
            OSGiMetaData metadata = OSGiMetaDataBuilder.load(child.openStream());
            return metadata;
         }

         VirtualFile parentFile = rootFile.getParent();
         if (parentFile != null)
         {
            child = parentFile.getChild("jbosgi-xservice.properties");
            if (child != null)
            {
               OSGiMetaData metadata = OSGiMetaDataBuilder.load(child.openStream());
               return metadata;
            }
         }
      }
      catch (IOException ex)
      {
         log.warnf(ex, "Cannot process XService metadata: %s", rootFile);
      }
      return null;
   }

   private OSGiMetaData toOSGiMetaData(final Deployment dep, final BundleInfo info)
   {
      Manifest manifest = info.getManifest();
      return OSGiMetaDataBuilder.load(manifest);
   }

   private OSGiMetaData toOSGiMetaData(final Deployment dep, final XModule resModule)
   {
      String symbolicName = dep.getSymbolicName();
      Version version = Version.parseVersion(dep.getVersion());
      if (symbolicName.equals(resModule.getName()) == false || version.equals(resModule.getVersion()) == false)
         throw new IllegalArgumentException("Inconsistent bundle metadata: " + resModule);

      // Create dummy OSGiMetaData from the user provided XModule
      OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder(symbolicName, version);
      return builder.getOSGiMetaData();
   }
}