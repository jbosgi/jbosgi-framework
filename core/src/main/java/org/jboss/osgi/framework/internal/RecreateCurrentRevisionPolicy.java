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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;
import static org.jboss.osgi.framework.spi.IntegrationConstants.OSGI_METADATA_KEY;

import java.io.IOException;
import java.io.InputStream;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.BundleLifecycle.BundleRefreshPolicy;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.DeploymentProvider;
import org.jboss.osgi.framework.spi.IntegrationConstants;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

/**
 * A refresh policy that recreates the current revision.
 *
 * @author thomas.diesler@jboss.com
 * @since 09-Apr-2013
 */
final class RecreateCurrentRevisionPolicy implements BundleRefreshPolicy {

    private final BundleManagerPlugin bundleManager;
    private VirtualFile rootFile;
    private XBundle bundle;

    RecreateCurrentRevisionPolicy(BundleManager bundleManager) {
        this.bundleManager = (BundleManagerPlugin) bundleManager;
    }

    @Override
    public void startBundleRefresh(XBundle bundle) throws BundleException {
        this.bundle = bundle;

        XBundleRevision brev = bundle.getBundleRevision();
        Deployment dep = brev.getAttachment(IntegrationConstants.DEPLOYMENT_KEY);

        try {
            InputStream inputStream = dep.getRoot().getStreamURL().openStream();
            rootFile = AbstractVFS.toVirtualFile(inputStream);
        } catch (IOException ex) {
            throw MESSAGES.cannotObtainVirtualFile(ex);
        }

        bundleManager.removeRevisionLifecycle(brev, 0);
    }

    @Override
    public void refreshCurrentRevision(XBundleRevision brev) throws BundleException {

        // Create the revision {@link Deployment}
        DeploymentProvider deploymentManager = bundleManager.getFrameworkState().getDeploymentProvider();
        Deployment dep = deploymentManager.createDeployment(bundle.getLocation(), rootFile);
        OSGiMetaData metadata = deploymentManager.createOSGiMetaData(dep);
        dep.putAttachment(OSGI_METADATA_KEY, metadata);
        dep.putAttachment(IntegrationConstants.BUNDLE_KEY, bundle);
        dep.setAutoStart(false);

        // Create the {@link XBundleRevision} service from {@link Deployment}
        BundleContext context = bundleManager.getSystemBundle().getBundleContext();
        bundleManager.createBundleRevision(context, dep, null);
    }

    @Override
    public void endBundleRefresh(XBundle bundle) {
    }
}