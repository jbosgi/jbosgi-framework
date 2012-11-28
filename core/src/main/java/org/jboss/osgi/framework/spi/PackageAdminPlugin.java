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

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.PackageAdminImpl;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;
import org.osgi.service.startlevel.StartLevel;

/**
 * An implementation of the {@link PackageAdmin} service.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 06-Jul-2010
 */
public class PackageAdminPlugin extends ExecutorServicePlugin<PackageAdminSupport> implements PackageAdminSupport {

    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<FrameworkEvents> injectedFrameworkEvents = new InjectedValue<FrameworkEvents>();
    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private final InjectedValue<ModuleManager> injectedModuleManager = new InjectedValue<ModuleManager>();
    private final InjectedValue<XResolver> injectedResolver = new InjectedValue<XResolver>();
    private final InjectedValue<StartLevel> injectedStartLevel = new InjectedValue<StartLevel>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    private PackageAdminSupport packageAdmin;
    private ServiceRegistration registration;

    public PackageAdminPlugin() {
        super(Services.PACKAGE_ADMIN, "PackageAdmin Refresh Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<PackageAdminSupport> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(Services.START_LEVEL, StartLevel.class, injectedStartLevel);
        builder.addDependency(IntegrationServices.FRAMEWORK_EVENTS, FrameworkEvents.class, injectedFrameworkEvents);
        builder.addDependency(IntegrationServices.MODULE_MANGER, ModuleManager.class, injectedModuleManager);
        builder.addDependency(IntegrationServices.LOCK_MANAGER, LockManager.class, injectedLockManager);
        builder.addDependency(Services.FRAMEWORK_CREATE, BundleContext.class, injectedSystemContext);
        builder.addDependency(Services.RESOLVER, XResolver.class, injectedResolver);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        XEnvironment env = injectedEnvironment.getValue();
        FrameworkEvents events = injectedFrameworkEvents.getValue();
        ModuleManager moduleManager = injectedModuleManager.getValue();
        XResolver resolver = injectedResolver.getValue();
        StartLevel startLevel = injectedStartLevel.getValue();
        LockManager lockManager = injectedLockManager.getValue();
        packageAdmin = new PackageAdminImpl(getBundleManager(), env, events, moduleManager, resolver, startLevel, lockManager, getExecutorService(), new AtomicBoolean(false));
        BundleContext systemContext = injectedSystemContext.getValue();
        registration = systemContext.registerService(PackageAdmin.class.getName(), this, null);
    }

    @Override
    public void stop(StopContext context) {
        registration.unregister();
        super.stop(context);
    }

    @Override
    public PackageAdminSupport getValue() {
        return this;
    }

    public void enableImmediateExecution(boolean enable) {
        packageAdmin.enableImmediateExecution(enable);
    }

    public ExportedPackage[] getExportedPackages(Bundle bundle) {
        return packageAdmin.getExportedPackages(bundle);
    }

    public ExportedPackage[] getExportedPackages(String name) {
        return packageAdmin.getExportedPackages(name);
    }

    public ExportedPackage getExportedPackage(String name) {
        return packageAdmin.getExportedPackage(name);
    }

    public void refreshPackages(Bundle[] bundles) {
        packageAdmin.refreshPackages(bundles);
    }

    public boolean resolveBundles(Bundle[] bundles) {
        return packageAdmin.resolveBundles(bundles);
    }

    public RequiredBundle[] getRequiredBundles(String symbolicName) {
        return packageAdmin.getRequiredBundles(symbolicName);
    }

    public Bundle[] getBundles(String symbolicName, String versionRange) {
        return packageAdmin.getBundles(symbolicName, versionRange);
    }

    public Bundle[] getFragments(Bundle bundle) {
        return packageAdmin.getFragments(bundle);
    }

    public Bundle[] getHosts(Bundle bundle) {
        return packageAdmin.getHosts(bundle);
    }

    @SuppressWarnings("rawtypes")
    public Bundle getBundle(Class clazz) {
        return packageAdmin.getBundle(clazz);
    }

    public int getBundleType(Bundle bundle) {
        return packageAdmin.getBundleType(bundle);
    }
}
