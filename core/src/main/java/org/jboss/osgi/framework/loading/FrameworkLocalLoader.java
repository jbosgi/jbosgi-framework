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
package org.jboss.osgi.framework.loading;

import java.util.HashSet;
import java.util.Set;

import org.jboss.modules.LocalLoader;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.SystemPackagesPlugin;

/**
 * A {@link LocalLoader} that only loads framework defined classes/resources.
 * 
 * @author thomas.diesler@jboss.com
 * @since 08-Jul-2010
 */
public class FrameworkLocalLoader extends SystemLocalLoader {

    public FrameworkLocalLoader(BundleManager bundleManager) {
        super(getExportedPaths(bundleManager));
    }

    private static Set<String> getExportedPaths(BundleManager bundleManager) {
        SystemPackagesPlugin plugin = bundleManager.getPlugin(SystemPackagesPlugin.class);
        HashSet<String> paths = new HashSet<String>(plugin.getExportedPaths());
        paths.add("META-INF/services");
        return paths;
    }
}
