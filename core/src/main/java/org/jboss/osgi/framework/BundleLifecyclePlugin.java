package org.jboss.osgi.framework;
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

import org.jboss.msc.service.Service;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.BundleException;

/**
 * A handler for bundle deployments.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Mar-2011
 */
public interface BundleLifecyclePlugin extends Service<BundleLifecyclePlugin> {

    void install(Deployment dep, DefaultHandler handler) throws BundleException;

    void start(XBundle bundle, int options, DefaultHandler handler) throws BundleException;

    void stop(XBundle bundle, int options, DefaultHandler handler) throws BundleException;

    void uninstall(XBundle bundle, DefaultHandler handler);

    interface DefaultHandler {
        void install(BundleManager bundleManager, Deployment dep) throws BundleException;
        void start(XBundle bundle, int options) throws BundleException;
        void stop(XBundle bundle, int options) throws BundleException;
        void uninstall(XBundle bundle);
    }
}