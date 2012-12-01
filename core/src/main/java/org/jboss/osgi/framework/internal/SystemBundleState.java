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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.FrameworkModuleProvider;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;

/**
 * Represents the state of the system {@link Bundle}.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class SystemBundleState extends AbstractBundleState<SystemBundleRevision> {

    private final SystemBundleRevision systemRevision;
    private final BundleRevisions bundleRevisions;

    SystemBundleState(final FrameworkState frameworkState, final SystemBundleRevision brev) {
        super(frameworkState, brev, 0);
        this.systemRevision = brev;

        // Assign the {@link ModuleIdentifier}
        brev.addAttachment(ModuleIdentifier.class, FrameworkModuleProvider.FRAMEWORK_MODULE_IDENTIFIER);

        final Bundle bundle = this;
        bundleRevisions = new BundleRevisions() {

            @Override
            public Bundle getBundle() {
                return bundle;
            }

            @Override
            public List<BundleRevision> getRevisions() {
                return Collections.singletonList((BundleRevision)brev);
            }
        };
    }

    static SystemBundleState assertBundleState(Bundle bundle) {
        bundle = AbstractBundleState.assertBundleState(bundle);
        assert bundle instanceof SystemBundleState : "Not an SystemBundleState: " + bundle;
        return (SystemBundleState) bundle;
    }

    @Override
    public List<XBundleRevision> getAllBundleRevisions() {
        return Collections.singletonList((XBundleRevision) systemRevision);
    }

    @Override
    ServiceName getServiceName(int state) {
        return IntegrationServices.SYSTEM_BUNDLE_INTERNAL;
    }

    @Override
    public String getSymbolicName() {
        return Constants.FRAMEWORK_SYMBOLIC_NAME;
    }

    @Override
    public String getLocation() {
        return Constants.FRAMEWORK_LOCATION;
    }

    @Override
    public Version getVersion() {
        return BundleManagerPlugin.getFrameworkVersion();
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
    BundleRevisions getBundleRevisions() {
        return bundleRevisions;
    }

    @Override
    public <T> T adapt(Class<T> type) {
        T result = super.adapt(type);
        if (result == null) {
            result = getBundleManagerPlugin().adapt(type);
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
        // Does nothing because the system bundle is already started
    }

    @Override
    void stopInternal(int options) throws BundleException {
        // Returns immediately and shuts down the Framework on another thread
        getBundleManagerPlugin().shutdownManager(false);
    }

    @Override
    void updateInternal(InputStream input) throws BundleException {
        // [TODO] Returns immediately, then stops and restarts the Framework on another thread.
    }

    @Override
    void uninstallInternal(int options) throws BundleException {
        // The Framework must throw a BundleException indicating that the system bundle cannot be uninstalled
        throw MESSAGES.cannotUninstallSystemBundle();
    }
}
