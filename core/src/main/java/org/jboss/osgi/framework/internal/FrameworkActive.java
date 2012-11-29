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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.BundleLifecyclePlugin;
import org.jboss.osgi.framework.spi.BundleStoragePlugin;
import org.jboss.osgi.framework.spi.DeploymentProviderPlugin;
import org.jboss.osgi.framework.spi.EnvironmentPlugin;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.FrameworkModuleLoaderPlugin;
import org.jboss.osgi.framework.spi.FrameworkModuleProviderPlugin;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.LifecycleInterceptorPlugin;
import org.jboss.osgi.framework.spi.LockManagerPlugin;
import org.jboss.osgi.framework.spi.ModuleManagerPlugin;
import org.jboss.osgi.framework.spi.NativeCodePlugin;
import org.jboss.osgi.framework.spi.PackageAdminPlugin;
import org.jboss.osgi.framework.spi.ResolverPlugin;
import org.jboss.osgi.framework.spi.ServiceManagerPlugin;
import org.jboss.osgi.framework.spi.StartLevelPlugin;
import org.jboss.osgi.framework.spi.StartLevelSupport;
import org.jboss.osgi.framework.spi.SystemPathsPlugin;
import org.jboss.osgi.framework.spi.SystemServicesPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

/**
 * A service that represents the ACTIVE state of the {@link Framework}.
 *
 * See {@link Framework#start()} for details.
 *
 * The framework service dependency hierarchy
 *
 * <code>
 * {@link FrameworkActive}
 * +---{@link FrameworkInit}
 *     +---{@link CoreServices}
 *     |   +---{@link BundleLifecyclePlugin}
 *     |   +---{@link LifecycleInterceptorPlugin}
 *     |   +---{@link PackageAdminPlugin}
 *     |   +---{@link StartLevelPlugin
 *     |   +---{@link SystemServicesPlugin}
 *     |   +---{@link URLHandlerPlugin}
 *     +---{@link PersistentBundlesInstallPlugin}
 *         +---{@link BootstrapBundlesInstallPlugin}
 *             +---{@link FrameworkCreate}
 *                 +---{@link DeploymentProviderPlugin}
 *                 +---{@link ResolverPlugin}
 *                 |   +---{@link NativeCodePlugin}
 *                 +---{@link ServiceManagerPlugin}
 *                     +---{@link FrameworkEventsPlugin}
 *                         +---{@link SystemContext}
 *                             +---{@link SystemBundle}
 *                                 +---{@link BundleStoragePlugin}
 *                                 +---{@link SystemPathsPlugin}
 *                                 +---{@link ModuleManagerPlugin}
 *                                     +---{@link FrameworkModuleLoaderPlugin}
 *                                     +---{@link FrameworkModuleProviderPlugin}
 *                                         +---{@link BundleManagerPlugin}
 *                                             +---{@link EnvironmentPlugin}
 *                                                 +---{@link LockManagerPlugin}
 *
 *
 * </code>
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class FrameworkActive extends AbstractFrameworkService {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();

    FrameworkActive() {
        super(IntegrationServices.FRAMEWORK_ACTIVE_INTERNAL);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<FrameworkState> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, injectedBundleManager);
        builder.addDependency(IntegrationServices.FRAMEWORK_INIT_INTERNAL, FrameworkState.class, injectedFramework);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    /**
     * Start this Framework.
     *
     * The following steps are taken to start this Framework:
     *
     * - If this Framework is not in the {@link Bundle#STARTING} state, {@link Framework#init()} is called
     * - All installed bundles must be started
     * - The start level of this Framework is moved to the FRAMEWORK_BEGINNING_STARTLEVEL
     *
     * Any exceptions that occur during bundle starting must be wrapped in a {@link BundleException} and then published as a
     * framework event of type {@link FrameworkEvent#ERROR}
     *
     * - This Framework's state is set to {@link Bundle#ACTIVE}.
     * - A framework event of type {@link FrameworkEvent#STARTED} is fired
     */
    @Override
    protected FrameworkState createServiceValue(StartContext startContext) throws StartException {
        // This Framework's state is set to ACTIVE
        FrameworkState frameworkState = injectedFramework.getValue();
        BundleManagerPlugin bundleManager = frameworkState.getBundleManagerPlugin();
        SystemBundleState systemBundle = frameworkState.getSystemBundle();
        systemBundle.changeState(Bundle.ACTIVE);

        // Increase to initial start level
        StartLevelSupport startLevelPlugin = frameworkState.getCoreServices().getStartLevelSupport();
        startLevelPlugin.increaseStartLevel(getBeginningStartLevel(bundleManager));

        // Mark Framework as active in the bundle manager
        bundleManager.injectedFrameworkActive.inject(Boolean.TRUE);

        // A framework event of type STARTED is fired
        FrameworkEvents eventsPlugin = frameworkState.getFrameworkEvents();
        eventsPlugin.fireFrameworkEvent(systemBundle, FrameworkEvent.STARTED, null);

        LOGGER.infoFrameworkStarted();
        return frameworkState;
    }

    @Override
    public void stop(StopContext context) {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        bundleManager.injectedFrameworkActive.uninject();
        super.stop(context);
    }

    private int getBeginningStartLevel(BundleManagerPlugin bundleManager) {
        String levelSpec = (String) bundleManager.getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
        if (levelSpec != null) {
            try {
                return Integer.parseInt(levelSpec);
            } catch (NumberFormatException nfe) {
                LOGGER.errorInvalidBeginningStartLevel(levelSpec);
            }
        }
        return 1;
    }

    static class FrameworkActivated extends AbstractIntegrationService<BundleContext> {

        private final InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();
        private final Mode initialMode;

        FrameworkActivated(Mode initialMode) {
            super(Services.FRAMEWORK_ACTIVE);
            this.initialMode = initialMode;
        }

        @Override
        protected void addServiceDependencies(ServiceBuilder<BundleContext> builder) {
            builder.addDependency(Services.FRAMEWORK_INIT, BundleContext.class, injectedBundleContext);
            builder.addDependency(IntegrationServices.FRAMEWORK_ACTIVE_INTERNAL);
            builder.setInitialMode(initialMode);
        }

        @Override
        protected BundleContext createServiceValue(StartContext startContext) throws StartException {
            return injectedBundleContext.getValue();
        }
    }
}