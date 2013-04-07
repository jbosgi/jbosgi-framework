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
package org.jboss.osgi.framework.spi;

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.resolver.ResolutionException;

/**
 * An integration point for the bundle lifecycle.
 *
 * @author thomas.diesler@jboss.com
 * @since 19-Oct-2009
 */
public class BundleLifecyclePlugin extends AbstractIntegrationService<BundleLifecycle> {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<XResolver> injectedResolver = new InjectedValue<XResolver>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();

    public BundleLifecyclePlugin() {
        super(IntegrationServices.BUNDLE_LIFECYCLE_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleLifecycle> builder) {
        builder.addDependency(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(Services.RESOLVER, XResolver.class, injectedResolver);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    protected BundleLifecycle createServiceValue(StartContext startContext) throws StartException {
        BundleManager bundleManager = injectedBundleManager.getValue();
        XResolver resolver = injectedResolver.getValue();
        XEnvironment environment = injectedEnvironment.getValue();
        LockManager lockManager = injectedLockManager.getValue();
        return new BundleLifecycleImpl(bundleManager, environment, resolver, lockManager);
    }

    static class BundleLifecycleImpl implements BundleLifecycle {

        private final BundleManager bundleManager;
        private final XEnvironment environment;
        private final XResolver resolver;
        private final LockManager lockManager;

        BundleLifecycleImpl(BundleManager bundleManager, XEnvironment environment, XResolver resolver, LockManager lockManager) {
            this.bundleManager = bundleManager;
            this.environment = environment;
            this.resolver = resolver;
            this.lockManager = lockManager;
        }

        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public ServiceController<? extends XBundleRevision> createBundleRevision(BundleContext context, Deployment dep) throws BundleException {
            ServiceController<? extends XBundleRevision> controller = bundleManager.createBundleRevision(context, dep, null, null);
            FutureServiceValue<XBundleRevision> future = new FutureServiceValue(controller);
            try {
                future.get(30, TimeUnit.SECONDS);
                return controller;
            } catch (Exception ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof BundleException) {
                    throw (BundleException) cause;
                }
                throw MESSAGES.cannotInstallBundleRevisionFromDeployment(ex, dep);
            }
        }

        @Override
        public void resolve(XBundle bundle) throws ResolutionException {
            Set<XBundleRevision> mandatory = Collections.singleton(bundle.getBundleRevision());
            XResolveContext context = resolver.createResolveContext(environment, mandatory, null);
            resolver.resolveAndApply(context);
        }

        @Override
        public void start(XBundle bundle, int options) throws BundleException {
            bundleManager.startBundle(bundle, options);
        }

        @Override
        public void stop(XBundle bundle, int options) throws BundleException {
            bundleManager.stopBundle(bundle, options);
        }

        @Override
        public void update(XBundle bundle, InputStream input) throws BundleException {
            bundleManager.updateBundle(bundle, input);
        }

        @Override
        public void uninstall(XBundle bundle, int options) throws BundleException {
            bundleManager.uninstallBundle(bundle, options);
        }

        @Override
        public LockContext lockBundle(Method method, XBundle bundle, LockableItem[] items) {
            return lockManager.lockItems(method, items);
        }

        @Override
        public void unlockBundle(XBundle bundle, LockContext context) {
            lockManager.unlockItems(context);
        }
    }
}