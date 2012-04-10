/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
