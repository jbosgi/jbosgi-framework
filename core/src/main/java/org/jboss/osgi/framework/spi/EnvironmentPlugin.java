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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.framework.internal.EnvironmentImpl;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;

/**
 * The default {@link XEnvironment} plugin.
 *
 * @author thomas.diesler@jboss.com
 * @since 15-Feb-2012
 */
public class EnvironmentPlugin extends AbstractIntegrationService<XEnvironment> implements XEnvironment {

    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();
    private XEnvironment environment;

    public EnvironmentPlugin() {
        super(IntegrationServices.ENVIRONMENT);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<XEnvironment> builder) {
        builder.addDependency(IntegrationServices.LOCK_MANAGER, LockManager.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        environment = new EnvironmentImpl(injectedLockManager.getValue());
    }

    @Override
    public XEnvironment getValue() {
        return this;
    }

    @Override
    public void installResources(XResource... resources) {
        environment.installResources(resources);
    }

    @Override
    public void uninstallResources(XResource... resources) {
        environment.uninstallResources(resources);
    }

    @Override
    public void refreshResources(XResource... resources) {
        environment.refreshResources(resources);
    }

    @Override
    public Collection<XResource> getResources(String... types) {
        return environment.getResources(types);
    }

    @Override
    public Long nextResourceIdentifier(Long value, String symbolicName) {
        return environment.nextResourceIdentifier(value, symbolicName);
    }

    @Override
    public List<Capability> findProviders(Requirement req) {
        return environment.findProviders(req);
    }

    @Override
    public Map<Resource, Wiring> updateWiring(Map<Resource, List<Wire>> delta) {
        return environment.updateWiring(delta);
    }

    @Override
    public Wiring createWiring(XResource res, List<Wire> required, List<Wire> provided) {
        return environment.createWiring(res, required, provided);
    }

    @Override
    public Map<Resource, Wiring> getWirings() {
        return environment.getWirings();
    }
}
