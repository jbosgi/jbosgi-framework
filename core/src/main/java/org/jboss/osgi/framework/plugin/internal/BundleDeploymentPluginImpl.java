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

import java.io.IOException;
import java.util.jar.Manifest;

import org.jboss.logging.Logger;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.BundleDeploymentPlugin;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.spi.util.BundleInfo;
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
         String symbolicName = info.getSymbolicName();
         Version version = info.getVersion();
         Deployment dep = DeploymentFactory.createDeployment(rootFile, location, symbolicName, version);
         dep.addAttachment(BundleInfo.class, info);
         return dep;
      }
      catch (NumberFormatException nfe)
      {
         throw new BundleException("Invalid OSGi version:", nfe);
      }
      catch (BundleException ex)
      {
         log.debug("Not a valid osgi manifest: " + ex.getMessage());
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

      throw new BundleException("Cannot process as OSGi deployment: " + location);
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
      String descriptor = "META-INF/jbosgi-xservice.properties";
      try
      {
         VirtualFile child = rootFile.getChild(descriptor);
         if (child != null)
         {
            OSGiMetaData metadata = OSGiMetaDataBuilder.load(child.openStream());
            return metadata;
         }
      }
      catch (IOException ex)
      {
         log.warn("Cannot process XService metadata: " + rootFile, ex);
      }
      return null;
   }

   private OSGiMetaData toOSGiMetaData(Deployment dep, BundleInfo info)
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