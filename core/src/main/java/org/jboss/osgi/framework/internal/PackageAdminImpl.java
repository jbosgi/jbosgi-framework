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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.resolver.XBundle;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.VersionRange;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.service.packageadmin.RequiredBundle;

/**
 * A minimal implementation of the deprecated {@link PackageAdmin} service.
 *
 * @author Thomas.Diesler@jboss.com
 * @since 13-Mar-2013
 */
@SuppressWarnings("deprecation")
public final class PackageAdminImpl implements PackageAdmin {

    private final BundleManager bundleManager;

    public PackageAdminImpl(BundleManager bundleManager) {
        this.bundleManager = bundleManager;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Bundle getBundle(Class clazz) {
        Bundle result = null;
        ClassLoader classLoader = clazz != null ? clazz.getClassLoader() : null;
        if (classLoader instanceof BundleReference) {
            result = ((BundleReference) classLoader).getBundle();
        }
        return result;
    }

    @Override
    public void refreshPackages(Bundle[] barray) {
        XBundle systemBundle = bundleManager.getSystemBundle();
        FrameworkWiring fwkWiring = systemBundle.adapt(FrameworkWiring.class);
        Collection<Bundle> bundles = barray != null ? Arrays.asList(barray) : null;
        fwkWiring.refreshBundles(bundles, (FrameworkListener) null);
    }

    @Override
    public boolean resolveBundles(Bundle[] barray) {
        XBundle systemBundle = bundleManager.getSystemBundle();
        FrameworkWiring fwkWiring = systemBundle.adapt(FrameworkWiring.class);
        Collection<Bundle> bundles = barray != null ? Arrays.asList(barray) : null;
        return fwkWiring.resolveBundles(bundles);
    }

    @Override
    public Bundle[] getBundles(String symbolicName, String rangespec) {
        VersionRange vrange = rangespec != null ? new VersionRange(rangespec) : null;
        Set<XBundle> bundles = bundleManager.getBundles(symbolicName, vrange);
        List<Bundle> result = new ArrayList<Bundle>();
        for (Bundle aux : bundles) {
            result.add(aux);
        }
        return !result.isEmpty() ? result.toArray(new Bundle[result.size()]) : null;
    }

    @Override
    public int getBundleType(Bundle bundle) {
        return bundle != null && ((XBundle) bundle).isFragment() ? PackageAdmin.BUNDLE_TYPE_FRAGMENT : 0;
    }

    @Override
    public ExportedPackage[] getExportedPackages(Bundle bundle) {
        return null;
    }

    @Override
    public ExportedPackage[] getExportedPackages(String name) {
        return null;
    }

    @Override
    public ExportedPackage getExportedPackage(String name) {
        return null;
    }

    @Override
    public RequiredBundle[] getRequiredBundles(String symbolicName) {
        return null;
    }

    @Override
    public Bundle[] getFragments(Bundle bundle) {
        return null;
    }

    @Override
    public Bundle[] getHosts(Bundle bundle) {
        return null;
    }
}
