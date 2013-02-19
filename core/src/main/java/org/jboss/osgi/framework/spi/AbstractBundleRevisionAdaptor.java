package org.jboss.osgi.framework.spi;
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

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.spi.AbstractBundleRevision;
import org.jboss.osgi.resolver.spi.AbstractBundleWiring;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;

/**
 * An abstract implementation that adapts a {@link Module} to a {@link BundleRevision}
 *
 * @author thomas.diesler@jboss.com
 * @since 30-May-2012
 */
public class AbstractBundleRevisionAdaptor extends AbstractBundleRevision implements XBundleRevision {

    private final Module module;
    private final XBundle bundle;

    public AbstractBundleRevisionAdaptor(BundleContext context, Module module) {
        if (context == null)
            throw MESSAGES.illegalArgumentNull("context");
        if (module == null)
            throw MESSAGES.illegalArgumentNull("module");
        this.module = module;
        this.bundle = createBundle(context, module, this);
        addAttachment(ModuleIdentifier.class, module.getIdentifier());
        addAttachment(Module.class, module);
        createWiring();
    }

    protected BundleWiring createWiring() {
        BundleWiring wiring = new AbstractBundleWiring(this, null, null);
        getWirings().setCurrent(wiring);
        return wiring;
    }

    protected XBundle createBundle(BundleContext context, Module module, XBundleRevision bundleRev) {
        return new AbstractBundleAdaptor(context, module, bundleRev);
    }

    public Module getModule() {
        return module;
    }

    @Override
    public XBundle getBundle() {
        return bundle;
    }

    @Override
    public ModuleIdentifier getModuleIdentifier() {
        return module.getIdentifier();
    }

    @Override
    public ModuleClassLoader getModuleClassLoader() {
        return module.getClassLoader();
    }

    @Override
    public URL getResource(String name) {
        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        return null;
    }

    @Override
    public URL getEntry(String path) {
        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        return null;
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        return null;
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recursive) {
        // [TODO] Add support for entry/resource related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-566
        return null;
    }
}
