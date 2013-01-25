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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.FrameworkBuilder;
import org.jboss.osgi.framework.spi.FrameworkBuilder.FrameworkPhase;
import org.jboss.osgi.framework.spi.ServiceTracker;
import org.jboss.osgi.resolver.Adaptable;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;

/**
 * The proxy that represents the {@link Framework}.
 *
 * The {@link FrameworkProxy} uses the respective {@link AbstractFrameworkService}s.
 * It never interacts with the {@link FrameworkState} directly.
 * The client may hold a reference to the {@link FrameworkProxy}.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class FrameworkProxy implements Framework, Adaptable {

    private final FrameworkBuilder frameworkBuilder;
    private BundleManagerPlugin bundleManager;
    private boolean firstInit;

    FrameworkProxy(FrameworkBuilder frameworkBuilder) {
        this.frameworkBuilder = frameworkBuilder;
        this.firstInit = true;
    }

    @Override
    public long getBundleId() {
        return 0;
    }

    @Override
    public String getSymbolicName() {
        return Constants.JBOSGI_FRAMEWORK_SYMBOLIC_NAME;
    }

    @Override
    public String getLocation() {
        return Constants.JBOSGI_FRAMEWORK_LOCATION;
    }

    @Override
    public Version getVersion() {
        return BundleManagerPlugin.getFrameworkVersion();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T adapt(Class<T> type) {
        if (!hasStopped()) {
            BundleManagerPlugin bundleManager = getBundleManager();
            if (type.isAssignableFrom(BundleManager.class)) {
                return (T) bundleManager;
            } else if (type.isAssignableFrom(ServiceContainer.class)) {
                return (T) bundleManager.getServiceContainer();
            }
        }
        return null;
    }

    private BundleManagerPlugin getBundleManager() {
        return bundleManager;
    }

    /**
     * Initialize this Framework.
     *
     * After calling this method, this Framework must:
     *
     * - Be in the Bundle.STARTING state.
     * - Have a valid Bundle Context.
     * - Be at start level 0.
     * - Have event handling enabled.
     * - Have reified Bundle objects for all installed bundles.
     * - Have registered any framework services (e.g. PackageAdmin, ConditionalPermissionAdmin, StartLevel)
     *
     * This Framework will not actually be started until start is called.
     *
     * This method does nothing if called when this Framework is in the Bundle.STARTING, Bundle.ACTIVE or Bundle.STOPPING
     * states.
     */
    @Override
    public void init() throws BundleException {

        // This method does nothing if called when this Framework is in the STARTING, ACTIVE or STOPPING state
        int state = getState();
        if (state == Bundle.STARTING || state == Bundle.ACTIVE || state == Bundle.STOPPING)
            return;

        LOGGER.debugf("Init framework");
        try {
            ServiceContainer serviceContainer = frameworkBuilder.getServiceContainer();
            if (serviceContainer == null)
                serviceContainer = frameworkBuilder.createServiceContainer();

            ServiceTarget serviceTarget = frameworkBuilder.getServiceTarget();
            if (serviceTarget == null)
                serviceTarget = serviceContainer.subTarget();

            BundleManager auxBundleManager = frameworkBuilder.createFrameworkServices(serviceContainer, firstInit);
            bundleManager = BundleManagerPlugin.assertBundleManagerPlugin(auxBundleManager);
            bundleManager.setManagerState(Bundle.STARTING);

            ServiceTracker<Object> serviceTracker = new ServiceTracker<Object>("Framework.init");
            frameworkBuilder.installServices(FrameworkPhase.CREATE, serviceTarget, serviceTracker);
            frameworkBuilder.installServices(FrameworkPhase.INIT, serviceTarget, serviceTracker);

            // Wait for all CREATE and INIT services to complete
            if (!serviceTracker.awaitCompletion()) {
                throw serviceTracker.getFirstFailure();
            }

        } catch (BundleException ex) {
            bundleManager.setManagerState(Bundle.INSTALLED);
            throw ex;
        } catch (Throwable ex) {
            bundleManager.setManagerState(Bundle.INSTALLED);
            throw MESSAGES.cannotInitializeFramework(ex);
        } finally {
            firstInit = false;
        }
    }

    @Override
    public void start() throws BundleException {
        start(0);
    }

    /**
     * Start this Framework.
     *
     * The following steps are taken to start this Framework:
     *
     * - If this Framework is not in the {@link #STARTING} state, {@link #init()} is called
     * - All installed bundles must be started
     * - The start level of this Framework is moved to the FRAMEWORK_BEGINNING_STARTLEVEL
     *
     * Any exceptions that occur during bundle starting must be wrapped in a {@link BundleException} and then published as a
     * framework event of type {@link FrameworkEvent#ERROR}
     *
     * - This Framework's state is set to {@link #ACTIVE}.
     * - A framework event of type {@link FrameworkEvent#STARTED} is fired
     */
    @Override
    public void start(int options) throws BundleException {

        // If this Framework is not in the STARTING state, initialize this Framework
        if (getState() != Bundle.STARTING)
            init();

        LOGGER.debugf("Start framework");
        try {

            ServiceTracker<Object> serviceTracker = new ServiceTracker<Object>("Framework.start");
            frameworkBuilder.installServices(FrameworkPhase.ACTIVE, bundleManager.getServiceTarget(), serviceTracker);

            // Wait for all CREATE and INIT services to complete
            if (!serviceTracker.awaitCompletion()) {
                throw serviceTracker.getFirstFailure();
            }

            bundleManager.setManagerState(Bundle.ACTIVE);

        } catch (BundleException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw MESSAGES.cannotStartFramework(ex);
        }
    }

    @Override
    public void stop() throws BundleException {
        stop(0);
    }

    /**
     * Stop this Framework.
     *
     * The method returns immediately to the caller after initiating the following steps to be taken on another thread.
     *
     * 1. This Framework's state is set to Bundle.STOPPING.
     * 2. All installed bundles must be stopped without changing each bundle's persistent autostart setting.
     * 3. Unregister all services registered by this Framework.
     * 4. Event handling is disabled.
     * 5. This Framework's state is set to Bundle.RESOLVED.
     * 6. All resources held by this Framework are released. This includes threads, bundle class loaders, open files, etc.
     * 7. Notify all threads that are waiting at waitForStop that the stop operation has completed.
     *
     * After being stopped, this Framework may be discarded, initialized or started.
     */
    @Override
    public void stop(int options) throws BundleException {
        if (bundleManager != null)
            bundleManager.shutdownManager(false);
    }

    /**
     * Wait until this Framework has completely stopped.
     *
     * The stop and update methods on a Framework performs an asynchronous stop of the Framework. This method can be used to
     * wait until the asynchronous stop of this Framework has completed. This method will only wait if called when this
     * Framework is in the Bundle.STARTING, Bundle.ACTIVE, or Bundle.STOPPING states. Otherwise it will return immediately.
     *
     * A Framework Event is returned to indicate why this Framework has stopped.
     */
    @Override
    public FrameworkEvent waitForStop(long timeout) throws InterruptedException {
        return bundleManager != null ? bundleManager.waitForStop(timeout) : new FrameworkEvent(FrameworkEvent.STOPPED, this, null);
    }

    @Override
    public void update(InputStream input) throws BundleException {
        if (input != null) {
            try {
                input.close();
            } catch (IOException ex) {
                // ignore
            }
        }
        update();
    }

    @Override
    public void update() {

        int state = getState();
        if (state != Bundle.STARTING && state != Bundle.ACTIVE)
            return;

        LOGGER.debugf("update framework");

        final int targetState = getState();
        Runnable cmd = new Runnable() {

            @Override
            public void run() {
                try {
                    bundleManager.shutdownManager(true);
                    if (targetState == Bundle.STARTING) {
                        init();
                    }
                    if (targetState == Bundle.ACTIVE) {
                        start();
                    }
                } catch (Exception ex) {
                    LOGGER.errorCannotUpdateFramework(ex);
                }
            }
        };
        new Thread(cmd, "Framework update thread").run();
    }

    @Override
    public void uninstall() throws BundleException {
        throw MESSAGES.cannotUninstallSystemBundle();
    }

    @Override
    public int getState() {
        return bundleManager != null ? bundleManager.getManagerState() : Bundle.INSTALLED;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Dictionary<String, String> getHeaders() {
        assertNotStopped();
        return getSystemBundle().getHeaders();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        assertNotStopped();
        return getSystemBundle().getRegisteredServices();
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        assertNotStopped();
        return getSystemBundle().getServicesInUse();
    }

    @Override
    public boolean hasPermission(Object permission) {
        assertNotStopped();
        return getSystemBundle().hasPermission(permission);
    }

    @Override
    public URL getResource(String name) {
        assertNotStopped();
        return getSystemBundle().getResource(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        assertNotStopped();
        return getSystemBundle().getHeaders(locale);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        assertNotStopped();
        return getSystemBundle().loadClass(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        assertNotStopped();
        return getSystemBundle().getResources(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Enumeration<String> getEntryPaths(String path) {
        assertNotStopped();
        return getSystemBundle().getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        assertNotStopped();
        return getSystemBundle().getEntry(path);
    }

    @Override
    public long getLastModified() {
        assertNotStopped();
        return getSystemBundle().getLastModified();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        assertNotStopped();
        return getSystemBundle().findEntries(path, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return !hasStopped() ? getSystemBundle().getBundleContext() : null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException();
    }

    private boolean hasStopped() {
        return bundleManager == null || bundleManager.hasStopped();
    }

    private void assertNotStopped() {
        if (hasStopped())
            throw MESSAGES.illegalStateFrameworkAlreadyStopped();
    }

    private XBundle getSystemBundle() {
        if (bundleManager == null)
            throw MESSAGES.illegalStateFrameworkNotInitialized();
        return bundleManager.getFrameworkState().getSystemBundle();
    }
}
