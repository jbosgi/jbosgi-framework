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

import org.jboss.osgi.framework.spi.BundleStartLevelSupport;
import org.jboss.osgi.framework.spi.StartLevelSupport;
import org.jboss.osgi.resolver.XBundle;

/**
 * An implementation of the {@link BundleStartLevelSupport} service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 19-Nov-2012
 */
public final class BundleStartLevelImpl  implements BundleStartLevelSupport {

    private final StartLevelSupport startLevel;

    public BundleStartLevelImpl(StartLevelSupport startLevel) {
        this.startLevel = startLevel;
    }

    @Override
    public int getBundleStartLevel(XBundle bundle) {
        return startLevel.getBundleStartLevel(bundle);
    }

    @Override
    public void setBundleStartLevel(XBundle bundle, int level) {
        startLevel.setBundleStartLevel(bundle, level);
    }

    @Override
    public boolean isBundlePersistentlyStarted(XBundle bundle) {
        return startLevel.isBundlePersistentlyStarted(bundle);
    }

    @Override
    public boolean isBundleActivationPolicyUsed(XBundle bundle) {
        return startLevel.isBundleActivationPolicyUsed(bundle);
    }
}
