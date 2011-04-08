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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.AutoInstallProvider;
import org.jboss.osgi.framework.ServiceNames;
import org.jboss.osgi.spi.util.StringPropertyReplacer;
import org.jboss.osgi.spi.util.StringPropertyReplacer.PropertyProvider;
import org.osgi.framework.BundleContext;

/**
 * A provider for the list of auto install bundles
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
final class DefaultAutoInstallProvider extends AbstractPluginService<AutoInstallProvider> implements AutoInstallProvider {

    static final String PROP_JBOSS_OSGI_AUTO_INSTALL = "org.jboss.osgi.auto.install";
    static final String PROP_JBOSS_OSGI_AUTO_START = "org.jboss.osgi.auto.start";

    // Provide logging
    final Logger log = Logger.getLogger(DefaultAutoInstallProvider.class);

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private List<URL> autoInstall = new ArrayList<URL>();
    private List<URL> autoStart = new ArrayList<URL>();

    static void addService(ServiceTarget serviceTarget) {
        DefaultAutoInstallProvider service = new DefaultAutoInstallProvider();
        ServiceBuilder<AutoInstallProvider> builder = serviceTarget.addService(ServiceNames.AUTOINSTALL_PROVIDER, service);
        builder.addDependency(ServiceNames.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private DefaultAutoInstallProvider() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        BundleManager bundleManager = injectedBundleManager.getValue();
        String propValue = (String) bundleManager.getProperty(PROP_JBOSS_OSGI_AUTO_INSTALL);
        if (propValue != null) {
            for (String path : propValue.split(",")) {
                URL url = toURL(bundleManager, path.trim());
                if (url != null) {
                    autoInstall.add(url);
                }
            }
        }
        propValue = (String) bundleManager.getProperty(PROP_JBOSS_OSGI_AUTO_START);
        if (propValue != null) {
            for (String path : propValue.split(",")) {
                URL url = toURL(bundleManager, path.trim());
                if (url != null) {
                    autoStart.add(url);
                }
            }
        }
    }

    @Override
    public AutoInstallProvider getValue() {
        return this;
    }

    @Override
    public List<URL> getAutoInstallList(BundleContext context) {
        return Collections.unmodifiableList(autoInstall);
    }

    @Override
    public List<URL> getAutoStartList(BundleContext context) {
        return Collections.unmodifiableList(autoStart);
    }

    private URL toURL(final BundleManager bundleManager, final String path) {

        URL pathURL = null;
        PropertyProvider provider = new PropertyProvider() {
            @Override
            public String getProperty(String key) {
                return (String) bundleManager.getProperty(key);
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
                throw new IllegalArgumentException("Invalid path: " + realPath, ex);
            }
        }

        if (pathURL == null)
            throw new IllegalArgumentException("Invalid path: " + realPath);

        return pathURL;
    }
}