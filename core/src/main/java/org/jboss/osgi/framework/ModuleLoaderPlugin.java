package org.jboss.osgi.framework;
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

import java.util.Map;

import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.resolver.XResource;

/**
 * Integration point for the {@link ModuleLoader}.
 *
 * @author thomas.diesler@jboss.com
 * @since 20-Apr-2011
 */
public interface ModuleLoaderPlugin extends Service<ModuleLoaderPlugin> {

    ModuleLoader getModuleLoader();

    ModuleIdentifier getModuleIdentifier(XResource resource, int rev);

    void addIntegrationDependencies(ModuleSpecBuilderContext context);

    void addModuleSpec(XResource resource, ModuleSpec moduleSpec);

    void addModule(XResource resource, Module module);

    Module getModule(ModuleIdentifier identifier);

    void removeModule(XResource resource, ModuleIdentifier identifier);

    ServiceName getModuleServiceName(ModuleIdentifier identifier);

    ServiceName createModuleService(XResource resource, ModuleIdentifier identifier);

    interface ModuleSpecBuilderContext {

        XResource getBundleRevision();

        ModuleSpec.Builder getModuleSpecBuilder();

        Map<ModuleIdentifier, DependencySpec> getModuleDependencies();
    }

}