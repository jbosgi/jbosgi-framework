package org.jboss.osgi.framework.spi;
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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class BootstrapBundlesActivate<T> extends BootstrapBundlesService<T> {

    private final Set<XBundle> resolvedBundles;

    public BootstrapBundlesActivate(ServiceName baseName, Set<XBundle> resolvedBundles) {
        super(baseName, IntegrationServices.BootstrapPhase.ACTIVATE);
        this.resolvedBundles = resolvedBundles;
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<T> builder) {
        builder.addDependencies(getPreviousService());
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        super.start(startContext);

        // Sort the bundles by Id
        List<XBundle> bundles = new ArrayList<XBundle>(resolvedBundles);
        Collections.sort(bundles, new Comparator<XBundle>(){
            @Override
            public int compare(XBundle o1, XBundle o2) {
                return (int) (o1.getBundleId() - o2.getBundleId());
            }
        });

        // Start the resolved bundles
        for (XBundle bundle : bundles) {
            try {
                bundle.start(Bundle.START_ACTIVATION_POLICY);
            } catch (BundleException ex) {
                LOGGER.errorCannotStartBundle(ex, bundle);
            }
        }

        // We are done
        installCompleteService(startContext.getChildTarget());
    }

    protected ServiceController<T> installCompleteService(ServiceTarget serviceTarget) {
        return new BootstrapBundlesComplete<T>(getServiceName().getParent()).install(serviceTarget, getServiceListener());
    }
}