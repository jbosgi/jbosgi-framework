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

import org.jboss.modules.ClassSpec;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.jboss.modules.filter.PathFilter;

/**
 * A {@link ModuleClassLoader} that hosld a reference to the underlying bundle.
 *
 * @author thomas.diesler@jboss.com
 * @since 16-Dec-2010
 */
final class HostBundleClassLoader extends BundleReferenceClassLoader<HostBundleState> {

    private final PathFilter lazyFilter;
    
    private HostBundleClassLoader(Configuration configuration, HostBundleState bundleState, PathFilter lazyFilter) {
        super(configuration, bundleState);
        this.lazyFilter = lazyFilter;
    }
    
    @Override
    protected void postDefine(ClassSpec classSpec, Class<?> definedClass) {
        if (getBundleState().isActivationLazy()) {
            String path = definedClass.getPackage().getName().replace('.', '/');
            if (lazyFilter.accept(path)) {
                LazyActivationTracker.processDefinedClass(getBundleState(), definedClass);
            }
        }
    }

    static class Factory implements ModuleClassLoaderFactory {

        private HostBundleState bundleState;
        private PathFilter lazyFilter;

        public Factory(HostBundleState bundleState, PathFilter lazyFilter) {
            this.bundleState = bundleState;
            this.lazyFilter = lazyFilter;
        }

        @Override
        public ModuleClassLoader create(Configuration configuration) {
            return new HostBundleClassLoader(configuration, bundleState, lazyFilter);
        }
    }
}
