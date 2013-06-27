/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;
import static org.jboss.osgi.framework.spi.IntegrationConstants.STORAGE_STATE_KEY;

import java.io.IOException;
import java.util.Properties;
import java.util.jar.Manifest;

import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.FrameworkMessages;
import org.jboss.osgi.framework.spi.DeploymentProvider;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.StorageState;
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
public final class DeploymentProviderImpl implements DeploymentProvider {

    @Override
    public Deployment createDeployment(StorageState storageState) throws BundleException {
        assert storageState != null : "Null storageState";
        String location = storageState.getLocation();
        VirtualFile rootFile = storageState.getRootFile();
        Deployment dep = createDeployment(location, rootFile);
        dep.setAutoStart(storageState.isPersistentlyStarted());
        dep.setStartLevel(storageState.getStartLevel());
        dep.putAttachment(STORAGE_STATE_KEY, storageState);
        return dep;
    }

    @Override
    public Deployment createDeployment(String location, VirtualFile rootFile) throws BundleException {

        BundleException cause = null;
        try {
            BundleInfo info = BundleInfo.createBundleInfo(rootFile, location);
            Deployment dep = DeploymentFactory.createDeployment(info);
            OSGiMetaData metadata = info.getOSGiMetadata();
            dep.putAttachment(IntegrationConstants.BUNDLE_INFO_KEY, info);
            dep.putAttachment(IntegrationConstants.OSGI_METADATA_KEY, metadata);
            return dep;
        } catch (IllegalArgumentException nfe) {
            throw FrameworkMessages.MESSAGES.invalidNumberFormat(nfe, nfe.getMessage());
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
            dep.putAttachment(IntegrationConstants.OSGI_METADATA_KEY, metadata);
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
            dep.putAttachment(IntegrationConstants.OSGI_METADATA_KEY, metadata);
            return dep;
        }

        // Rethrow root cause if we have one
        throw (cause != null ? cause : new BundleException("Cannot create deployment from: " + rootFile));
    }

    @Override
    public OSGiMetaData createOSGiMetaData(Deployment deployment) throws BundleException {

        // #1 check if the Deployment already contains a OSGiMetaData
        OSGiMetaData metadata = deployment.getAttachment(IntegrationConstants.OSGI_METADATA_KEY);
        if (metadata != null)
            return metadata;

        // #2 check if the Deployment contains valid BundleInfo
        BundleInfo info = deployment.getAttachment(IntegrationConstants.BUNDLE_INFO_KEY);
        if (info != null)
            metadata = info.getOSGiMetadata();

        // #3 check if we have a valid OSGi manifest
        if (metadata == null) {
            VirtualFile rootFile = deployment.getRoot();
            String location = deployment.getLocation();
            try {
                info = BundleInfo.createBundleInfo(rootFile, location);
                metadata = info.getOSGiMetadata();
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
            throw MESSAGES.invalidDeployment(deployment);

        deployment.putAttachment(IntegrationConstants.OSGI_METADATA_KEY, metadata);
        return metadata;
    }

    private OSGiMetaData getXServiceMetaData(VirtualFile rootFile) {
        // Try jbosgi-xservice.properties
        try {
            VirtualFile child = rootFile.getChild("META-INF/jbosgi-xservice.properties");
            if (child != null) {
                Properties props = new Properties();
                props.load(child.openStream());
                OSGiMetaData metadata = OSGiMetaDataBuilder.load(props);
                return metadata;
            }

            VirtualFile parentFile = rootFile.getParent();
            if (parentFile != null) {
                child = parentFile.getChild("jbosgi-xservice.properties");
                if (child != null) {
                    Properties props = new Properties();
                    props.load(child.openStream());
                    OSGiMetaData metadata = OSGiMetaDataBuilder.load(props);
                    return metadata;
                }
            }
        } catch (Exception ex) {
            LOGGER.warnCannotProcessMetadataProperties(ex, rootFile);
        }
        return null;
    }
}
