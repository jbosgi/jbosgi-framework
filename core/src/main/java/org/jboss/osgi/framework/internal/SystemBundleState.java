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

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.FrameworkModuleProvider;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * Represents the state of the system {@link Bundle}.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class SystemBundleState extends AbstractBundleState {

    // Provide logging
    static final Logger log = Logger.getLogger(SystemBundleState.class);

    private final FrameworkModuleProvider frameworkModuleProvider;
    private BundleStorageState storageState;
    private SystemBundleRevision revision;

    SystemBundleState(FrameworkState frameworkState, FrameworkModuleProvider frameworkModuleProvider) {
        super(frameworkState, 0, Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
        this.frameworkModuleProvider = frameworkModuleProvider;
    }

    /**
     * Assert that the given bundle is an instance of {@link UserBundleState}
     *
     * @throws IllegalArgumentException if the given bundle is not an instance of {@link UserBundleState}
     */
    static SystemBundleState assertBundleState(Bundle bundle) {
        bundle = AbstractBundleState.assertBundleState(bundle);

        if (bundle instanceof UserBundleState == false)
            throw new IllegalArgumentException("Not an UserBundleState: " + bundle);

        return (SystemBundleState) bundle;
    }

    Module getFrameworkModule() {
        return frameworkModuleProvider.getFrameworkModule(this);
    }

    SystemBundleRevision createBundleRevision(OSGiMetaData metadata) throws BundleException {
        revision = new SystemBundleRevision(this, metadata);
        return revision;
    }

    @Override
    List<AbstractBundleRevision> getAllBundleRevisions() {
        return Collections.singletonList((AbstractBundleRevision)revision);
    }

    void createStorageState(BundleStoragePlugin storagePlugin) {
        try {
            storageState = storagePlugin.createStorageState(0, Constants.SYSTEM_BUNDLE_SYMBOLICNAME, null);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create system persistence storage", ex);
        }
    }

    @Override
    ServiceName getServiceName(int state) {
        return Services.SYSTEM_BUNDLE;
    }

    @Override
    public String getLocation() {
        return Constants.SYSTEM_BUNDLE_LOCATION;
    }

    @Override
    public Version getVersion() {
        return Version.emptyVersion;
    }

    @Override
    AbstractBundleContext createContextInternal() {
        return new SystemBundleContext(this);
    }

    @Override
    SystemBundleRevision getCurrentBundleRevision() {
        return revision;
    }

    @Override
    BundleStorageState getBundleStorageState() {
        return storageState;
    }

    @Override
    boolean isFragment() {
        return false;
    }

    @Override
    boolean isSingleton() {
        return true;
    }

    @Override
    SystemBundleRevision getBundleRevisionById(int revisionId) {
        if (revisionId != 0)
            throw new IllegalArgumentException("System bundle does not have a revision with id: " + revisionId);
        return revision;
    }

    @Override
    void startInternal(int options) throws BundleException {
        // do nothing
    }

    @Override
    void stopInternal(int options) throws BundleException {
        // do nothing
    }

    @Override
    void updateInternal(InputStream input) throws BundleException {
        // do nothing
    }

    @Override
    void uninstallInternal() throws BundleException {
        throw new BundleException("Cannot uninstall the system bundle");
    }
}