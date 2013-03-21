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

import java.io.InputStream;

import org.jboss.msc.service.ServiceController;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.resolver.ResolutionException;

/**
 * A handler for the bundle lifecycle.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Mar-2011
 */
public interface BundleLifecycle {

    ServiceController<? extends XBundleRevision> installBundleRevision(BundleContext context, Deployment dep) throws BundleException;

    void resolve(XBundle bundle) throws ResolutionException;

    void start(XBundle bundle, int options) throws BundleException;

    void stop(XBundle bundle, int options) throws BundleException;

    void update(XBundle bundle, InputStream input) throws BundleException;

    void uninstall(XBundle bundle, int options) throws BundleException;
}