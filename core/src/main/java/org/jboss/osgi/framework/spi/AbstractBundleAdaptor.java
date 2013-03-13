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
package org.jboss.osgi.framework.spi;

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger.Level;
import org.jboss.modules.Module;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.framework.spi.LockManager.LockSupport;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XIdentityCapability;
import org.jboss.osgi.resolver.spi.AbstractElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;

/**
 * An abstract implementation that adapts a {@link Module} to a {@link Bundle}
 *
 * @author thomas.diesler@jboss.com
 * @since 30-May-2012
 */
public class AbstractBundleAdaptor extends AbstractElement implements XBundle, LockableItem, BundleStartLevel {

    private final AtomicInteger bundleState = new AtomicInteger(Bundle.RESOLVED);
    private final LockSupport bundleLock = LockManager.Factory.addLockSupport(this);
    private final BundleManager bundleManager;
    private final BundleContext context;
    private final XBundleRevision brev;
    private final Module module;
    private BundleActivator bundleActivator;
    private long lastModified;

    public AbstractBundleAdaptor(BundleContext context, Module module, XBundleRevision brev) {
        if (context == null)
            throw MESSAGES.illegalArgumentNull("context");
        if (module == null)
            throw MESSAGES.illegalArgumentNull("module");
        if (brev == null)
            throw MESSAGES.illegalArgumentNull("brev");
        XBundle sysbundle = (XBundle) context.getBundle();
        this.bundleManager = sysbundle.adapt(BundleManager.class);
        this.context = context;
        this.module = module;
        this.brev = brev;

        this.lastModified = System.currentTimeMillis();
    }

    @Override
    public Bundle getBundle() {
        return this;
    }

    @Override
    public long getBundleId() {
        Long bundleId = brev.getAttachment(Long.class);
        return bundleId != null ? bundleId.longValue() : -1;
    }

    @Override
    public String getLocation() {
        String location = module.getIdentifier().getName();
        String slot = module.getIdentifier().getSlot();
        if (!slot.equals("main")) {
            location += ":" + slot;
        }
        return location;
    }

    @Override
    public String getSymbolicName() {
        String symbolicName = null;
        List<Capability> icaps = brev.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
        if (icaps.size() > 0) {
            XIdentityCapability icap = (XIdentityCapability) icaps.get(0);
            symbolicName = icap.getSymbolicName();
        } else {
            symbolicName = module.getIdentifier().getName();
        }
        return symbolicName;
    }

    @Override
    public String getCanonicalName() {
        return getSymbolicName() + ":" + getVersion();
    }

    @Override
    public int getState() {
        return bundleState.get();
    }

    @Override
    public Version getVersion() {
        Version version = Version.emptyVersion;
        List<Capability> icaps = brev.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
        if (icaps.size() > 0) {
            XIdentityCapability icap = (XIdentityCapability) icaps.get(0);
            version = icap.getVersion();
        } else {
            String slot = module.getIdentifier().getSlot();
            try {
                version = Version.parseVersion(slot);
            } catch (IllegalArgumentException ex) {
                // ignore
            }
        }
        return version;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return module.getClassLoader().loadClass(name);
    }

    /**
     * Required by spec:
     *
     * {@link BundleContext} The Bundle Context for this bundle.
     * {@link BundleRevision} The current Bundle Revision for this bundle.
     * {@link BundleRevisions} All existing Bundle Revision objects for this bundle.
     * {@link BundleStartLevel} The Bundle Start Level for this bundle.
     * [TODO] {@link BundleWiring} The Bundle Wiring for the current Bundle Revision.
     *
     * Proprietary extensions:
     *
     * [TODO] {@link OSGiMetaData} The Bundle metadata.
     * {@link Module} The Bundle's module.
     * {@link BundleManager} The Bundle manager.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T adapt(Class<T> type) {
        T result = null;
        if (type.isAssignableFrom(BundleContext.class)) {
            result = (T) getBundleContext();
        } else if (type.isAssignableFrom(BundleRevision.class)) {
            result = (T) getBundleRevision();
        } else if (type.isAssignableFrom(BundleRevisions.class)) {
            result = (T) getBundleRevisions();
        } else if (type.isAssignableFrom(BundleStartLevel.class)) {
            result = (T) this;
        } else if (type.isAssignableFrom(BundleManager.class)) {
            result = (T) bundleManager;
        } else if (type.isAssignableFrom(Module.class)) {
            result = (T) module;
        }
        return result;
    }

    BundleRevisions getBundleRevisions() {
        final Bundle bundle = this;
        return new BundleRevisions() {

            @Override
            public Bundle getBundle() {
                return bundle;
            }

            @Override
            public List<BundleRevision> getRevisions() {
                return Collections.singletonList((BundleRevision) brev);
            }

            @Override
            public String toString() {
                return bundle + ": [" + brev + "]";
            }
        };
    }

    @Override
    public void start(int options) throws BundleException {
        LockContext lockContext = null;
        LockManager lockManager = getPluginService(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class);
        try {
            lockContext = lockManager.lockItems(Method.START, this);

            // If this bundle's state is ACTIVE then this method returns immediately.
            if (getState() == Bundle.ACTIVE)
                return;

            // Set this bundle's autostart setting
            setPersistentlyStarted(true);

            // If the Framework's current start level is less than this bundle's start level
            if (startLevelValidForStart() == false) {

                // If the START_TRANSIENT option is set, then a BundleException is thrown
                // indicating this bundle cannot be started due to the Framework's current start level
                if ((options & START_TRANSIENT) != 0)
                    throw MESSAGES.cannotStartBundleDueToStartLevel();

                int frameworkState = bundleManager.getSystemBundle().getState();
                StartLevelSupport plugin = getPluginService(IntegrationServices.START_LEVEL_PLUGIN, StartLevelSupport.class);
                Level level = (plugin.isFrameworkStartLevelChanging() || frameworkState != Bundle.ACTIVE) ? Level.DEBUG : Level.INFO;
                LOGGER.log(level, MESSAGES.bundleStartLevelNotValid(getStartLevel(), plugin.getFrameworkStartLevel(), this));
                return;
            }

            bundleState.set(Bundle.STARTING);

            // Load the {@link BundleActivator}
            OSGiMetaData metadata = brev.getAttachment(OSGiMetaData.class);
            String activatorName = metadata != null ? metadata.getBundleActivator() : null;
            if (bundleActivator == null && activatorName != null) {
                Object result = loadClass(activatorName).newInstance();
                if (result instanceof BundleActivator) {
                    bundleActivator = (BundleActivator) result;
                } else {
                    throw MESSAGES.invalidBundleActivator(activatorName);
                }
            }

            if (bundleActivator != null) {
                bundleActivator.start(context);
            }

            setPersistentlyStarted(true);
            bundleState.set(Bundle.ACTIVE);
            LOGGER.infoBundleStarted(this);

        } catch (Throwable th) {
            bundleState.set(Bundle.RESOLVED);
            throw new BundleException(MESSAGES.cannotStartBundle(this), th);
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public void start() throws BundleException {
        start(0);
    }

    @Override
    public void stop(int options) throws BundleException {

        LockContext lockContext = null;
        LockManager lockManager = getPluginService(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class);
        try {
            lockContext = lockManager.lockItems(Method.STOP, this);

            // If this bundle's state is not ACTIVE then this method returns immediately.
            if (getState() != Bundle.ACTIVE)
                return;

            if ((options & Bundle.STOP_TRANSIENT) == 0)
                setPersistentlyStarted(false);

            if (bundleActivator != null) {
                bundleActivator.stop(context);
            }

            bundleState.set(Bundle.RESOLVED);
            LOGGER.infoBundleStopped(this);

        } catch (Throwable th) {
            throw MESSAGES.cannotStopBundle(th, this);
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public void stop() throws BundleException {
        stop(0);
    }

    @Override
    public void update(InputStream input) throws BundleException {
        throw MESSAGES.unsupportedBundleOpertaion(this);
    }

    @Override
    public void update() throws BundleException {
        throw MESSAGES.unsupportedBundleOpertaion(this);
    }

    @Override
    public void uninstall() throws BundleException {
        LockContext lockContext = null;
        LockManager lockManager = getPluginService(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class);
        try {
            FrameworkWiringLock wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
            lockContext = lockManager.lockItems(Method.RESOLVE, wireLock, this);

            // Uninstall from the environment
            XEnvironment env = getPluginService(Services.ENVIRONMENT, XEnvironment.class);
            env.uninstallResources(getBundleRevision());
            // Remove from the module loader
            FrameworkModuleLoader provider = getPluginService(IntegrationServices.FRAMEWORK_MODULE_LOADER_PLUGIN, FrameworkModuleLoader.class);
            provider.removeModule(brev);
            bundleState.set(Bundle.UNINSTALLED);
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return getHeaders(null);
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        // [TODO] Add support for manifest header related APIs on Module adaptors
        // https://issues.jboss.org/browse/JBOSGI-567
        return new Hashtable<String, String>();
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        return null;
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        return null;
    }

    @Override
    public boolean hasPermission(Object permission) {
        return false;
    }

    @Override
    public URL getResource(String name) {
        return getBundleRevision().getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return getBundleRevision().getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        return getBundleRevision().getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        return getBundleRevision().getEntry(path);
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        return getBundleRevision().findEntries(path, filePattern, recurse);
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public BundleContext getBundleContext() {
        return context;
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        return Collections.emptyMap();
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    public boolean isFragment() {
        return getBundleRevision().isFragment();
    }

    @Override
    public XBundleRevision getBundleRevision() {
        return brev;
    }

    @Override
    public List<XBundleRevision> getAllBundleRevisions() {
        return Collections.singletonList(brev);
    }

    @Override
    public int getStartLevel() {
        StartLevelSupport plugin = getPluginService(IntegrationServices.START_LEVEL_PLUGIN, StartLevelSupport.class);
        return plugin.getBundleStartLevel(this);
    }

    @Override
    public void setStartLevel(int level) {
        StartLevelSupport plugin = getPluginService(IntegrationServices.START_LEVEL_PLUGIN, StartLevelSupport.class);
        plugin.setBundleStartLevel(this, level);
    }

    @Override
    public boolean isPersistentlyStarted() {
        StartLevelSupport plugin = getPluginService(IntegrationServices.START_LEVEL_PLUGIN, StartLevelSupport.class);
        return plugin.isBundlePersistentlyStarted(this);
    }

    @Override
    public boolean isActivationPolicyUsed() {
        StartLevelSupport plugin = getPluginService(IntegrationServices.START_LEVEL_PLUGIN, StartLevelSupport.class);
        return plugin.isBundleActivationPolicyUsed(this);
    }

    private void setPersistentlyStarted(boolean started) {
        StartLevelSupport plugin = getPluginService(IntegrationServices.START_LEVEL_PLUGIN, StartLevelSupport.class);
        plugin.setBundlePersistentlyStarted(this, started);
    }

    private boolean startLevelValidForStart() {
        StartLevelSupport plugin = getPluginService(IntegrationServices.START_LEVEL_PLUGIN, StartLevelSupport.class);
        return plugin.getBundleStartLevel(this) <= plugin.getFrameworkStartLevel();
    }

    @Override
    public LockSupport getLockSupport() {
        return bundleLock;
    }

    @SuppressWarnings("unchecked")
    private <T extends Object> T getPluginService(ServiceName serviceName, Class<T> pluginType) {
        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        ServiceController<T> service = (ServiceController<T>) serviceContainer.getRequiredService(serviceName);
        return service.getValue();
    }

    @Override
    public int hashCode() {
        return (int) getBundleId() * 51;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof XBundle == false)
            return false;
        if (obj == this)
            return true;

        XBundle other = (XBundle) obj;
        return getBundleId() == other.getBundleId();
    }

    @Override
    public File getDataFile(String filename) {
        // [TODO] R5 Bundle.getDataFile
        throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(Bundle o) {
        // [TODO] R5 Bundle.compareTo
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return getCanonicalName();
    }
}
