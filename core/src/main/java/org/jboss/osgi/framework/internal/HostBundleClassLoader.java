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

import org.jboss.modules.ClassSpec;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleClassLoaderFactory;
import org.jboss.modules.filter.PathFilter;
import org.jboss.osgi.framework.internal.WeavingContext.ContextClass;
import org.jboss.osgi.framework.spi.BundleReferenceClassLoader;

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
    public Class<?> loadClassLocal(String className, boolean resolve) throws ClassNotFoundException {
        WeavingContext weavingContext = WeavingContext.create(getBundleState());
        try {
            return super.loadClassLocal(className, resolve);
        } finally {
            weavingContext.close();
        }
    }


    @Override
    protected void preDefine(ClassSpec classSpec, String className) {
        if (getBundleState().awaitLazyActivation()) {
            String path = className.substring(0, className.lastIndexOf('.')).replace('.', '/');
            if (lazyFilter.accept(path)) {
                LazyActivationTracker.preDefineClass(getBundleState(), className);
            }
        }
    }

    @Override
    protected void postDefine(ClassSpec classSpec, Class<?> definedClass) {
        WeavingContext weavingContext = WeavingContext.getCurrentWeavingContext();
        if (weavingContext != null) {
            ContextClass wovenClass = weavingContext.getContextClass(definedClass.getName());
            if (wovenClass != null) {
                wovenClass.setProtectionDomain(definedClass.getProtectionDomain());
                wovenClass.setDefinedClass(definedClass);
                wovenClass.setWeavingComplete();
            }
        }
        if (getBundleState().awaitLazyActivation()) {
            String path = definedClass.getPackage().getName().replace('.', '/');
            if (lazyFilter.accept(path)) {
                LazyActivationTracker.postDefineClass(getBundleState(), definedClass);
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
