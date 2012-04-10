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

import static org.jboss.osgi.framework.Constants.DEFAULT_FRAMEWORK_INIT_TIMEOUT;
import static org.jboss.osgi.framework.Constants.DEFAULT_FRAMEWORK_START_TIMEOUT;
import static org.jboss.osgi.framework.Constants.PROPERTY_FRAMEWORK_INIT_TIMEOUT;
import static org.jboss.osgi.framework.Constants.PROPERTY_FRAMEWORK_START_TIMEOUT;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.FutureServiceValue;
import org.jboss.osgi.framework.Services;
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
final class FrameworkProxy implements Framework {

    private final AtomicInteger proxyState;
    private final FrameworkBuilder frameworkBuilder;
    private LenientShutdownContainer lenientContainer;
    private final AtomicBoolean frameworkStopped;
    private FrameworkState frameworkState;
    private boolean firstInit;
    private int stoppedEvent;

    FrameworkProxy(FrameworkBuilder frameworkBuilder) {
        this.frameworkBuilder = frameworkBuilder;
        this.stoppedEvent = FrameworkEvent.STOPPED;
        this.proxyState = new AtomicInteger(Bundle.INSTALLED);
        this.frameworkStopped = new AtomicBoolean(false);
        this.firstInit = true;
    }

    @Override
    public long getBundleId() {
        return 0;
    }

    @Override
    public String getSymbolicName() {
        return Constants.SYSTEM_BUNDLE_SYMBOLICNAME;
    }

    @Override
    public String getLocation() {
        return Constants.SYSTEM_BUNDLE_LOCATION;
    }

    @Override
    public Version getVersion() {
        return Version.emptyVersion;
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
            frameworkStopped.set(false);

            boolean allowContainerShutdown = false;
            ServiceContainer serviceContainer = frameworkBuilder.getServiceContainer();
            if (serviceContainer == null) {
                serviceContainer = ServiceContainer.Factory.create();
                allowContainerShutdown = true;
            }
            lenientContainer = new LenientShutdownContainer(serviceContainer, allowContainerShutdown);
            ServiceTarget serviceTarget = frameworkBuilder.getServiceTarget();
            if (serviceTarget == null)
                serviceTarget = serviceContainer.subTarget();

            frameworkBuilder.createFrameworkServicesInternal(serviceTarget, Mode.ON_DEMAND, firstInit);
            proxyState.set(Bundle.STARTING);
            frameworkState = awaitFrameworkState();
            firstInit = false;

        } catch (Exception ex) {
            throw MESSAGES.bundleCannotInitializeFramework(ex);
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

        LOGGER.debugf("start framework");
        try {
            awaitActiveFramework();
            proxyState.set(Bundle.ACTIVE);
        } catch (Exception ex) {
            throw MESSAGES.bundleCannotStartFramework(ex);
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
        stopInternal(false);
    }

    private void stopInternal(boolean stopForUpdate) {

        // If the Framework is not STARTING and not ACTIVE there is nothing to do
        int state = getState();
        if (state != Bundle.STARTING && state != Bundle.ACTIVE)
            return;

        LOGGER.debugf("stop framework");

        CoreServices coreServices = frameworkState.getCoreServices();
        SystemBundleState systemBundle = frameworkState.getSystemBundle();

        stoppedEvent = stopForUpdate ? FrameworkEvent.STOPPED_UPDATE : FrameworkEvent.STOPPED;
        systemBundle.changeState(Bundle.STOPPING);
        proxyState.set(Bundle.STOPPING);

        // Move to start level 0 in the current thread
        StartLevelPlugin startLevel = coreServices.getStartLevelPlugin();
        if (startLevel != null) {
            startLevel.decreaseStartLevel(0);
        } else {
            // No Start Level Service available, stop all bundles individually...
            // All installed bundles must be stopped without changing each bundle's persistent autostart setting
            BundleManager bundleManager = systemBundle.getBundleManager();
            for (AbstractBundleState bundleState : bundleManager.getBundles()) {
                if (bundleState.getBundleId() != 0) {
                    try {
                        bundleState.stop(Bundle.STOP_TRANSIENT);
                    } catch (Exception ex) {
                        // Any exceptions that occur during bundle stopping must be wrapped in a BundleException and then
                        // published as a framework event of type FrameworkEvent.ERROR
                        bundleManager.fireFrameworkError(bundleState, "stopping bundle", ex);
                    }
                }
            }
        }

        lenientContainer.shutdown();
        proxyState.set(Bundle.RESOLVED);
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
        if (lenientContainer == null)
            return new FrameworkEvent(FrameworkEvent.STOPPED, this, null);

        LenientShutdownContainer localContainer = lenientContainer;
        localContainer.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        if (localContainer.isShutdownComplete() == false) {
            return new FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, this, null);
        }
        return new FrameworkEvent(stoppedEvent, this, null);
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

            public void run() {
                try {
                    stopInternal(true);
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
        throw MESSAGES.bundleCannotUninstallSystemBundle();
    }

    @Override
    public int getState() {
        return proxyState.get();
    }

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

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        assertNotStopped();
        return getSystemBundle().getResources(name);
    }

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

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        assertNotStopped();
        return getSystemBundle().findEntries(path, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return isNotStopped() ? getSystemBundle().getBundleContext() : null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException();
    }

    private boolean isNotStopped() {
        boolean serviceContainerActive = lenientContainer != null && lenientContainer.isShutdownInitiated() == false;
        return serviceContainerActive == true && frameworkStopped.get() == false;
    }

    private void assertNotStopped() {
        if (isNotStopped() == false)
            throw MESSAGES.illegalStateFrameworkAlreadyStopped();
    }

    private SystemBundleState getSystemBundle() {
        if (frameworkState == null)
            throw MESSAGES.illegalStateFrameworkNotInitialized();
        return frameworkState.getSystemBundle();
    }

    @SuppressWarnings("unchecked")
    private FrameworkState awaitFrameworkState() throws ExecutionException, TimeoutException {
        final ServiceController<FrameworkState> controller = (ServiceController<FrameworkState>) lenientContainer.getRequiredService(Services.FRAMEWORK_INIT);
        final String serviceName = controller.getName().getCanonicalName();
        controller.addListener(new AbstractServiceListener<FrameworkState>() {
            public void transition(final ServiceController<? extends FrameworkState> controller, final ServiceController.Transition transition) {
                LOGGER.tracef("awaitFrameworkState %s => %s", serviceName, transition);
                switch (transition) {
                    case STARTING_to_START_FAILED:
                    case STOPPING_to_DOWN:
                        controller.removeListener(this);
                        frameworkStopped.set(true);
                        frameworkState = null;
                        break;
                }
            }
        });
        controller.setMode(Mode.ACTIVE);
        FutureServiceValue<FrameworkState> future = new FutureServiceValue<FrameworkState>(controller);
        Integer timeout = (Integer) frameworkBuilder.getProperty(PROPERTY_FRAMEWORK_INIT_TIMEOUT, DEFAULT_FRAMEWORK_INIT_TIMEOUT);
        return future.get(timeout, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    private void awaitActiveFramework() throws ExecutionException, TimeoutException {
        final ServiceController<FrameworkState> controller = (ServiceController<FrameworkState>) lenientContainer.getRequiredService(Services.FRAMEWORK_ACTIVE);
        controller.setMode(Mode.ACTIVE);
        FutureServiceValue<FrameworkState> future = new FutureServiceValue<FrameworkState>(controller);
        Integer timeout = (Integer) frameworkBuilder.getProperty(PROPERTY_FRAMEWORK_START_TIMEOUT, DEFAULT_FRAMEWORK_START_TIMEOUT);
        future.get(timeout, TimeUnit.MILLISECONDS);
    }

    // The TCK calls waitForStop before it initiates the shutdown, in which case the {@link ServiceContainer}
    // returns imediately from awaitTermination and returns false from isShutdownComplete.
    // We compensate with a grace periode that allows the shutdown to get initiated after awaitTermination.
    static class LenientShutdownContainer {
        private final ServiceContainer serviceContainer;
        private final AtomicBoolean shutdownInitiated;
        private final boolean allowContainerShutdown;

        LenientShutdownContainer(final ServiceContainer providedContainer, boolean allowContainerShutdown) {
            this.serviceContainer = providedContainer;
            this.allowContainerShutdown = allowContainerShutdown;
            this.shutdownInitiated = new AtomicBoolean(false);
        }

        ServiceController<?> getRequiredService(ServiceName serviceName) {
            return serviceContainer.getRequiredService(serviceName);
        }

        boolean isShutdownInitiated() {
            return shutdownInitiated.get();
        }

        void shutdown() {
            if (allowContainerShutdown) {
                serviceContainer.shutdown();
                synchronized (shutdownInitiated) {
                    shutdownInitiated.set(true);
                    LOGGER.debugf("shutdownInitiated.notifyAll");
                    shutdownInitiated.notifyAll();
                }
            }
        }

        void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            LOGGER.debugf("awaitTermination");
            synchronized (shutdownInitiated) {
                if (shutdownInitiated.get() == false) {
                    LOGGER.debugf("shutdownInitiated.wait");
                    shutdownInitiated.wait(2000);
                }
            }
            serviceContainer.awaitTermination(timeout, unit);
        }

        boolean isShutdownComplete() {
            return serviceContainer.isShutdownComplete();
        }
    }
}