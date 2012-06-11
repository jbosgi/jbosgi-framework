/*
 * #%L
 * JBossOSGi Framework Core
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

import java.net.URL;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.ResourceBuilderException;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XBundleRevisionBuilderFactory;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResourceBuilder;
import org.jboss.osgi.resolver.XResourceBuilderFactory;
import org.jboss.osgi.resolver.spi.AbstractBundleRevision;
import org.osgi.framework.BundleException;
import org.osgi.framework.wiring.BundleWiring;

/**
 * An abstract bundle revision.
 *
 * @author thomas.diesler@jboss.com
 * @author <a href="david@redhat.com">David Bosschaert</a>
 * @since 29-Jun-2010
 */
abstract class BundleStateRevision extends AbstractBundleRevision {

    private final int revision;
    private final OSGiMetaData metadata;
    private final FrameworkState frameworkState;

    BundleStateRevision(FrameworkState frameworkState, OSGiMetaData metadata, int revId) throws BundleException {
        assert frameworkState != null : "Null frameworkState";
        assert metadata != null : "Null metadata";

        this.frameworkState = frameworkState;
        this.metadata = metadata;
        this.revision = revId;

        // Initialize the bundle caps/reqs
        try {
            final BundleStateRevision brev = this;
            XBundleRevisionBuilderFactory factory = new XBundleRevisionBuilderFactory() {
                public XBundleRevision createResource() {
                    return brev;
                }
            };
            XResourceBuilder builder = XResourceBuilderFactory.create(factory);
            builder.loadFrom(metadata).getResource();
        } catch (ResourceBuilderException ex) {
            throw new BundleException(ex.getMessage(), ex);
        }
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    String getCanonicalName() {
        return getSymbolicName() + ":" + getVersion();
    }

    @Override
    public int getRevisionId() {
        return revision;
    }

    OSGiMetaData getOSGiMetaData() {
        return metadata;
    }

    abstract String getLocation();

    abstract Class<?> loadClass(String className) throws ClassNotFoundException;
    
    abstract URL getLocalizationEntry(String path);
        
    @Override
    public ModuleIdentifier getModuleIdentifier() {
        return getAttachment(ModuleIdentifier.class);
    }

    @Override
    public ModuleClassLoader getModuleClassLoader() {
        ModuleIdentifier identifier = getModuleIdentifier();
        try {
            ModuleManagerPlugin moduleManager = frameworkState.getModuleManagerPlugin();
            Module module = moduleManager.loadModule(identifier);
            return module.getClassLoader();
        } catch (ModuleLoadException ex) {
            return null;
        }
    }

    void refreshRevision() throws BundleException {
        XEnvironment env = frameworkState.getEnvironment();
        env.refreshResources(this);
        refreshRevisionInternal();
    }

    void refreshRevisionInternal() {
        removeAttachment(BundleWiring.class);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getCanonicalName() + "]";
    }
}
