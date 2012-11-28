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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.internal.NativeCodeImpl;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.BundleException;

/**
 * The bundle native code plugin
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 11-Aug-2010
 */
public class NativeCodePlugin extends AbstractIntegrationService<NativeCode> implements NativeCode {

    private final InjectedValue<BundleManager> injectedBundleManager = new InjectedValue<BundleManager>();
    private NativeCode nativeCode;

    public NativeCodePlugin() {
        super(IntegrationServices.NATIVE_CODE_PLUGIN);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<NativeCode> builder) {
        builder.addDependency(org.jboss.osgi.framework.spi.IntegrationServices.BUNDLE_MANAGER, BundleManager.class, injectedBundleManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        nativeCode = new NativeCodeImpl(injectedBundleManager.getValue());
    }

    @Override
    public NativeCode getValue() {
        return this;
    }

    @Override
    public void deployNativeCode(Deployment dep) {
        nativeCode.deployNativeCode(dep);
    }

    @Override
    public void resolveNativeCode(XBundleRevision bev) throws BundleException {
        nativeCode.resolveNativeCode(bev);
    }
}