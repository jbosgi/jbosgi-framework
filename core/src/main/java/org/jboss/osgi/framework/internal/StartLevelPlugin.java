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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.launch.Framework;
import org.osgi.service.startlevel.StartLevel;

/**
 * An implementation of the {@link StartLevel} service.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 */
public final class StartLevelPlugin extends AbstractExecutorService<StartLevel> implements StartLevel {

    static final int BUNDLE_STARTLEVEL_UNSPECIFIED = -1;

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    private final InjectedValue<FrameworkEventsPlugin> injectedFrameworkEvents = new InjectedValue<FrameworkEventsPlugin>();

    private int initialBundleStartLevel = 1; // Synchronized on this
    private ServiceRegistration registration;
    private int startLevel = 0; // Synchronized on this

    static void addService(ServiceTarget serviceTarget) {
        StartLevelPlugin service = new StartLevelPlugin();
        ServiceBuilder<StartLevel> builder = serviceTarget.addService(Services.START_LEVEL, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, service.injectedBundleManager);
        builder.addDependency(InternalServices.FRAMEWORK_EVENTS_PLUGIN, FrameworkEventsPlugin.class, service.injectedFrameworkEvents);
        builder.addDependency(Services.SYSTEM_BUNDLE, SystemBundleState.class, service.injectedSystemBundle);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private StartLevelPlugin() {
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        BundleContext systemContext = injectedSystemBundle.getValue().getBundleContext();
        registration = systemContext.registerService(StartLevel.class.getName(), this, null);
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
        registration.unregister();
        registration = null;
    }

    @Override
    public StartLevelPlugin getValue() {
        return this;
    }

    @Override
    ExecutorService createExecutorService() {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable run) {
                Thread thread = new Thread(run);
                thread.setName("OSGi StartLevel Thread");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public synchronized int getStartLevel() {
        return startLevel;
    }

    @Override
    public synchronized void setStartLevel(final int level) {
        final FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
        final AbstractBundleState bundleState = injectedSystemBundle.getValue();
        if (level > getStartLevel()) {
            getExecutorService().execute(new Runnable() {

                @Override
                public void run() {
                    LOGGER.infoIncreasingStartLevel(getStartLevel(), level);
                    increaseStartLevel(level);
                    eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.STARTLEVEL_CHANGED, null);
                }
            });
        } else if (level < getStartLevel()) {
            getExecutorService().execute(new Runnable() {

                @Override
                public void run() {
                    LOGGER.infoDecreasingStartLevel(getStartLevel(), level);
                    decreaseStartLevel(level);
                    eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.STARTLEVEL_CHANGED, null);
                }
            });
        }
    }

    @Override
    public int getBundleStartLevel(Bundle bundle) {
        if (bundle instanceof Framework)
            return 0;

        AbstractBundleState b = AbstractBundleState.assertBundleState(bundle);
        if (b instanceof SystemBundleState)
            return 0;
        else if (b instanceof HostBundleState)
            return ((HostBundleState) b).getStartLevel();

        return StartLevelPlugin.BUNDLE_STARTLEVEL_UNSPECIFIED;
    }

    @Override
    public void setBundleStartLevel(final Bundle bundle, final int level) {
        if (bundle.getBundleId() == 0)
            throw MESSAGES.illegalArgumentStartLevelOnSystemBundles();

        final FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
        final HostBundleState hostBundle = HostBundleState.assertBundleState(bundle);
        hostBundle.setStartLevel(level);

        if (level <= getStartLevel() && hostBundle.isPersistentlyStarted()) {
            // If the bundle is active or starting, we don't need to start it again
            if ((bundle.getState() & (Bundle.ACTIVE | Bundle.STARTING)) > 0)
                return;

            LOGGER.infoStartingBundleDueToStartLevel(hostBundle);
            getExecutorService().execute(new Runnable() {
                public void run() {
                    try {
                        int opts = Bundle.START_TRANSIENT;
                        if (isBundleActivationPolicyUsed(hostBundle))
                            opts |= Bundle.START_ACTIVATION_POLICY;

                        hostBundle.start(opts);
                    } catch (BundleException e) {
                        eventsPlugin.fireFrameworkEvent(hostBundle, FrameworkEvent.ERROR, e);
                    }
                }
            });
        } else {
            // If the bundle is not active we don't need to stop it
            if ((bundle.getState() & (Bundle.ACTIVE | Bundle.STARTING)) == 0)
                return;

            LOGGER.infoStoppingBundleDueToStartLevel(hostBundle);
            getExecutorService().execute(new Runnable() {
                public void run() {
                    try {
                        hostBundle.stop(Bundle.STOP_TRANSIENT);
                    } catch (BundleException e) {
                        eventsPlugin.fireFrameworkEvent(hostBundle, FrameworkEvent.ERROR, e);
                    }
                }
            });
        }
    }

    @Override
    public synchronized int getInitialBundleStartLevel() {
        return initialBundleStartLevel;
    }

    @Override
    public synchronized void setInitialBundleStartLevel(int startlevel) {
        initialBundleStartLevel = startlevel;
    }

    @Override
    public boolean isBundlePersistentlyStarted(Bundle bundle) {
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        BundleStorageState storageState = bundleState.getBundleStorageState();
        return storageState.isPersistentlyStarted();
    }

    @Override
    public boolean isBundleActivationPolicyUsed(Bundle bundle) {
        AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
        BundleStorageState storageState = bundleState.getBundleStorageState();
        return storageState.isBundleActivationPolicyUsed();
    }

    /**
     * Increases the Start Level of the Framework in the current thread.
     *
     * @param level the target Start Level to which the Framework should move.
     */
    synchronized void increaseStartLevel(int level) {
        BundleManager bundleManager = injectedBundleManager.getValue();
        Collection<AbstractBundleState> bundles = bundleManager.getBundles();
        while (startLevel < level) {
            startLevel++;
            LOGGER.infoStartingBundlesForStartLevel(level);
            for (AbstractBundleState bundle : bundles) {
                if (!(bundle instanceof HostBundleState))
                    continue;

                HostBundleState hostBundle = (HostBundleState) bundle;
                if (hostBundle.getStartLevel() == startLevel && hostBundle.isPersistentlyStarted()) {
                    try {
                        int opts = Bundle.START_TRANSIENT;
                        if (isBundleActivationPolicyUsed(bundle)) {
                            opts |= Bundle.START_ACTIVATION_POLICY;
                        }
                        bundle.start(opts);
                    } catch (Throwable e) {
                        FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
                        eventsPlugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, e);
                    }
                }
            }
        }
    }

    /**
     * Decreases the Start Level of the Framework in the current thread.
     *
     * @param level the target Start Level to which the Framework should move.
     */
    synchronized void decreaseStartLevel(int level) {
        BundleManager bundleManager = injectedBundleManager.getValue();
        while (startLevel > level) {
            LOGGER.infoStoppingBundlesForStartLevel(level);
            Collection<AbstractBundleState> bundles = bundleManager.getBundles();
            for (AbstractBundleState bundleState : bundles) {
                if (bundleState instanceof HostBundleState) {
                    HostBundleState hostBundle = (HostBundleState) bundleState;
                    if (hostBundle.getStartLevel() == startLevel) {
                        try {
                            bundleState.stopInternal(Bundle.STOP_TRANSIENT);
                        } catch (Throwable e) {
                            FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
                            eventsPlugin.fireFrameworkEvent(bundleState, FrameworkEvent.ERROR, e);
                        }
                    }
                }
            }
            startLevel--;
        }
    }
}
