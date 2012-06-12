/*
 * #%L
 * JBossOSGi Framework Core
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
package org.jboss.osgi.framework;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.jboss.modules.Module;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleRevision;

/**
 * An abstract implementation that adapts a {@link Module} to a {@link Bundle}
 *
 * @author thomas.diesler@jboss.com
 * @since 30-May-2012
 */
public class AbstractBundleAdaptor implements XBundle {

    private final BundleContext context;
    private final XBundleRevision bundlerev;
    private final Module module;
    private long lastModified;

    public AbstractBundleAdaptor(BundleContext context, Module module, XBundleRevision bundlerev) {
        if (context == null)
            throw MESSAGES.illegalArgumentNull("context");
        if (module == null)
            throw MESSAGES.illegalArgumentNull("module");
        if (bundlerev == null)
            throw MESSAGES.illegalArgumentNull("bundlerev");
        this.context = context;
        this.module = module;
        this.bundlerev = bundlerev;
        this.lastModified = System.currentTimeMillis();
    }

    @Override
    public long getBundleId() {
        Long bundleId = bundlerev.getAttachment(Long.class);
        return bundleId != null ? bundleId.longValue() : -1;
    }

    @Override
    public String getLocation() {
        return module.getIdentifier().toString();
    }

    @Override
    public String getSymbolicName() {
        return module.getIdentifier().getName();
    }

    @Override
    public int getState() {
        return Bundle.RESOLVED;
    }

    @Override
    public Version getVersion() {
        String slot = module.getIdentifier().getSlot();
        try {
            return Version.parseVersion(slot);
        } catch (IllegalArgumentException ex) {
            return Version.emptyVersion;
        }
    };

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return module.getClassLoader().loadClass(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T adapt(Class<T> type) {
        T result = null;
        if (type == Module.class) {
            result = (T) module;
        } else if (type == BundleRevision.class) {
            result = (T) bundlerev;
        }
        return result;
    }

    @Override
    public void start(int options) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop(int options) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void stop() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(InputStream input) throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void uninstall() throws BundleException {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Dictionary getHeaders() {
        return getHeaders(null);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Dictionary getHeaders(String locale) {
        // [TODO] Add support for manifest header related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-567
        return new Hashtable();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        return null;
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        return null;
    }

    @Override
    public boolean hasPermission(Object permission) {
        return false;
    }

    @Override
    public URL getResource(String name) {
        return getBundleRevision().getResource(name);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Enumeration getResources(String name) throws IOException {
        return getBundleRevision().getResources(name);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Enumeration getEntryPaths(String path) {
        return getBundleRevision().getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        return getBundleRevision().getEntry(path);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Enumeration findEntries(String path, String filePattern, boolean recurse) {
        return getBundleRevision().findEntries(path, filePattern, recurse);
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public BundleContext getBundleContext() {
        return context;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        return Collections.emptyMap();
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    public boolean isFragment() {
        return getBundleRevision().isFragment();
    }

    @Override
    public XBundleRevision getBundleRevision() {
        return bundlerev;
    }

    @Override
    public List<XBundleRevision> getAllBundleRevisions() {
        return Collections.singletonList(bundlerev);
    }

}
