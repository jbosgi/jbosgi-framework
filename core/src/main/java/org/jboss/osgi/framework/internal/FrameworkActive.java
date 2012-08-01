package org.jboss.osgi.framework.internal;
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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;

import java.util.Collections;

import org.jboss.msc.service.AbstractService;
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
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.service.resolver.ResolutionException;

/**
 * A service that represents the ACTIVE state of the {@link Framework}.
 *
 * See {@link Framework#start()} for details.
 *
 * The framework service dependency hierarchy
 *
 * <code>
 * {@link FrameworkActive}
 *         +---{@link FrameworkInit}
 *             +---{@link DefaultPersistentBundlesInstall}
 *                 +---{@link DefaultBootstrapBundlesInstall}
 *             +---{@link FrameworkCoreServices}
 *                 +---{@link LifecycleInterceptorPlugin}
 *                 +---{@link PackageAdminPlugin}
 *                 +---{@link StartLevelPlugin}
 *                 +---{@link DefaultSystemServicesPlugin}
 *                 +---{@link URLHandlerPlugin}
 *                 +---{@link DefaultBundleInstallPlugin}
 *                     +---{@link FrameworkCreate}
 *                         +---{@link DefaultStorageStatePlugin}
 *                         +---{@link DeploymentFactoryPlugin}
 *                         +---{@link ResolverPlugin}
 *                         |   +---{@link NativeCodePlugin}
 *                         +---{@link ServiceManagerPlugin}
 *                             +---{@link ModuleManagerPlugin}
 *                             |   +---{@link DefaultModuleLoaderPlugin}
 *                             +---{@link FrameworkEventsPlugin}
 *                                 +---{@link SystemContextService}
 *                                     +---{@link SystemBundleService}
 *                                         +---{@link DefaultFrameworkModulePlugin}
 *                                         |   +---{@link DefaultSystemPathsPlugin}
 *                                         +---{@link BundleStoragePlugin}
 *                                             +---{@link BundleManagerPlugin}
 *                                                 +---{@link EnvironmentPlugin}
 * </code>
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
public final class FrameworkActive extends AbstractFrameworkService {

    private final InjectedValue<BundleManagerPlugin> injectedBundleManager = new InjectedValue<BundleManagerPlugin>();
    private final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();

    static void addService(ServiceTarget serviceTarget) {
        FrameworkActive service = new FrameworkActive();
        ServiceBuilder<FrameworkState> builder = serviceTarget.addService(InternalServices.FRAMEWORK_STATE_ACTIVE, service);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManagerPlugin.class, service.injectedBundleManager);
        builder.addDependency(InternalServices.FRAMEWORK_STATE_INIT, FrameworkState.class, service.injectedFramework);
        builder.setInitialMode(Mode.ON_DEMAND);

        // Add the public FRAMEWORK_ACTIVE service that provides the BundleContext
        FrameworkActivated.addService(serviceTarget);

        builder.install();
    }

    private FrameworkActive() {
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
    public void start(StartContext context) throws StartException {
        super.start(context);
        try {
            // Resolve the system bundle
            ResolverPlugin resolverPlugin = getValue().getResolverPlugin();
            BundleRevision sysrev = getSystemBundle().getBundleRevision();
            resolverPlugin.resolveAndApply(Collections.singleton(sysrev), null);

            // This Framework's state is set to ACTIVE
            getSystemBundle().changeState(Bundle.ACTIVE);

            // Increase to initial start level
            StartLevelPlugin startLevelPlugin = getValue().getCoreServices().getStartLevel();
            startLevelPlugin.increaseStartLevel(getBeginningStartLevel());

            // Mark Framework as active in the bundle manager
            BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
            bundleManager.injectedFrameworkActive.inject(Boolean.TRUE);

            // A framework event of type STARTED is fired
            FrameworkEventsPlugin eventsPlugin = getValue().getFrameworkEventsPlugin();
            eventsPlugin.fireFrameworkEvent(getSystemBundle(), FrameworkEvent.STARTED, null);

            LOGGER.infoFrameworkStarted();
        } catch (ResolutionException ex) {
            throw new StartException(ex);
        }
    }

    @Override
    public void stop(StopContext context) {
        BundleManagerPlugin bundleManager = injectedBundleManager.getValue();
        bundleManager.injectedFrameworkActive.uninject();
        super.stop(context);
    }

    @Override
    public FrameworkState getValue() {
        return injectedFramework.getValue();
    }

    private int getBeginningStartLevel() {
        String levelSpec = (String) getBundleManager().getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
        if (levelSpec != null) {
            try {
                return Integer.parseInt(levelSpec);
            } catch (NumberFormatException nfe) {
                LOGGER.errorInvalidBeginningStartLevel(levelSpec);
            }
        }
        return 1;
    }

    static class FrameworkActivated extends AbstractService<BundleContext> {

        final InjectedValue<BundleContext> injectedBundleContext = new InjectedValue<BundleContext>();

        static void addService(ServiceTarget serviceTarget) {
            FrameworkActivated service = new FrameworkActivated();
            ServiceBuilder<BundleContext> builder = serviceTarget.addService(Services.FRAMEWORK_ACTIVE, service);
            builder.addDependency(Services.FRAMEWORK_INIT, BundleContext.class, service.injectedBundleContext);
            builder.addDependency(InternalServices.FRAMEWORK_STATE_ACTIVE);
            builder.setInitialMode(Mode.LAZY);
            builder.install();
        }

        @Override
        public BundleContext getValue() throws IllegalStateException {
            return injectedBundleContext.getValue();
        }
    }
}