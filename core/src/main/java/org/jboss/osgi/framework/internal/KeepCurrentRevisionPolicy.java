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

import org.jboss.osgi.framework.spi.BundleLifecycle.BundleRefreshPolicy;
import org.jboss.osgi.resolver.XBundle;

/**
 * A refresh policy that keeps the current revision.
 *
 * @author thomas.diesler@jboss.com
 * @since 09-Apr-2013
 */
final class KeepCurrentRevisionPolicy implements BundleRefreshPolicy {
    private UserBundleRevision currentRev;

    @Override
    public void initBundleRefresh(XBundle bundle) {
        this.currentRev = (UserBundleRevision) bundle.getBundleRevision();
    }

    @Override
    public void refreshCurrentRevision() {
        currentRev.refreshRevision();
    }
}