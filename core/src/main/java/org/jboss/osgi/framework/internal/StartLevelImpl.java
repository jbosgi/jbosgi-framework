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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import org.jboss.osgi.framework.FrameworkMessages;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.service.startlevel.StartLevel;

/**
 * A minimal implementation of the deprecated {@link StartLevel} service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 13-Mar-2013
 */
@SuppressWarnings("deprecation")
public final class StartLevelImpl implements StartLevel {

    private final BundleManager bundleManager;

    public StartLevelImpl(BundleManager bundleManager) {
        this.bundleManager = bundleManager;
    }

    @Override
    public int getStartLevel() {
        XBundle systemBundle = bundleManager.getSystemBundle();
        FrameworkStartLevel fwkStartLevel = systemBundle.adapt(FrameworkStartLevel.class);
        return fwkStartLevel.getStartLevel();
    }

    @Override
    public void setStartLevel(int startlevel) {
        XBundle systemBundle = bundleManager.getSystemBundle();
        FrameworkStartLevel fwkStartLevel = systemBundle.adapt(FrameworkStartLevel.class);
        fwkStartLevel.setStartLevel(startlevel);
    }

    @Override
    public int getInitialBundleStartLevel() {
        XBundle systemBundle = bundleManager.getSystemBundle();
        FrameworkStartLevel fwkStartLevel = systemBundle.adapt(FrameworkStartLevel.class);
        return fwkStartLevel.getInitialBundleStartLevel();
    }

    @Override
    public void setInitialBundleStartLevel(int startlevel) {
        XBundle systemBundle = bundleManager.getSystemBundle();
        FrameworkStartLevel fwkStartLevel = systemBundle.adapt(FrameworkStartLevel.class);
        fwkStartLevel.setInitialBundleStartLevel(startlevel);
    }

    @Override
    public int getBundleStartLevel(Bundle bundle) {
        assertValidBaundle(bundle);
        return bundle.adapt(BundleStartLevel.class).getStartLevel();
    }

    @Override
    public void setBundleStartLevel(Bundle bundle, int startlevel) {
        assertValidBaundle(bundle);
        bundle.adapt(BundleStartLevel.class).setStartLevel(startlevel);
    }

    @Override
    public boolean isBundlePersistentlyStarted(Bundle bundle) {
        assertValidBaundle(bundle);
        return bundle.adapt(BundleStartLevel.class).isPersistentlyStarted();
    }

    @Override
    public boolean isBundleActivationPolicyUsed(Bundle bundle) {
        assertValidBaundle(bundle);
        return bundle.adapt(BundleStartLevel.class).isActivationPolicyUsed();
    }

    private void assertValidBaundle(Bundle bundle) {
        if (bundle == null)
            throw MESSAGES.illegalArgumentNull("bundle");
        if (bundle.getState() == Bundle.UNINSTALLED)
            throw FrameworkMessages.MESSAGES.illegalStateBundleAlreadyUninstalled(bundle);
    }
}
