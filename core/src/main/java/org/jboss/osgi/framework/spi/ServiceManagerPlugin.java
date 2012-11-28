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

import java.util.Dictionary;
import java.util.List;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.internal.ServiceManagerImpl;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceException;

/**
 * A plugin that manages OSGi services
 *
 * @author thomas.diesler@jboss.com
 * @since 18-Aug-2009
 */
public class ServiceManagerPlugin extends AbstractIntegrationService<ServiceManager> implements ServiceManager {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private final InjectedValue<FrameworkEvents> injectedFrameworkEvents = new InjectedValue<FrameworkEvents>();
    private final InjectedValue<ModuleManager> injectedModuleManager = new InjectedValue<ModuleManager>();
    private ServiceManager serviceManager;

    public ServiceManagerPlugin() {
        super(IntegrationServices.SERVICE_MANAGER);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<ServiceManager> builder) {
        builder.addDependency(Services.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.addDependency(IntegrationServices.FRAMEWORK_EVENTS, FrameworkEvents.class, injectedFrameworkEvents);
        builder.addDependency(IntegrationServices.MODULE_MANGER, ModuleManager.class, injectedModuleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        serviceManager = new ServiceManagerImpl(injectedFrameworkEvents.getValue());
    }

    @Override
    public ServiceManager getValue() {
        return this;
    }

    @SuppressWarnings("rawtypes")
    public ServiceState registerService(XBundle bundle, String[] classNames, Object serviceValue, Dictionary properties) {
        return serviceManager.registerService(bundle, classNames, serviceValue, properties);
    }

    public ServiceState getServiceReference(XBundle bundle, String clazz) {
        return serviceManager.getServiceReference(bundle, clazz);
    }

    public List<ServiceState> getServiceReferences(XBundle bundle, String clazz, String filterStr, boolean checkAssignable) throws InvalidSyntaxException {
        return serviceManager.getServiceReferences(bundle, clazz, filterStr, checkAssignable);
    }

    public Object getService(XBundle bundle, ServiceState serviceState) {
        return serviceManager.getService(bundle, serviceState);
    }

    public void unregisterService(ServiceState serviceState) {
        serviceManager.unregisterService(serviceState);
    }

    public boolean ungetService(XBundle bundle, ServiceState serviceState) {
        return serviceManager.ungetService(bundle, serviceState);
    }

    public void fireFrameworkEvent(XBundle bundle, int type, ServiceException ex) {
        serviceManager.fireFrameworkEvent(bundle, type, ex);
    }

    public void fireServiceEvent(XBundle bundle, int type, ServiceState serviceState) {
        serviceManager.fireServiceEvent(bundle, type, serviceState);
    }

}