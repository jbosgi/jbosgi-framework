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
import org.jboss.osgi.framework.internal.StartLevelImpl;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.startlevel.StartLevel;

/**
 * An implementation of the {@link StartLevel} service.
 *
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @author Thomas.Diesler@jboss.com
 */
public class StartLevelPlugin extends ExecutorServicePlugin<StartLevelSupport> implements StartLevelSupport {

    private final InjectedValue<BundleContext> injectedSystemContext = new InjectedValue<BundleContext>();
    private final InjectedValue<FrameworkEvents> injectedFrameworkEvents = new InjectedValue<FrameworkEvents>();
    private ServiceRegistration registration;
    private StartLevelSupport startLevelSupport;

    public StartLevelPlugin() {
        super(Services.START_LEVEL, "StartLevel Thread");
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<StartLevelSupport> builder) {
        super.addServiceDependencies(builder);
        builder.addDependency(IntegrationServices.SYSTEM_CONTEXT_INTERNAL, BundleContext.class, injectedSystemContext);
        builder.addDependency(IntegrationServices.FRAMEWORK_EVENTS, FrameworkEvents.class, injectedFrameworkEvents);
        builder.addDependency(Services.FRAMEWORK_CREATE);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        FrameworkEvents events = injectedFrameworkEvents.getValue();
        startLevelSupport = new StartLevelImpl(getBundleManager(), events, getExecutorService(), new AtomicBoolean(false));
        BundleContext systemContext = injectedSystemContext.getValue();
        registration = systemContext.registerService(StartLevel.class.getName(), this, null);
    }

    @Override
    public void stop(StopContext context) {
        registration.unregister();
        super.stop(context);
    }

    @Override
    public StartLevelSupport getValue() {
        return this;
    }

    @Override
    public void enableImmediateExecution(boolean enable) {
        startLevelSupport.enableImmediateExecution(enable);
    }

    public void setBundlePersistentlyStarted(XBundle bundle, boolean started) {
        startLevelSupport.setBundlePersistentlyStarted(bundle, started);
    }

    public void decreaseStartLevel(int level) {
        startLevelSupport.decreaseStartLevel(level);
    }

    public void increaseStartLevel(int level) {
        startLevelSupport.increaseStartLevel(level);
    }

    public int getStartLevel() {
        return startLevelSupport.getStartLevel();
    }

    public void setStartLevel(int startlevel) {
        startLevelSupport.setStartLevel(startlevel);
    }

    public int getBundleStartLevel(Bundle bundle) {
        return startLevelSupport.getBundleStartLevel(bundle);
    }

    public void setBundleStartLevel(Bundle bundle, int startlevel) {
        startLevelSupport.setBundleStartLevel(bundle, startlevel);
    }

    public int getInitialBundleStartLevel() {
        return startLevelSupport.getInitialBundleStartLevel();
    }

    public void setInitialBundleStartLevel(int startlevel) {
        startLevelSupport.setInitialBundleStartLevel(startlevel);
    }

    public boolean isBundlePersistentlyStarted(Bundle bundle) {
        return startLevelSupport.isBundlePersistentlyStarted(bundle);
    }

    public boolean isBundleActivationPolicyUsed(Bundle bundle) {
        return startLevelSupport.isBundleActivationPolicyUsed(bundle);
    }

}
