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

import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;


/**
 * Represents the INSTALLED state of a user bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class UserBundleInstalledService<T extends UserBundleState> extends AbstractBundleService<T> {

    private final Deployment initialDeployment;

    private T bundleState;

    UserBundleInstalledService(FrameworkState frameworkState, Deployment dep) {
        super(frameworkState);
        this.initialDeployment = dep;
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        Deployment dep = initialDeployment;
        InternalStorageState storageState = null;
        try {
            bundleState = createBundleState(dep);
            dep.addAttachment(Bundle.class, bundleState);
            OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
            storageState = bundleState.createStorageState(dep);
            UserBundleRevision userRev = bundleState.createRevision(dep);
            bundleState.initUserBundleState(metadata);
            validateBundle(bundleState, metadata);
            processNativeCode(bundleState, dep);
            addToEnvironment(userRev);
            bundleState.changeState(Bundle.INSTALLED, 0);
            bundleState.fireBundleEvent(BundleEvent.INSTALLED);
        } catch (BundleException ex) {
            if (storageState != null) {
                BundleStoragePlugin storagePlugin = getFrameworkState().getBundleStoragePlugin();
                storagePlugin.deleteStorageState(storageState);
            }
            throw new StartException(ex);
        }
    }

    abstract T createBundleState(Deployment dep);

    @Override
    T getBundleState() {
        return bundleState;
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        getBundleManager().uninstallBundle(getBundleState(), Bundle.STOP_TRANSIENT);
    }

    private void validateBundle(UserBundleState userBundle, OSGiMetaData metadata) throws BundleException {
        if (metadata.getBundleManifestVersion() > 1) {
            new BundleValidatorR4().validateBundle(userBundle, metadata);
        } else {
            new BundleValidatorR3().validateBundle(userBundle, metadata);
        }
    }

    // Process the Bundle-NativeCode header if there is one
    private void processNativeCode(UserBundleState userBundle, Deployment dep) {
        OSGiMetaData metadata = userBundle.getOSGiMetaData();
        if (metadata.getBundleNativeCode() != null) {
            FrameworkState frameworkState = userBundle.getFrameworkState();
            NativeCodePlugin nativeCodePlugin = frameworkState.getNativeCodePlugin();
            nativeCodePlugin.deployNativeCode(dep);
        }
    }

    private void addToEnvironment(UserBundleRevision userRev) {
        UserBundleState userBundle = userRev.getBundleState();
        if (userBundle.isSingleton()) {
            String symbolicName = getBundleState().getSymbolicName();
            for (AbstractBundleState aux : getBundleManager().getBundles(symbolicName, null)) {
                if (aux != userBundle && aux.isSingleton()) {
                    LOGGER.infoNoResolvableSingleton(userBundle);
                    return;
                }
            }
        }
        FrameworkState frameworkState = userBundle.getFrameworkState();
        XEnvironment env = frameworkState.getEnvironment();
        env.installResources(userRev);
    }
}