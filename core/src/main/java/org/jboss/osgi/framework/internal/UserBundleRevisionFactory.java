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
import static org.jboss.osgi.framework.internal.InternalConstants.REVISION_IDENTIFIER_KEY;
import static org.jboss.osgi.framework.spi.IntegrationConstants.OSGI_METADATA_KEY;
import static org.jboss.osgi.framework.spi.IntegrationConstants.STORAGE_STATE_KEY;

import java.io.IOException;

import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.framework.spi.NativeCode;
import org.jboss.osgi.framework.spi.StorageManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;

/**
 * Service associated with a user installed {@link XBundleRevision}
 *
 * @author thomas.diesler@jboss.com
 * @since 21-Mar-2013
 */
abstract class UserBundleRevisionFactory<R extends UserBundleRevision> {

    private final FrameworkState frameworkState;
    private final ServiceTarget serviceTarget;
    private final Deployment deployment;
    private final BundleContext targetContext;
    private R bundleRevision;

    UserBundleRevisionFactory(FrameworkState frameworkState, BundleContext targetContext, Deployment deployment, ServiceTarget serviceTarget) {
        this.frameworkState = frameworkState;
        this.serviceTarget = serviceTarget;
        this.deployment = deployment;
        this.targetContext = targetContext;
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    BundleManagerPlugin getBundleManager() {
        return frameworkState.getBundleManager();
    }

    R create() throws BundleException {
        LOGGER.debugf("Creating %s for: %s", getClass().getSimpleName(), deployment);
        StorageState storageState = null;
        try {
            Deployment dep = deployment;
            RevisionIdentifier revIdentifier = dep.getAttachment(REVISION_IDENTIFIER_KEY);
            storageState = createStorageState(dep, revIdentifier);
            OSGiMetaData metadata = dep.getAttachment(OSGI_METADATA_KEY);
            bundleRevision = createBundleRevision(dep, storageState, serviceTarget);
            bundleRevision.putAttachment(XResource.RESOURCE_IDENTIFIER_KEY, revIdentifier.getRevisionId());
            validateBundleRevision(bundleRevision, metadata);
            processNativeCode(bundleRevision, metadata, dep);
            XBundle bundle = dep.getAttachment(IntegrationConstants.BUNDLE_KEY);
            if (bundle == null) {
                bundle = createBundleState(bundleRevision);
                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                userBundle.addBundleRevision(bundleRevision);
                dep.putAttachment(IntegrationConstants.BUNDLE_KEY, userBundle);
                userBundle.initLazyActivation();
                installBundleRevision(bundleRevision);
                userBundle.changeState(Bundle.INSTALLED, 0);
                LOGGER.infoBundleInstalled(bundle);
                FrameworkEvents events = frameworkState.getFrameworkEvents();
                events.fireBundleEvent(targetContext, bundle, BundleEvent.INSTALLED);
            } else {
                UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
                userBundle.addBundleRevision(bundleRevision);
                installBundleRevision(bundleRevision);
            }
        } catch (Exception ex) {
            throw handleCreateException(storageState, ex);
        }
        return bundleRevision;
    }

    private BundleException handleCreateException(StorageState storageState, Exception ex) {
        if (storageState != null) {
            StorageManager storagePlugin = frameworkState.getStorageManager();
            storagePlugin.deleteStorageState(storageState);
        }
        if (ex instanceof BundleException) {
            return (BundleException) ex;
        }
        return MESSAGES.cannotCreateBundleRevisionFromDeployment(ex, deployment);
    }

    abstract R createBundleRevision(Deployment deployment, StorageState storageState, ServiceTarget serviceTarget) throws BundleException;

    UserBundleState createBundleState(UserBundleRevision revision) {
        return new UserBundleState(frameworkState, revision);
    }

    private StorageState createStorageState(Deployment dep, RevisionIdentifier revIdentifier) throws BundleException {
        // The storage state exists when we re-create the bundle from persistent storage
        StorageState storageState = dep.getAttachment(STORAGE_STATE_KEY);
        if (storageState == null) {
            String location = dep.getLocation();
            VirtualFile rootFile = dep.getRoot();
            try {
                Integer startlevel = dep.getStartLevel();
                StorageManager storageManager = frameworkState.getStorageManager();
                storageState = storageManager.createStorageState(revIdentifier.getRevisionId(), location, startlevel, rootFile);
                dep.putAttachment(STORAGE_STATE_KEY, storageState);
            } catch (IOException ex) {
                throw MESSAGES.cannotSetupStorage(ex, rootFile);
            }
        }
        return storageState;
    }

    private void installBundleRevision(R brev) throws BundleException {
        XEnvironment env = frameworkState.getEnvironment();
        env.installResources(new XResource[] { brev });
    }

    private void validateBundleRevision(R bundleRevision, OSGiMetaData metadata) throws BundleException {
        if (metadata.getBundleManifestVersion() > 1) {
            new BundleRevisionValidatorR4().validateBundleRevision(bundleRevision, metadata);
        } else {
            new BundleRevisionValidatorR3().validateBundleRevision(bundleRevision, metadata);
        }
    }

    // Process the Bundle-NativeCode header if there is one
    private void processNativeCode(R bundleRevision, OSGiMetaData metadata, Deployment dep) {
        if (metadata.getBundleNativeCode() != null) {
            NativeCode nativeCodePlugin = frameworkState.getNativeCode();
            nativeCodePlugin.deployNativeCode(dep);
        }
    }
}
