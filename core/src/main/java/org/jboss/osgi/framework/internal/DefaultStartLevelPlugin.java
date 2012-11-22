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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.framework.spi.StartLevelPlugin;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
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
 * @author Thomas.Diesler@jboss.com
 */
final class DefaultStartLevelPlugin extends ExecutorServicePlugin<StartLevelPlugin> implements StartLevelPlugin {

    private final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    private final InjectedValue<FrameworkEventsPlugin> injectedFrameworkEvents = new InjectedValue<FrameworkEventsPlugin>();

    private AtomicInteger initialBundleStartLevel = new AtomicInteger(1);
    private ServiceRegistration registration;
    private AtomicInteger startLevel = new AtomicInteger(0);

    DefaultStartLevelPlugin() {
        super(Services.START_LEVEL, "StartLevel Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<StartLevelPlugin> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(InternalServices.FRAMEWORK_EVENTS_PLUGIN, FrameworkEventsPlugin.class, injectedFrameworkEvents);
        builder.addDependency(InternalServices.SYSTEM_BUNDLE, SystemBundleState.class, injectedSystemBundle);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
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
    }

    @Override
    public StartLevelPlugin getValue() {
        return this;
    }

    @Override
    public void enableImmediateExecution(boolean enable) {
        super.enableImmediateExecution(enable);
    }

    @Override
    public int getStartLevel() {
        return startLevel.get();
    }

    @Override
    public synchronized void setStartLevel(final int level) {
        final FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
        final Bundle sysbundle = injectedSystemBundle.getValue();
        if (level > getStartLevel()) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    LOGGER.infoIncreasingStartLevel(getStartLevel(), level);
                    increaseStartLevel(level);
                    eventsPlugin.fireFrameworkEvent(sysbundle, FrameworkEvent.STARTLEVEL_CHANGED, null);
                }
            };
            ExecutorService executorService = getExecutorService();
            if (!executorService.isShutdown()) {
                executorService.execute(runner);
            }
        } else if (level < getStartLevel()) {
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    LOGGER.infoDecreasingStartLevel(getStartLevel(), level);
                    decreaseStartLevel(level);
                    eventsPlugin.fireFrameworkEvent(sysbundle, FrameworkEvent.STARTLEVEL_CHANGED, null);
                }
            };
            ExecutorService executorService = getExecutorService();
            if (!executorService.isShutdown()) {
                executorService.execute(runner);
            }
        }
    }

    @Override
    public int getBundleStartLevel(Bundle bundle) {
        return getBundleStartLevelState(bundle).getLevel();
    }

    @Override
    public void setBundleStartLevel(final Bundle bundle, final int level) {
        if (bundle.getBundleId() == 0)
            throw MESSAGES.illegalArgumentStartLevelOnSystemBundles();

        final XBundle hostBundle = (XBundle) bundle;
        final FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
        getBundleStartLevelState(hostBundle).setLevel(level);

        if (level <= getStartLevel()) {
            // If the bundle is active or starting, we don't need to start it again
            if ((bundle.getState() & (Bundle.ACTIVE | Bundle.STARTING)) > 0)
                return;

            if (isBundlePersistentlyStarted(bundle)) {
                LOGGER.infoStartingBundleDueToStartLevel(hostBundle);
                Runnable runner = new Runnable() {
                    @Override
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
                };

                ExecutorService executorService = getExecutorService();
                if (!executorService.isShutdown()) {
                    executorService.execute(runner);
                }
            }
        } else {
            // If the bundle is not active we don't need to stop it
            if ((bundle.getState() & (Bundle.ACTIVE | Bundle.STARTING)) == 0)
                return;

            LOGGER.infoStoppingBundleDueToStartLevel(hostBundle);
            Runnable runner = new Runnable() {
                @Override
                public void run() {
                    try {
                        hostBundle.stop(Bundle.STOP_TRANSIENT);
                    } catch (BundleException e) {
                        eventsPlugin.fireFrameworkEvent(hostBundle, FrameworkEvent.ERROR, e);
                    }
                }
            };

            ExecutorService executorService = getExecutorService();
            if (!executorService.isShutdown()) {
                executorService.execute(runner);
            }
        }
    }

    @Override
    public int getInitialBundleStartLevel() {
        return initialBundleStartLevel.get();
    }

    @Override
    public void setInitialBundleStartLevel(int startlevel) {
        initialBundleStartLevel.set(startlevel);
    }

    @Override
    public boolean isBundlePersistentlyStarted(Bundle bundle) {
        return getBundleStartLevelState(bundle).isStarted();
    }

    @Override
    public void setBundlePersistentlyStarted(XBundle bundle, boolean started) {
        getBundleStartLevelState(bundle).setStarted(started);
    }

    @Override
    public boolean isBundleActivationPolicyUsed(Bundle bundle) {
        boolean result = false;
        if (bundle instanceof AbstractBundleState) {
            AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
            StorageState storageState = bundleState.getStorageState();
            result = storageState.isBundleActivationPolicyUsed();
        }
        return result;
    }

    /**
     * Increases the Start Level of the Framework in the current thread.
     *
     * @param level the target Start Level to which the Framework should move.
     */
    @Override
    public synchronized void increaseStartLevel(int level) {

        // Sort the bundles after their bundle id
        List<XBundle> bundles = new ArrayList<XBundle>(getBundleManager().getBundles());
        Comparator<XBundle> comparator = new Comparator<XBundle>() {
            @Override
            public int compare(XBundle b1, XBundle b2) {
                return (int) (b1.getBundleId() - b2.getBundleId());
            }
        };
        Collections.sort(bundles, comparator);

        while (startLevel.get() < level) {
            startLevel.incrementAndGet();
            LOGGER.infoStartingBundlesForStartLevel(startLevel.get());
            for (XBundle bundle : bundles) {
                if (bundle.getBundleId() == 0 || bundle.isFragment())
                    continue;

                BundleStartLevelState state = getBundleStartLevelState(bundle);
                if (state.getLevel() == startLevel.get() && state.isStarted()) {
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
    @Override
    public synchronized void decreaseStartLevel(int level) {
        while (startLevel.get() > level) {
            LOGGER.infoStoppingBundlesForStartLevel(level);

            // Sort the bundles after their bundle id
            List<XBundle> bundles = new ArrayList<XBundle>(getBundleManager().getBundles());
            Comparator<XBundle> comparator = new Comparator<XBundle>() {
                @Override
                public int compare(XBundle b1, XBundle b2) {
                    return (int) (b1.getBundleId() - b2.getBundleId());
                }
            };
            Collections.sort(bundles, comparator);
            Collections.reverse(bundles);

            for (XBundle bundle : bundles) {
                if (bundle.getBundleId() == 0 || bundle.isFragment())
                    continue;

                BundleStartLevelState state = getBundleStartLevelState(bundle);
                if (state.getLevel() == startLevel.get()) {
                    try {
                        bundle.stop(Bundle.STOP_TRANSIENT);
                    } catch (Throwable e) {
                        FrameworkEventsPlugin eventsPlugin = injectedFrameworkEvents.getValue();
                        eventsPlugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, e);
                    }
                }
            }
            startLevel.decrementAndGet();
        }
    }

    private BundleStartLevelState getBundleStartLevelState(Bundle bundle) {
        if (bundle instanceof Framework)
            return new BundleStartLevelState(bundle);

        XBundle bundleState = (XBundle) bundle;
        BundleStartLevelState state = bundleState.getAttachment(BundleStartLevelState.class);
        if (state == null) {
            state = new BundleStartLevelState(bundle);
            bundleState.addAttachment(BundleStartLevelState.class, state);
        }
        return state;
    }

    class BundleStartLevelState {
        final Bundle bundle;
        boolean started;
        int level;

        public BundleStartLevelState(Bundle bundle) {
            this.bundle = bundle;
            if (bundle.getBundleId() > 0) {
                XBundle bundleState = (XBundle) bundle;
                level = !bundleState.isFragment() ? getInitialBundleStartLevel() : 0;
                if (bundleState instanceof HostBundleState) {
                    XBundleRevision brev = bundleState.getBundleRevision();
                    InternalStorageState storageState = ((BundleStateRevision)brev).getStorageState();
                    level = storageState.getStartLevel();
                }
            }
        }

        int getLevel() {
            return level;
        }

        void setLevel(int level) {
            this.level = level;
            if (bundle instanceof HostBundleState) {
                XBundleRevision brev = ((XBundle)bundle).getBundleRevision();
                InternalStorageState storageState = ((BundleStateRevision)brev).getStorageState();
                storageState.setStartLevel(level);
            }
        }

        boolean isStarted() {
            return started;
        }

        void setStarted(boolean started) {
            this.started = started;
            if (bundle instanceof HostBundleState) {
                XBundleRevision brev = ((XBundle)bundle).getBundleRevision();
                InternalStorageState storageState = ((BundleStateRevision)brev).getStorageState();
                storageState.setPersistentlyStarted(started);
            }
        }
    }
}
