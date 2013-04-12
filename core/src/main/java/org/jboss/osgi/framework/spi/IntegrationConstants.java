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
package org.jboss.osgi.framework.spi;

import java.util.jar.Manifest;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.osgi.spi.AttachmentKey;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;

/**
 * A collection of propriatary constants.
 *
 * @author thomas.diesler@jboss.com
 * @since 08-Apr-2013
 */
public interface IntegrationConstants {

    /** The bundle attachment key */
    AttachmentKey<Bundle> BUNDLE_KEY = AttachmentKey.create(Bundle.class);
    /** The bundle activator attachment key */
    AttachmentKey<BundleActivator> BUNDLE_ACTIVATOR_KEY = AttachmentKey.create(BundleActivator.class);
    /** The deployment attachment key */
    AttachmentKey<Deployment> DEPLOYMENT_KEY = AttachmentKey.create(Deployment.class);
    /** The metadata attachment key */
    AttachmentKey<OSGiMetaData> OSGI_METADATA_KEY = AttachmentKey.create(OSGiMetaData.class);
    /** The Manifest attachment key */
    AttachmentKey<Manifest> MANIFEST_KEY = AttachmentKey.create(Manifest.class);
    /** The bundle attachment key */
    AttachmentKey<BundleInfo> BUNDLE_INFO_KEY = AttachmentKey.create(BundleInfo.class);
    /** The service name attachment key */
    AttachmentKey<ServiceName> SERVICE_NAME_KEY = AttachmentKey.create(ServiceName.class);
    /** The storage state attachment key */
    AttachmentKey<StorageState> STORAGE_STATE_KEY = AttachmentKey.create(StorageState.class);
    /** The module identifier attachment key */
    AttachmentKey<ModuleIdentifier> MODULE_IDENTIFIER_KEY = AttachmentKey.create(ModuleIdentifier.class);

}