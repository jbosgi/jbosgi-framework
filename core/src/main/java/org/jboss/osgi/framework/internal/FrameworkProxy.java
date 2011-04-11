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

import static org.jboss.osgi.framework.Constants.PROPERTY_FRAMEWORK_INIT_TIMEOUT;
import static org.jboss.osgi.framework.Constants.PROPERTY_FRAMEWORK_START_TIMEOUT;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.logging.Logger;
import org.jboss.msc.service.AbstractServiceListener;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.ServiceNames;
import org.jboss.osgi.spi.NotImplementedException;
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
 * The {@link FrameworkProxy} uses the respective {@link FrameworkService}s.
 * It never interacts with the {@link FrameworkState} directly.
 * The client may hold a reference to the {@link FrameworkProxy}.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class FrameworkProxy implements Framework {

    // Provide logging
    static final Logger log = Logger.getLogger(FrameworkProxy.class);

    private final FrameworkBuilder frameworkBuilder;
    private ServiceContainer serviceContainer;
    private boolean shutdownContainer;
    private int frameworkBundleState;
    private boolean serviceStopped;
    private boolean firstInit;
    private int stoppedEvent;

    FrameworkProxy(FrameworkBuilder frameworkBuilder) {
        this.frameworkBuilder = frameworkBuilder;
        this.stoppedEvent = FrameworkEvent.STOPPED;
        this.frameworkBundleState = Bundle.INSTALLED;
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

        try {
            serviceStopped = false;

            serviceContainer = frameworkBuilder.getServiceContainer();
            if (serviceContainer == null) {
                serviceContainer = ServiceContainer.Factory.create();
                shutdownContainer = true;
            }

            ServiceTarget serviceTarget = frameworkBuilder.getServiceTarget();
            if (serviceTarget == null)
                serviceTarget = serviceContainer.subTarget();

            frameworkBuilder.createFrameworkServicesInternal(serviceTarget, Mode.ACTIVE, firstInit);
            awaitFrameworkInit();
            firstInit = false;

        } catch (IllegalStateException ex) {
            throw new BundleException(ex.getMessage(), ex);
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

        try {
            FrameworkState frameworkState = awaitFrameworkInit();
            frameworkState.setStartStopOptions(options);
            awaitActiveFramework();
        } catch (IllegalStateException ex) {
            throw new BundleException(ex.getMessage(), ex);
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

        FrameworkState frameworkState = awaitFrameworkInit();
        CoreServices coreServices = frameworkState.getCoreServices();
        SystemBundleState systemBundle = frameworkState.getSystemBundle();

        stoppedEvent = stopForUpdate ? FrameworkEvent.STOPPED_UPDATE : FrameworkEvent.STOPPED;
        systemBundle.changeState(Bundle.STOPPING);

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

        if (shutdownContainer == true) {
            serviceContainer.shutdown();
        } else {
            ServiceController<?> controller = serviceContainer.getRequiredService(ServiceNames.BUNDLE_MANAGER);
            controller.setMode(Mode.REMOVE);
        }
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
    @SuppressWarnings("unchecked")
    public FrameworkEvent waitForStop(long timeout) throws InterruptedException {
        if (shutdownContainer == true) {
            serviceContainer.awaitTermination(timeout, TimeUnit.MILLISECONDS);
            if (serviceContainer.isShutdownComplete() == false) {
                return new FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, this, null);
            }
        } else {
            final CountDownLatch latch = new CountDownLatch(1);
            ServiceController<BundleManager> controller = (ServiceController<BundleManager>) serviceContainer.getRequiredService(ServiceNames.BUNDLE_MANAGER);
            controller.addListener(new AbstractServiceListener<BundleManager>() {
                @Override
                public void serviceRemoved(ServiceController<? extends BundleManager> controller) {
                    controller.removeListener(this);
                    latch.countDown();
                }
            });
            controller.setMode(Mode.REMOVE);
            if (latch.await(timeout, TimeUnit.MILLISECONDS) == false) {
                return new FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, this, null);
            }
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

        final int targetState = getState();
        Runnable cmd = new Runnable() {

            public void run() {
                try {
                    stopInternal(true);
                    if (targetState == Bundle.STARTING)
                        init();
                    if (targetState == Bundle.ACTIVE)
                        start();
                } catch (Exception ex) {
                    log.errorf(ex, "Error stopping framework");
                }
            }
        };
        new Thread(cmd, "OSGi Framework update").run();
    }

    @Override
    public void uninstall() throws BundleException {
        throw new BundleException("The system bundle cannot be uninstalled");
    }

    @Override
    public int getState() {
        return frameworkInit != null ? frameworkInit.getSystemBundle().getState() : frameworkBundleState;
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getHeaders();
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getRegisteredServices();
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getServicesInUse();
    }

    @Override
    public boolean hasPermission(Object permission) {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().hasPermission(permission);
    }

    @Override
    public URL getResource(String name) {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getHeaders(locale);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().loadClass(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getEntry(path);
    }

    @Override
    public long getLastModified() {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().getLastModified();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        assertNotStopped();
        return awaitFrameworkInit().getSystemBundle().findEntries(path, filePattern, recurse);
    }

    @Override
    public BundleContext getBundleContext() {
        return serviceStopped ? null : awaitFrameworkInit().getSystemBundle().getBundleContext();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        throw new NotImplementedException();
    }

    private void assertNotStopped() {
        if (serviceStopped == true)
            throw new IllegalStateException("Framework already stopped");
    }

    private FrameworkState frameworkInit;

    @SuppressWarnings("unchecked")
    private FrameworkState awaitFrameworkInit() {
        if (frameworkInit == null) {
            ServiceController<FrameworkState> controller = (ServiceController<FrameworkState>) serviceContainer.getRequiredService(ServiceNames.FRAMEWORK_INIT);
            controller.addListener(new AbstractServiceListener<FrameworkState>() {
                @Override
                public void serviceStopped(ServiceController<? extends FrameworkState> controller) {
                    controller.removeListener(this);
                    frameworkBundleState = Bundle.RESOLVED;
                    serviceStopped = true;
                    frameworkInit = null;
                }
            });
            controller.setMode(Mode.ACTIVE);
            FutureServiceValue<FrameworkState> future = new FutureServiceValue<FrameworkState>(controller);
            try {
                Integer timeout = (Integer) frameworkBuilder.getProperty(PROPERTY_FRAMEWORK_INIT_TIMEOUT, new Integer(2000));
                frameworkInit = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                throw new IllegalStateException("Cannot initialize Framework", cause);
            } catch (TimeoutException e) {
                throw new IllegalStateException("Timeout starting Framework");
            }
        }
        return frameworkInit;
    }

    private FrameworkState activeFramework;

    @SuppressWarnings("unchecked")
    private FrameworkState awaitActiveFramework() {
        if (activeFramework == null) {
            ServiceController<FrameworkState> controller = (ServiceController<FrameworkState>) serviceContainer.getRequiredService(ServiceNames.FRAMEWORK_ACTIVE);
            controller.addListener(new AbstractServiceListener<FrameworkState>() {
                @Override
                public void serviceStopped(ServiceController<? extends FrameworkState> controller) {
                    controller.removeListener(this);
                    activeFramework = null;
                }
            });
            controller.setMode(Mode.ACTIVE);
            FutureServiceValue<FrameworkState> future = new FutureServiceValue<FrameworkState>(controller);
            try {
                Integer timeout = (Integer) frameworkBuilder.getProperty(PROPERTY_FRAMEWORK_START_TIMEOUT, new Integer(2000));
                activeFramework = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                throw new IllegalStateException("Cannot start the Framework", cause);
            } catch (TimeoutException e) {
                throw new IllegalStateException("Timeout start the Framework");
            }
        }
        return activeFramework;
    }
}