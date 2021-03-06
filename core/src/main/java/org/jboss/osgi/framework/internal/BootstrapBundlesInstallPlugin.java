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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.deployer.DeploymentFactory;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.BootstrapBundlesInstall;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.spi.BundleInfo;
import org.jboss.osgi.spi.util.StringPropertyReplacer;
import org.jboss.osgi.spi.util.StringPropertyReplacer.PropertyProvider;
import org.osgi.framework.BundleException;

/**
 * A plugin that installs the auto install bundles on framework startup.
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class BootstrapBundlesInstallPlugin extends BootstrapBundlesInstall<Void> {

    BootstrapBundlesInstallPlugin() {
        super(IntegrationServices.BOOTSTRAP_BUNDLES);
    }

    @Override
    public void start(final StartContext context) throws StartException {

        final ServiceTarget serviceTarget = context.getChildTarget();
        final List<URL> autoInstall = new ArrayList<URL>();
        final List<URL> autoStart = new ArrayList<URL>();

        String propValue = (String) getBundleManager().getProperty(Constants.PROPERTY_AUTO_INSTALL_URLS);
        if (propValue != null) {
            for (String path : propValue.split(",")) {
                URL url = toURL(path.trim());
                if (url != null) {
                    autoInstall.add(url);
                }
            }
        }
        propValue = (String) getBundleManager().getProperty(Constants.PROPERTY_AUTO_START_URLS);
        if (propValue != null) {
            for (String path : propValue.split(",")) {
                URL url = toURL(path.trim());
                if (url != null) {
                    autoStart.add(url);
                }
            }
        }

        // Add the autoStart bundles to autoInstall
        autoInstall.addAll(autoStart);

        // Collect the bundle deployments
        List<Deployment> deployments = new ArrayList<Deployment>();
        for (URL url : autoInstall) {
            try {
                BundleInfo info = BundleInfo.createBundleInfo(url);
                Deployment dep = DeploymentFactory.createDeployment(info);
                dep.setAutoStart(autoStart.contains(url));
                deployments.add(dep);
            } catch (BundleException ex) {
                LOGGER.errorStateCannotInstallInitialBundle(ex, url.toExternalForm());
            }
        }

        // Install the bundles from the given locations
        installBootstrapBundles(serviceTarget, deployments);
    }

    private URL toURL(final String path) {

        URL pathURL = null;
        PropertyProvider provider = new PropertyProvider() {
            @Override
            public String getProperty(String key) {
                return (String) getBundleManager().getProperty(key);
            }
        };
        String realPath = StringPropertyReplacer.replaceProperties(path, provider);
        try {
            pathURL = new URL(realPath);
        } catch (MalformedURLException ex) {
            // ignore
        }

        if (pathURL == null) {
            try {
                File file = new File(realPath);
                if (file.exists())
                    pathURL = file.toURI().toURL();
            } catch (MalformedURLException ex) {
                throw MESSAGES.illegalArgumentInvalidPath(ex, realPath);
            }
        }

        if (pathURL == null)
            throw MESSAGES.illegalArgumentInvalidPath(null, realPath);

        return pathURL;
    }
}