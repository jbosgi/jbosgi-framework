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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * The proxy that represents a {@link BundleService}.
 * 
 * The {@link BundleProxy} uses the respective {@link BundleService}s. 
 * It never interacts with the {@link BundleState} directly. 
 * The client may hold a reference to the {@link BundleProxy}. 
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class BundleProxy<T extends BundleState> implements Bundle {

    // Provide logging
    static final Logger log = Logger.getLogger(BundleProxy.class);

    private final ServiceContainer serviceContainer;
    private final ServiceName serviceName;
    private String cachedObjectName;
    private long bundleId;
    private T bundleState;

    BundleProxy(T bundleState) {
        this.serviceContainer = bundleState.getBundleManager().getServiceContainer();
        this.serviceName = bundleState.getServiceName();
        this.cachedObjectName = bundleState.toString();
        this.bundleId = bundleState.getBundleId();
    }

    T getBundleState() {
        return awaitBundleServiceActive();
    }

    @Override
    public long getBundleId() {
        return bundleId;
    }

    @Override
    public int getState() {
        return awaitBundleServiceActive().getState();
    }

    @Override
    public void start(int options) throws BundleException {
        awaitBundleServiceActive().start(options);
    }

    @Override
    public void start() throws BundleException {
        awaitBundleServiceActive().start();
    }

    @Override
    public void stop(int options) throws BundleException {
        awaitBundleServiceActive().stop(options);
    }

    @Override
    public void stop() throws BundleException {
        awaitBundleServiceActive().stop();
    }

    @Override
    public void update(InputStream input) throws BundleException {
        awaitBundleServiceActive().update(input);
    }

    @Override
    public void update() throws BundleException {
        awaitBundleServiceActive().update();
    }

    @Override
    public void uninstall() throws BundleException {
        awaitBundleServiceActive().uninstall();
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return awaitBundleServiceActive().getHeaders();
    }

    @Override
    public String getLocation() {
        return awaitBundleServiceActive().getLocation();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        return awaitBundleServiceActive().getRegisteredServices();
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        return awaitBundleServiceActive().getServicesInUse();
    }

    @Override
    public boolean hasPermission(Object permission) {
        return awaitBundleServiceActive().hasPermission(permission);
    }

    @Override
    public URL getResource(String name) {
        return awaitBundleServiceActive().getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return awaitBundleServiceActive().getHeaders(locale);
    }

    @Override
    public String getSymbolicName() {
        return awaitBundleServiceActive().getSymbolicName();
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return awaitBundleServiceActive().loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return awaitBundleServiceActive().getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        return awaitBundleServiceActive().getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        return awaitBundleServiceActive().getEntry(path);
    }

    @Override
    public long getLastModified() {
        return awaitBundleServiceActive().getLastModified();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        return awaitBundleServiceActive().findEntries(path, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return awaitBundleServiceActive().getBundleContext();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        return awaitBundleServiceActive().getSignerCertificates(signersType);
    }

    @Override
    public Version getVersion() {
        return awaitBundleServiceActive().getVersion();
    }

    @SuppressWarnings("unchecked")
    private T awaitBundleServiceActive() {
        if (bundleState == null) {
            ServiceController<T> controller = (ServiceController<T>) serviceContainer.getRequiredService(serviceName);
            controller.addListener(new AbstractServiceListener<T>() {
                @Override
                public void serviceStopped(ServiceController<? extends T> controller) {
                    controller.removeListener(this);
                    // otherwise do nothing
                }
            });
            controller.setMode(Mode.ACTIVE);
            FutureServiceValue<T> future = new FutureServiceValue<T>(controller);
            try {
                bundleState = future.get(2000, TimeUnit.MILLISECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                throw new IllegalStateException("Cannot starting bundle service: " + serviceName, cause);
            } catch (TimeoutException e) {
                throw new IllegalStateException("Timeout starting bundle service: " + serviceName);
            }
        }
        return bundleState;
    }

    @Override
    public String toString() {
        return cachedObjectName;
    }
}