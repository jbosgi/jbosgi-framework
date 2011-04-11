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

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

/**
 * Represents the INSTALLED state of a bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class AbstractBundleService<T extends AbstractBundleState> implements Service<T>, Bundle {

    // Provide logging
    static final Logger log = Logger.getLogger(AbstractBundleService.class);

    private final T bundleState;

    AbstractBundleService(T bundleState) {
        this.bundleState = bundleState;
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.debugf("Starting: %s", context.getController().getName());
    }

    @Override
    public void stop(StopContext context) {
        log.debugf("Stopping: %s", context.getController().getName());
    }
    
    @Override
    public T getValue() {
        return bundleState;
    }

    T getBundleState() {
        return bundleState;
    }

    BundleManager getBundleManager() {
        return bundleState.getBundleManager();
    }
    
    FrameworkState getFrameworkState() {
        return bundleState.getFrameworkState();
    }
    
    @Override
    public int getState() {
        return bundleState.getState();
    }

    @Override
    public long getBundleId() {
        return bundleState.getBundleId();
    }

    @Override
    public String getLocation() {
        return bundleState.getLocation();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        return bundleState.getRegisteredServices();
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        return bundleState.getServicesInUse();
    }

    @Override
    public boolean hasPermission(Object permission) {
        return bundleState.hasPermission(permission);
    }

    @Override
    public URL getResource(String name) {
        return bundleState.getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return bundleState.getHeaders();
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return bundleState.getHeaders(locale);
    }

    @Override
    public String getSymbolicName() {
        return bundleState.getSymbolicName();
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return bundleState.loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return bundleState.getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        return bundleState.getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        return bundleState.getEntry(path);
    }

    @Override
    public long getLastModified() {
        return bundleState.getLastModified();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        return bundleState.findEntries(path, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return bundleState.getBundleContext();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        return bundleState.getSignerCertificates(signersType);
    }

    @Override
    public Version getVersion() {
        return bundleState.getVersion();
    }

    @Override
    public void start(int options) throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }

    @Override
    public void start() throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }

    @Override
    public void stop(int options) throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }

    @Override
    public void stop() throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }

    @Override
    public void update(InputStream input) throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }

    @Override
    public void update() throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }

    @Override
    public void uninstall() throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }
}