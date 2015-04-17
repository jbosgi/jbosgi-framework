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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.Manifest;

import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.Resource;
import org.jboss.osgi.framework.internal.AbstractCommonBundleRevision;
import org.jboss.osgi.framework.internal.InternalConstants;
import org.jboss.osgi.framework.internal.ModuleEntriesProvider;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XBundleWiring;
import org.jboss.osgi.resolver.spi.AbstractBundleWiring;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleRevision;

/**
 * An abstract implementation that adapts a {@link Module} to a {@link BundleRevision}
 *
 * @author thomas.diesler@jboss.com
 * @since 30-May-2012
 */
public class AbstractBundleRevisionAdaptor extends AbstractCommonBundleRevision implements XBundleRevision {

    private final Module module;
    private final XBundle bundle;
    private final ModuleEntriesProvider entriesProvider;

    private OSGiMetaData metadata;

    public AbstractBundleRevisionAdaptor(BundleContext context, Module module) {
        if (context == null)
            throw MESSAGES.illegalArgumentNull("context");
        if (module == null)
            throw MESSAGES.illegalArgumentNull("module");
        this.module = module;
        this.bundle = createBundle(context, module, this);
        this.entriesProvider = new ModuleEntriesProvider(module);
        putAttachment(IntegrationConstants.MODULE_IDENTIFIER_KEY, module.getIdentifier());
        putAttachment(InternalConstants.MODULE_KEY, module);
        createWiring();
    }

    protected XBundleWiring createWiring() {
        XBundleWiring wiring = new AbstractBundleWiring(this, null, null);
        getWiringSupport().setWiring(wiring);
        return wiring;
    }

    protected XBundle createBundle(BundleContext context, Module module, AbstractCommonBundleRevision bundleRev) {
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
        return module.getExportedResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return module.getExportedResources(name);
    }

    @Override
    public URL getEntry(String path) {
        return entriesProvider.getEntry(path);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        return entriesProvider.getEntryPaths(path);
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recursive) {
        return entriesProvider.findEntries(path, filePattern, recursive);
    }

    /* (non-Javadoc)
     * @see org.jboss.osgi.framework.internal.AbstractCommonBundleRevision#getLocalizationEntry(java.lang.String)
     */
    @Override
    public URL getLocalizationEntry(String entryPath)
    {
        return getEntry(entryPath);
    }

    /* (non-Javadoc)
     * @see org.jboss.osgi.framework.internal.AbstractCommonBundleRevision#getOSGiMetaData()
     */
    @Override
    public OSGiMetaData getOSGiMetaData()
    {
        OSGiMetaData metadata = this.metadata;
        if (metadata == null) {
            LOGGER.tracef("First time initializing module [%s] OSGI metadata", module);
            metadata = getAttachment(IntegrationConstants.OSGI_METADATA_KEY);
            if (metadata == null) {
                LOGGER.tracef("No module [%s] OSGI metadata found in the attachment", module);
                try {
                    metadata = createOSGiMetaData();
                }
                catch (IOException e) {
                    LOGGER.warnf(e, "Unable to construct OSGI metadata by reading manifest of module [%s]",
                            module);
                }
            }
            if (metadata != null) {
                this.metadata = metadata;
            }
        }
        return metadata;
    }

    protected OSGiMetaData getOSGiMetaDataFromManifest() throws IOException {
        OSGiMetaData result = null;
        LOGGER.tracef("Attempting to read OSGI metadata from module [%s]", module);
        Manifest manifest = getManifest();
        if (OSGiManifestBuilder.isValidBundleManifest(manifest)) {
            result = OSGiMetaDataBuilder.load(manifest);
            LOGGER.debugf("Found OSGI metadata in the manifest of module [%s]: %s", module, result);
        }
        return result;
    }

    protected OSGiMetaData createOSGiMetaData() throws IOException {
        OSGiMetaData result = getOSGiMetaDataFromManifest();
        if (result == null) {
            result = getOSGiMetaDataFromModule(module);
            LOGGER.tracef("OSGI metadata created for module [%s]: %s", module, result);
        }
        return result;
    }

    protected OSGiMetaData getOSGiMetaDataFromModule(Module module) {

        // Get symbolic name & version
        ModuleIdentifier moduleId = module.getIdentifier();
        String symbolicName = moduleId.getName();
        Version version;
        try {
            version = Version.parseVersion(moduleId.getSlot());
        } catch (IllegalArgumentException ex) {
            version = Version.emptyVersion;
        }
        OSGiMetaDataBuilder builder = OSGiMetaDataBuilder.createBuilder(symbolicName, version);

        // Add a package capability for every exported path
        Set<String> paths = new HashSet<String>();
        Iterator<Resource> it = module.getClassLoader().iterateResources("", true);
        while (it.hasNext()) {
            Resource res = it.next();
            String path = res.getName();
            if (!path.endsWith(".class"))
                continue;

            int index = path.lastIndexOf("/");
            if (index <= 0)
                continue;

            path = path.substring(0, index);
            if (!paths.contains(path)) {
                paths.add(path);
                String packageName = path.replace('/', '.');
                builder.addExportPackages(packageName);
            }
        }

        return builder.getOSGiMetaData();
    }

    protected Manifest getManifest() throws IOException
    {
        URL manifestUrl = module.getClassLoader().getResource("META-INF/MANIFEST.MF");
        if (manifestUrl != null) {
            InputStream manifestStream = manifestUrl.openStream();
            try {
                return new Manifest(manifestStream);
            }
            finally {
                manifestStream.close();
            }
        }
        return null;
    }
}
