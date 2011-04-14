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
import org.jboss.osgi.spi.NotImplementedException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;

/**
 * The base of all framework services.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class FrameworkService implements Service<FrameworkService>, Framework {

    // Provide logging
    static final Logger log = Logger.getLogger(FrameworkService.class);

    @Override
    public void start(StartContext context) throws StartException {
        log.debugf("Starting: %s", context.getController().getName());
    }

    @Override
    public void stop(StopContext context) {
        log.debugf("Stopping: %s", context.getController().getName());
    }

    @Override
    public FrameworkService getValue() {
        return this;
    }

    abstract FrameworkState getFrameworkState();

    BundleManager getBundleManager() {
        return getFrameworkState().getBundleManager();
    }

    SystemBundleState getSystemBundle() {
        return getFrameworkState().getSystemBundle();
    }

    void changeState(int newstate) {
        throw new NotImplementedException();
    }

    @Override
    public int getState() {
        return getSystemBundle().getState();
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return getSystemBundle().getHeaders();
    }

    @Override
    public long getBundleId() {
        return getSystemBundle().getBundleId();
    }

    @Override
    public String getLocation() {
        return getSystemBundle().getLocation();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        return getSystemBundle().getRegisteredServices();
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        return getSystemBundle().getServicesInUse();
    }

    @Override
    public boolean hasPermission(Object permission) {
        return getSystemBundle().hasPermission(permission);
    }

    @Override
    public URL getResource(String name) {
        return getSystemBundle().getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return getSystemBundle().getHeaders(locale);
    }

    @Override
    public String getSymbolicName() {
        return getSystemBundle().getSymbolicName();
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return getSystemBundle().loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return getSystemBundle().getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        return getSystemBundle().getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        return getSystemBundle().getEntry(path);
    }

    @Override
    public long getLastModified() {
        return getSystemBundle().getLastModified();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        return getSystemBundle().findEntries(path, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return getSystemBundle().getBundleContext();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        return getSystemBundle().getSignerCertificates(signersType);
    }

    @Override
    public Version getVersion() {
        return getSystemBundle().getVersion();
    }

    @Override
    public void init() throws BundleException {
        throw new UnsupportedOperationException("Not supported on the service object");
    }

    @Override
    public FrameworkEvent waitForStop(long timeout) throws InterruptedException {
        throw new UnsupportedOperationException("Not supported on the service object");
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