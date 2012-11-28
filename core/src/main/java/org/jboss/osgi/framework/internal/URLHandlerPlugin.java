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

import java.net.ContentHandler;
import java.net.URLStreamHandler;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.URLHandlerSupport;
import org.osgi.framework.BundleContext;

/**
 * This plugin provides OSGi URL handler support as per the specification.
 * The interface is through the java.net.URL class and the OSGi Service Registry.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @author Thomas.Diesler@jboss.com
 * @since 10-Jan-2011
 */
final class URLHandlerPlugin extends AbstractIntegrationService<URLHandlerSupport> implements URLHandlerSupport {

    private final InjectedValue<BundleManagerImpl> injectedBundleManager = new InjectedValue<BundleManagerImpl>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private URLHandlerSupportImpl handlerSupport;

    URLHandlerPlugin() {
        super(IntegrationServices.URL_HANDLER_PLUGIN);
        URLHandlerSupportImpl.initURLHandlerSupport();
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<URLHandlerSupport> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerImpl.class, injectedBundleManager);
        builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, injectedSystemContext);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        BundleManagerImpl bundleManager = injectedBundleManager.getValue();
        handlerSupport = new URLHandlerSupportImpl(bundleManager);
        handlerSupport.start();
    }

    @Override
    public void stop(StopContext context) {
        handlerSupport.stop();
    }

    @Override
    public URLHandlerSupport getValue() {
        return this;
    }

    public URLStreamHandler createURLStreamHandler(String protocol) {
        return handlerSupport.createURLStreamHandler(protocol);
    }

    public ContentHandler createContentHandler(String mimetype) {
        return handlerSupport.createContentHandler(mimetype);
    }
}
