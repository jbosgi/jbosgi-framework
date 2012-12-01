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

import org.jboss.osgi.framework.spi.FrameworkStartLevelSupport;
import org.jboss.osgi.framework.spi.StartLevelSupport;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkListener;

/**
 * An implementation of the {@link FrameworkStartLevelSupport} service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 08-Nov-2012
 */
public final class FrameworkStartLevelImpl implements FrameworkStartLevelSupport {

    private final XBundle systemBundle;
    private final StartLevelSupport startLevelSupport;

    public FrameworkStartLevelImpl(XBundle systemBundle, StartLevelSupport startLevel) {
        this.systemBundle = systemBundle;
        this.startLevelSupport = startLevel;
    }

    @Override
    public Bundle getBundle() {
        return systemBundle;
    }

    @Override
    public int getStartLevel() {
        return startLevelSupport.getFrameworkStartLevel();
    }

    @Override
    public void setStartLevel(int startlevel, FrameworkListener... listeners) {
        startLevelSupport.setFrameworkStartLevel(startlevel, listeners);
    }

    @Override
    public void shutdownFramework(FrameworkListener... listeners) {
        startLevelSupport.shutdownFramework(listeners);
    }

    @Override
    public int getInitialBundleStartLevel() {
        return startLevelSupport.getInitialBundleStartLevel();
    }

    @Override
    public void setInitialBundleStartLevel(int startlevel) {
        startLevelSupport.setInitialBundleStartLevel(startlevel);
    }
}
