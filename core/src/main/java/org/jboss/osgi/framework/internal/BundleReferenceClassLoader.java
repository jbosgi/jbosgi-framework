package org.jboss.osgi.framework.internal;
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

import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

/**
 * A {@link ModuleClassLoader} that hosld a reference to the underlying bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Dec-2010
 */
class BundleReferenceClassLoader<T extends AbstractBundleState> extends ModuleClassLoader implements BundleReference {

    private final T bundleState;

    BundleReferenceClassLoader(Configuration configuration, T bundleState) {
        super(configuration);
        assert bundleState != null : "Null bundleState";
        this.bundleState = bundleState;
    }

    @Override
    public Bundle getBundle() {
        return bundleState;
    }

    T getBundleState() {
        return bundleState;
    }

    static class Factory<T extends AbstractBundleState> implements ModuleClassLoaderFactory {

        private T bundleState;

        public Factory(T bundleState) {
            this.bundleState = bundleState;
        }

        @Override
        public ModuleClassLoader create(Configuration configuration) {
            return new BundleReferenceClassLoader<T>(configuration, bundleState);
        }
    }
}
