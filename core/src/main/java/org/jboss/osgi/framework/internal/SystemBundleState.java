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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_LOCATION;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;

/**
 * Represents the state of the system {@link Bundle}.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class SystemBundleState extends AbstractBundleState {

    private final SystemBundleRevision systemRevision;
    private InternalStorageState storageState;

    SystemBundleState(FrameworkState frameworkState, SystemBundleRevision revision) {
        super(frameworkState, revision, 0);
        this.systemRevision = revision;
    }

    /**
     * Assert that the given bundle is an instance of {@link UserBundleState}
     */
    static SystemBundleState assertBundleState(Bundle bundle) {
        bundle = AbstractBundleState.assertBundleState(bundle);
        assert bundle instanceof SystemBundleState : "Not an SystemBundleState: " + bundle;
        return (SystemBundleState) bundle;
    }

    @Override
    public List<XBundleRevision> getAllBundleRevisions() {
        return Collections.singletonList((XBundleRevision) systemRevision);
    }

    void createStorageState(BundleStoragePlugin storagePlugin) {
        try {
            storageState = storagePlugin.createStorageState(0, SYSTEM_BUNDLE_LOCATION, 0, null);
        } catch (IOException ex) {
            throw MESSAGES.illegalStateCannotCreateSystemBundleStorage(ex);
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
    public SystemBundleRevision getBundleRevision() {
        return systemRevision;
    }

    @Override
    InternalStorageState getStorageState() {
        return storageState;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T adapt(Class<T> type) {
        T result = super.adapt(type);
        if (result == null) {
            if (type.isAssignableFrom(BundleManager.class)) {
                result = (T) getBundleManager();
            } else if (type.isAssignableFrom(XEnvironment.class)) {
                result = (T) getFrameworkState().getEnvironment();
            }
        }
        return result;
    }

    @Override
    public boolean isFragment() {
        return false;
    }

    @Override
    boolean isSingleton() {
        return true;
    }

    @Override
    SystemBundleRevision getBundleRevisionById(int revisionId) {
        assert revisionId == 0 : "System bundle does not have a revision with id: " + revisionId;
        return systemRevision;
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
        throw MESSAGES.bundleCannotUninstallSystemBundle();
    }
}
