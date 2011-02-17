/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.osgi.framework.plugin.internal;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.osgi.framework.bundle.BundleManager;
import org.jboss.osgi.framework.plugin.AbstractPlugin;
import org.jboss.osgi.framework.plugin.ModuleLoaderPlugin;

/**
 * A plugin that handles module loading.
 *
 * @author thomas.diesler@jboss.com
 * @since 17-Feb-2011
 */
public class ModuleLoaderPluginImpl extends AbstractPlugin implements ModuleLoaderPlugin {

    public ModuleLoaderPluginImpl(BundleManager bundleManager) {
        super(bundleManager);
    }

    @Override
    public Module loadModule(ModuleIdentifier identifier) throws ModuleLoadException {
        ModuleLoader moduleLoader = Module.getSystemModuleLoader();
        return moduleLoader.loadModule(identifier);
    }
}