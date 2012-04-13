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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.util.jar.Manifest;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * A plugin taht create bundle {@link Deployment} objects.
 *
 * @author thomas.diesler@jboss.com
 * @since 12-Jul-2010
 */
final class DeploymentFactoryPlugin extends AbstractPluginService<DeploymentFactoryPlugin> {

    static void addService(ServiceTarget serviceTarget) {
        DeploymentFactoryPlugin service = new DeploymentFactoryPlugin();
        ServiceBuilder<DeploymentFactoryPlugin> builder = serviceTarget.addService(InternalServices.DEPLOYMENT_FACTORY_PLUGIN, service);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    DeploymentFactoryPlugin() {
    }

    @Override
    public DeploymentFactoryPlugin getValue() {
        return this;
    }

    /**
     * Create a {@link Deployment} from the given bundle storage.
     *
     * @param storageState The bundle storage to be associated with the deployment
     * @throws BundleException If the given root file does not
     */
    Deployment createDeployment(StorageState storageState) throws BundleException {
        assert storageState != null : "Null storageState";
        String location = storageState.getLocation();
        VirtualFile rootFile = storageState.getRootFile();
        Deployment dep = createDeployment(location, rootFile);
        dep.setAutoStart(storageState.isPersistentlyStarted());
        dep.addAttachment(StorageState.class, storageState);
        return dep;
    }

    Deployment createDeployment(String location, VirtualFile rootFile) throws BundleException {

        BundleException cause = null;
        try {
            BundleInfo info = BundleInfo.createBundleInfo(rootFile, location);
            Deployment dep = DeploymentFactory.createDeployment(info);
            OSGiMetaData metadata = toOSGiMetaData(info);
            dep.addAttachment(BundleInfo.class, info);
            dep.addAttachment(OSGiMetaData.class, metadata);
            return dep;
        } catch (NumberFormatException nfe) {
            throw FrameworkMessages.MESSAGES.bundleInvalidNumberFormat(nfe, nfe.getMessage());
        } catch (BundleException ex) {
            // No valid OSGi manifest. Fallback to jbosgi-xservice.properties
            cause = ex;
        }

        // Check if we have META-INF/jbosgi-xservice.properties
        OSGiMetaData metadata = getXServiceMetaData(rootFile);
        if (metadata != null) {
            String symbolicName = metadata.getBundleSymbolicName();
            Version version = metadata.getBundleVersion();
            Deployment dep = DeploymentFactory.createDeployment(rootFile, location, symbolicName, version);
            dep.addAttachment(OSGiMetaData.class, metadata);
            return dep;
        }

        Manifest manifest = null;
        try {
            manifest = VFSUtils.getManifest(rootFile);
        } catch (IOException ex) {
            // ignore no manifest
        }

        // Generate symbolic name and version for empty manifest
        if (manifest != null && manifest.getMainAttributes().keySet().size() < 2) {
            Deployment dep = DeploymentFactory.createDeployment(rootFile, location, null, Version.emptyVersion);
            metadata = OSGiMetaDataBuilder.load(manifest);
            dep.addAttachment(OSGiMetaData.class, metadata);
            return dep;
        }

        // Rethrow root cause if we have one
        throw (cause != null ? cause : new BundleException("Cannot create deployment from: " + rootFile));
    }

    /**
     * Creates {@link OSGiMetaData} from the deployment.
     *
     * @return The OSGiMetaData
     * @throws BundleException If OSGiMetaData could not be constructed from the deployment
     */
    OSGiMetaData createOSGiMetaData(Deployment deployment) throws BundleException {

        // #1 check if the Deployment already contains a OSGiMetaData
        OSGiMetaData metadata = deployment.getAttachment(OSGiMetaData.class);
        if (metadata != null)
            return metadata;

        // #2 check if the Deployment contains valid BundleInfo
        BundleInfo info = deployment.getAttachment(BundleInfo.class);
        if (info != null)
            metadata = toOSGiMetaData(info);

        // #3 check if we have a valid OSGi manifest
        if (metadata == null) {
            VirtualFile rootFile = deployment.getRoot();
            String location = deployment.getLocation();
            try {
                info = BundleInfo.createBundleInfo(rootFile, location);
                metadata = toOSGiMetaData(info);
            } catch (BundleException ex) {
                // ignore
            }
        }

        // #5 check if we have META-INF/jbosgi-xservice.properties
        if (metadata == null) {
            VirtualFile rootFile = deployment.getRoot();
            metadata = getXServiceMetaData(rootFile);
        }

        if (metadata == null)
            throw MESSAGES.bundleInvalidDeployment(deployment);

        deployment.addAttachment(OSGiMetaData.class, metadata);
        return metadata;
    }

    private OSGiMetaData getXServiceMetaData(VirtualFile rootFile) {
        // Try jbosgi-xservice.properties
        try {
            VirtualFile child = rootFile.getChild("META-INF/jbosgi-xservice.properties");
            if (child != null) {
                OSGiMetaData metadata = OSGiMetaDataBuilder.load(child.openStream());
                return metadata;
            }

            VirtualFile parentFile = rootFile.getParent();
            if (parentFile != null) {
                child = parentFile.getChild("jbosgi-xservice.properties");
                if (child != null) {
                    OSGiMetaData metadata = OSGiMetaDataBuilder.load(child.openStream());
                    return metadata;
                }
            }
        } catch (IOException ex) {
            LOGGER.warnCannotProcessMetadataProperties(ex, rootFile);
        }
        return null;
    }

    private OSGiMetaData toOSGiMetaData(final BundleInfo info) {
        Manifest manifest = info.getManifest();
        return OSGiMetaDataBuilder.load(manifest);
    }
}