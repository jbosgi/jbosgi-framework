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

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Permission;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorService;
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.BundleStartLevelSupport;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.ServiceState;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.CaseInsensitiveDictionary;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.spi.AbstractElement;
import org.jboss.osgi.spi.ConstantsHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleRevisions;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;

/**
 * An abstract representation of a {@link Bundle} state.
 * <p/>
 * It is used by the various {@link AbstractBundleService}s.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class AbstractBundleState<R extends BundleStateRevision> extends AbstractElement implements XBundle, LockableItem, BundleStartLevel {

    private final long bundleId;
    private final FrameworkState frameworkState;
    private final ReentrantLock bundleLock = new ReentrantLock();
    private final AtomicInteger bundleState = new AtomicInteger(INSTALLED);
    private final List<ServiceState<?>> registeredServices = new CopyOnWriteArrayList<ServiceState<?>>();
    private final ConcurrentHashMap<ServiceState<?>, AtomicInteger> usedServices = new ConcurrentHashMap<ServiceState<?>, AtomicInteger>();

    private AbstractBundleContext<? extends AbstractBundleState<?>> bundleContext;
    private Exception lastResolverException;
    private R initialRevision;
    private R currentRevision;

    AbstractBundleState(FrameworkState frameworkState, R brev, long bundleId) {
        assert frameworkState != null : "Null frameworkState";
        assert brev != null : "Null revision";

        // strip-off the directives
        String symbolicName = brev.getOSGiMetaData().getBundleSymbolicName();
        if (symbolicName != null && symbolicName.indexOf(';') > 0)
            symbolicName = symbolicName.substring(0, symbolicName.indexOf(';'));

        this.bundleId = bundleId;
        this.frameworkState = frameworkState;
        this.initialRevision = brev;

        // Link the bundle revision to this state
        brev.setBundle(this);
    }

    static AbstractBundleState<?> assertBundleState(Bundle bundle) {
        assert bundle != null : "Null bundle";
        assert bundle instanceof AbstractBundleState : "Not a BundleState: " + bundle;
        return (AbstractBundleState<?>) bundle;
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    BundleManagerPlugin getBundleManager() {
        return frameworkState.getBundleManager();
    }

    CoreServices getCoreServices() {
        return frameworkState.getCoreServices();
    }

    @Override
    public int getState() {
        return bundleState.get();
    }

    @Override
    public long getBundleId() {
        return bundleId;
    }

    @Override
    public String getSymbolicName() {
        return getBundleRevision().getSymbolicName();
    }

    abstract AbstractBundleContext<?> createContextInternal();

    /**
     * Required by spec:
     *
     * {@link BundleContext} The Bundle Context for this bundle.
     * {@link BundleRevision} The current Bundle Revision for this bundle.
     * {@link BundleRevisions} All existing Bundle Revision objects for this bundle.
     * {@link BundleStartLevel} The Bundle Start Level for this bundle.
     * {@link BundleWiring} The Bundle Wiring for the current Bundle Revision.
     *
     * Proprietary extensions:
     *
     * {@link OSGiMetaData} The Bundle metadata.
     * {@link StorageState} The Bundle's storage state.
     * {@link BundleManager} The Bundle manager.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T adapt(Class<T> type) {
        T result = null;
        if (type.isAssignableFrom(BundleContext.class)) {
            result = (T) getBundleContext();
        } else if (type.isAssignableFrom(BundleRevision.class)) {
            // After an uninstall the adapt method will always return null for BundleRevision
            result = !isUninstalled() ? (T) getBundleRevision() : null;
        } else if (type.isAssignableFrom(BundleRevisions.class)) {
            result = (T) getBundleRevisions();
        } else if (type.isAssignableFrom(BundleStartLevel.class)) {
            result = (T) this;
        } else if (type.isAssignableFrom(BundleWiring.class)) {
            // After an uninstall the adapt method will always return null for BundleWiring
            result = !isUninstalled() ? (T) getBundleWiring() : null;
        } else if (type.isAssignableFrom(OSGiMetaData.class)) {
            result = (T) getOSGiMetaData();
        } else if (type.isAssignableFrom(StorageState.class)) {
            result = (T) getStorageState();
        } else if (type.isAssignableFrom(BundleManager.class)) {
            result = (T) getBundleManager();
        }
        return result;
    }

    abstract BundleRevisions getBundleRevisions();

    @Override
    public R getBundleRevision() {
        return currentRevision != null ? currentRevision : initialRevision;
    }

    void addBundleRevision(R rev) {
        rev.setBundle(this);
        currentRevision = rev;
    }

    abstract R getBundleRevisionById(int revisionId);

    abstract boolean isSingleton();

    StorageState getStorageState() {
        return getBundleRevision().getStorageState();
    }

    ModuleIdentifier getModuleIdentifier() {
        return getBundleRevision().getModuleIdentifier();
    }

    void changeState(int state) {
        int bundleEvent;
        switch (state) {
            case Bundle.STARTING:
                bundleEvent = BundleEvent.STARTING;
                break;
            case Bundle.ACTIVE:
                bundleEvent = BundleEvent.STARTED;
                break;
            case Bundle.STOPPING:
                bundleEvent = BundleEvent.STOPPING;
                break;
            case Bundle.UNINSTALLED:
                bundleEvent = BundleEvent.UNINSTALLED;
                break;
            case Bundle.INSTALLED:
                bundleEvent = BundleEvent.INSTALLED;
                break;
            case Bundle.RESOLVED:
                bundleEvent = BundleEvent.RESOLVED;
                break;
            default:
                throw MESSAGES.illegalArgumentUnknownBundleState(state);
        }
        changeState(state, bundleEvent);
    }

    void changeState(int state, int eventType) {

        LOGGER.tracef("changeState: %s -> %s", this, ConstantsHelper.bundleState(state));

        // Invoke the lifecycle interceptors
        boolean frameworkActive = getBundleManager().isFrameworkCreated();
        if (frameworkActive && getBundleId() > 0) {
            LifecycleInterceptorService plugin = getCoreServices().getLifecycleInterceptorService();
            plugin.handleStateChange(state, this);
        }

        bundleState.set(state);

        // Fire the bundle event
        if (frameworkActive && eventType != 0) {
            fireBundleEvent(eventType);
        }
    }

    void fireBundleEvent(int eventType) {
        FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
        eventsPlugin.fireBundleEvent(this, eventType);
    }

    void addRegisteredService(ServiceState<?> serviceState) {
        LOGGER.tracef("Add registered service %s to: %s", serviceState, this);
        registeredServices.add(serviceState);
    }

    @Override
    public Bundle getBundle() {
        return this;
    }

    @Override
    public int getStartLevel() {
        BundleStartLevelSupport plugin = frameworkState.getBundleStartLevel();
        return plugin.getBundleStartLevel(this);
    }

    @Override
    public void setStartLevel(int level) {
        BundleStartLevelSupport plugin = frameworkState.getBundleStartLevel();
        plugin.setBundleStartLevel(this, level);
    }

    @Override
    public boolean isPersistentlyStarted() {
        BundleStartLevelSupport plugin = frameworkState.getBundleStartLevel();
        return plugin.isBundlePersistentlyStarted(this);
    }

    @Override
    public boolean isActivationPolicyUsed() {
        BundleStartLevelSupport plugin = frameworkState.getBundleStartLevel();
        return plugin.isBundleActivationPolicyUsed(this);
    }

    void removeRegisteredService(ServiceState<?> serviceState) {
        LOGGER.tracef("Remove registered service %s from: %s", serviceState, this);
        registeredServices.remove(serviceState);
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        assertNotUninstalled();
        List<ServiceState<?>> rs = getRegisteredServicesInternal();
        if (rs.isEmpty())
            return null;

        List<ServiceReference<?>> srefs = new ArrayList<ServiceReference<?>>();
        for (ServiceState<?> serviceState : rs)
            srefs.add(serviceState.getReference());

        return srefs.toArray(new ServiceReference[srefs.size()]);
    }

    List<ServiceState<?>> getRegisteredServicesInternal() {
        return Collections.unmodifiableList(registeredServices);
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        assertNotUninstalled();
        Set<ServiceState<?>> servicesInUse = getServicesInUseInternal();
        if (servicesInUse.isEmpty())
            return null;

        List<ServiceReference<?>> srefs = new ArrayList<ServiceReference<?>>();
        for (ServiceState<?> serviceState : servicesInUse)
            srefs.add(serviceState.getReference());

        return srefs.toArray(new ServiceReference[srefs.size()]);
    }

    Set<ServiceState<?>> getServicesInUseInternal() {
        return Collections.unmodifiableSet(usedServices.keySet());
    }

    void addServiceInUse(ServiceState<?> serviceState) {
        LOGGER.tracef("Add service in use %s to: %s", serviceState, this);
        usedServices.putIfAbsent(serviceState, new AtomicInteger());
        AtomicInteger count = usedServices.get(serviceState);
        count.incrementAndGet();
    }

    int removeServiceInUse(ServiceState<?> serviceState) {
        LOGGER.tracef("Remove service in use %s from: %s", serviceState, this);
        AtomicInteger count = usedServices.get(serviceState);
        if (count == null)
            return -1;

        int countVal = count.decrementAndGet();
        if (countVal == 0)
            usedServices.remove(serviceState);

        return countVal;
    }

    @Override
    public boolean hasPermission(Object permission) {
        if (permission == null || permission instanceof Permission == false)
            return false;

        SecurityManager sm = System.getSecurityManager();
        if (sm == null)
            return true;

        // [TODO] AbstractBundle.hasPermission
        return true;
    }

    @Override
    public URL getResource(String name) {
        return getBundleRevision().getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        // If the specified locale is null then the locale returned
        // by java.util.Locale.getDefault is used.
        return getHeaders(null);
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return getBundleRevision().getHeadersFromRaw(getOSGiMetaData().getHeaders(), locale);
    }

    OSGiMetaData getOSGiMetaData() {
        return getBundleRevision().getOSGiMetaData();
    }

    @Override
    public boolean isResolved() {
        return getBundleWiring() != null;
    }

    BundleWiring getBundleWiring() {
        return getBundleRevision().getWiring();
    }

    boolean isUninstalled() {
        return getState() == Bundle.UNINSTALLED;
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        assertNotUninstalled();
        return getBundleRevision().loadClass(className);
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
    public long getLastModified() {
        return getStorageState().getLastModified();
    }

    void updateLastModified() {
        // A bundle is considered to be modified when it is installed, updated or uninstalled.
        getStorageState().updateLastModified();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        return getBundleRevision().findEntries(path, filePattern, recurse);
    }

    AbstractBundleContext<? extends AbstractBundleState<?>> getBundleContextInternal() {
        return bundleContext;
    }

    AbstractBundleContext<? extends AbstractBundleState<?>> createBundleContext() {
        assert bundleContext == null : "BundleContext already available";
        return bundleContext = createContextInternal();
    }

    void destroyBundleContext() {
        // The BundleContext object is only valid during the execution of its context bundle;
        // that is, during the period from when the context bundle is in the STARTING, STOPPING, and ACTIVE bundle states.
        // If the BundleContext object is used subsequently, an IllegalStateException must be thrown.
        // The BundleContext object must never be reused after its context bundle is stopped.
        if (bundleContext != null) {
            bundleContext.destroy();
            bundleContext = null;
        }
    }

    @Override
    public BundleContext getBundleContext() {
        return bundleContext;
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Version getVersion() {
        return getBundleRevision().getVersion();
    }

    @Override
    public void start() throws BundleException {
        startWithOptions(0);
    }

    @Override
    public void start(int options) throws BundleException {
        startWithOptions(options);
    }

    private void startWithOptions(int options) throws BundleException {
        assertStartConditions(options);
        getBundleManager().startBundleLifecycle(this, options);
    }

    void assertStartConditions(int options) throws BundleException {
        if (isFragment())
            throw MESSAGES.cannotStartFragment();
        assertNotUninstalled();
    }

    abstract void startInternal(int options) throws BundleException;

    @Override
    public void stop() throws BundleException {
        stopWithOptions(0);
    }

    @Override
    public void stop(int options) throws BundleException {
        stopWithOptions(options);
    }

    void stopWithOptions(int options) throws BundleException {
        assertStopConditions(options);
        getBundleManager().stopBundleLifecycle(this, options);
    }

    void assertStopConditions(int options) throws BundleException {
        if (isFragment())
            throw MESSAGES.cannotStopFragment();
        assertNotUninstalled();
    }

    abstract void stopInternal(int options) throws BundleException;

    @Override
    public void update() throws BundleException {
        updateWithInputStream(null);
    }

    @Override
    public void update(InputStream input) throws BundleException {
        updateWithInputStream(input);
    }

    private void updateWithInputStream(InputStream input) throws BundleException {
        assertNotUninstalled();
        getBundleManager().updateBundle(this, input);
    }

    abstract void updateInternal(InputStream input) throws BundleException;

    @Override
    public void uninstall() throws BundleException {
        assertNotUninstalled();
        getBundleManager().uninstallBundle(this, InternalConstants.UNINSTALL_INTERNAL);
    }

    abstract void uninstallInternal(int options) throws BundleException;

    boolean ensureResolved(boolean fireEvent) {

        if (isUninstalled())
            throw MESSAGES.illegalStateBundleAlreadyUninstalled(this);

        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        // If this bundle cannot be resolved, a Framework event of type FrameworkEvent.ERROR is fired
        // containing a BundleException with details of the reason this bundle could not be resolved.

        boolean result = true;
        if (isResolved() == false) {
            try {
                getBundleManager().resolveBundleLifecycle(this);
                if (LOGGER.isDebugEnabled()) {
                    BundleWiring wiring = getBundleRevision().getWiring();
                    LOGGER.tracef("Required resource wires for: %s", wiring.getResource());
                    for (Wire wire : wiring.getRequiredResourceWires(null)) {
                        LOGGER.tracef("   %s", wire);
                    }
                    LOGGER.tracef("Provided resource wires for: %s", wiring.getResource());
                    for (Wire wire : wiring.getProvidedResourceWires(null)) {
                        LOGGER.tracef("   %s", wire);
                    }
                }
            } catch (ResolutionException ex) {
                result = false;
                handleResolverException(fireEvent, BundleException.RESOLVE_ERROR, ex);
            } catch (RuntimeException ex) {
                result = false;
                handleResolverException(fireEvent, BundleException.REJECTED_BY_HOOK, ex);
            }
        }
        return result;
    }

    private void handleResolverException(boolean fireEvent, int type, Exception ex) {
        LOGGER.debugf(ex, "Cannot resolve bundle: %s", this);
        lastResolverException = ex;
        if (fireEvent == true) {
            FrameworkEvents eventsPlugin = getFrameworkState().getFrameworkEvents();
            eventsPlugin.fireFrameworkEvent(this, FrameworkEvent.ERROR, new BundleException(ex.getMessage(), type, ex));
        }
    }

    Exception getLastResolverException() {
        return lastResolverException;
    }

    @Override
    public ReentrantLock getReentrantLock() {
        return bundleLock;
    }

    void assertNotUninstalled() {
        if (isUninstalled())
            throw MESSAGES.illegalStateBundleAlreadyUninstalled(this);
    }

    @Override
    public String getCanonicalName() {
        return getBundleRevision().getCanonicalName();
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
    public String toString() {
        return getCanonicalName();
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
}
