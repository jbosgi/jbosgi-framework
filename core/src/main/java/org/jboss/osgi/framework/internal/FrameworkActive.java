/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

/**
 * A service that represents the ACTIVE state of the {@link Framework}.
 * 
 *  See {@link Framework#start()} for details.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
final class FrameworkActive extends FrameworkService<FrameworkActive> {

    // Provide logging
    static final Logger log = Logger.getLogger(FrameworkActive.class);

    private final InjectedValue<FrameworkInit> injectedFramework = new InjectedValue<FrameworkInit>();
    
    static void addService(ServiceTarget serviceTarget) {
        FrameworkActive service = new FrameworkActive();
        ServiceBuilder<FrameworkActive> builder = serviceTarget.addService(Services.FRAMEWORK_ACTIVE, service);
        builder.addDependency(Services.FRAMEWORK_INIT, FrameworkInit.class, service.injectedFramework);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }
    
    private FrameworkActive() {
    }

    /**
     * Start this Framework.
     *
     * The following steps are taken to start this Framework:
     *
     * - If this Framework is not in the {@link #STARTING} state, {@link #init()} is called 
     * - All installed bundles must be started 
     * - The start level of this Framework is moved to the FRAMEWORK_BEGINNING_STARTLEVEL
     *
     * Any exceptions that occur during bundle starting must be wrapped in a {@link BundleException} and then published as a
     * framework event of type {@link FrameworkEvent#ERROR}
     *
     * - This Framework's state is set to {@link #ACTIVE}. 
     * - A framework event of type {@link FrameworkEvent#STARTED} is fired
     */
    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        try {
            // Resolve the system bundle
            SystemBundleState systemBundle = getSystemBundle();
            ResolverPlugin resolverPlugin = getFrameworkState().getResolverPlugin();
            resolverPlugin.resolve(systemBundle.getResolverModule());

            // This Framework's state is set to ACTIVE
            systemBundle.changeState(Bundle.ACTIVE);

            // Increase to initial start level
            StartLevelPlugin startLevelPlugin = getFrameworkState().getCoreServices().getStartLevelPlugin();
            startLevelPlugin.increaseStartLevel(getBeginningStartLevel());

            // A framework event of type STARTED is fired
            FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
            eventsPlugin.fireFrameworkEvent(getSystemBundle(), FrameworkEvent.STARTED, null);

            log.infof("Framework started");
        } catch (BundleException ex) {
            throw new StartException(ex);
        }
    }

    @Override
    public FrameworkActive getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
    FrameworkState getFrameworkState() {
        return injectedFramework.getValue().getFrameworkState();
    }

    private int getBeginningStartLevel() {
        String beginning = (String) getBundleManager().getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL);
        if (beginning == null)
            return 1;

        try {
            return Integer.parseInt(beginning);
        } catch (NumberFormatException nfe) {
            log.errorf("Could not set beginning start level to: %s", beginning);
            return 1;
        }
    }
}