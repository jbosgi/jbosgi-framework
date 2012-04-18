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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.Permission;
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

import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.framework.internal.BundleStoragePlugin.InternalStorageState;
import org.jboss.osgi.metadata.CaseInsensitiveDictionary;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.spi.ConstantsHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Wire;
import org.osgi.service.resolver.ResolutionException;

/**
 * An abstract representation of a {@link Bundle} state.
 * <p/>
 * It is used by the various {@link AbstractBundleService}s.
 * The state is never given to the client.
 *
 * @author thomas.diesler@jboss.com
 * @since 04-Apr-2011
 */
abstract class AbstractBundleState implements Bundle {

    private final long bundleId;
    private final String symbolicName;
    private final FrameworkState frameworkState;
    private final AtomicInteger bundleState = new AtomicInteger(UNINSTALLED);
    private final List<ServiceState> registeredServices = new CopyOnWriteArrayList<ServiceState>();
    private final ConcurrentHashMap<ServiceState, AtomicInteger> usedServices = new ConcurrentHashMap<ServiceState, AtomicInteger>();
    private AbstractBundleContext bundleContext;

    AbstractBundleState(FrameworkState frameworkState, long bundleId, String symbolicName) {
        assert frameworkState != null : "Null frameworkState";

        // strip-off the directives
        if (symbolicName != null && symbolicName.indexOf(';') > 0)
            symbolicName = symbolicName.substring(0, symbolicName.indexOf(';'));

        this.bundleId = bundleId;
        this.symbolicName = symbolicName;
        this.frameworkState = frameworkState;
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    BundleManagerPlugin getBundleManager() {
        return frameworkState.getBundleManager();
    }

    SystemBundleState getSystemBundle() {
        return frameworkState.getSystemBundle();
    }

    FrameworkCoreServices getCoreServices() {
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
        return symbolicName;
    }

    abstract AbstractBundleContext createContextInternal();

    abstract AbstractBundleRevision getCurrentBundleRevision();

    abstract AbstractBundleRevision getBundleRevisionById(int revisionId);

    abstract List<AbstractBundleRevision> getAllBundleRevisions();

    abstract ServiceName getServiceName(int state);

    abstract boolean isFragment();

    abstract boolean isSingleton();

    abstract InternalStorageState getStorageState();

    ModuleIdentifier getModuleIdentifier() {
        return getCurrentBundleRevision().getModuleIdentifier();
    }

    void changeState(int state) {
        // Get the corresponding bundle event type
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
        if (frameworkActive && bundleId > 0) {
            LifecycleInterceptorPlugin plugin = getCoreServices().getLifecycleInterceptorPlugin();
            plugin.handleStateChange(state, this);
        }

        bundleState.set(state);

        // Fire the bundle event
        if (frameworkActive && eventType != 0) {
            fireBundleEvent(eventType);
        }
    }

    void fireBundleEvent(int eventType) {
        FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
        eventsPlugin.fireBundleEvent(this, eventType);
    }

    void addRegisteredService(ServiceState serviceState) {
        LOGGER.tracef("Add registered service %s to: %s", serviceState, this);
        registeredServices.add(serviceState);
    }

    void removeRegisteredService(ServiceState serviceState) {
        LOGGER.tracef("Remove registered service %s from: %s", serviceState, this);
        registeredServices.remove(serviceState);
    }

    @Override
    public ServiceReference[] getRegisteredServices() {
        assertNotUninstalled();
        List<ServiceState> rs = getRegisteredServicesInternal();
        if (rs.isEmpty())
            return null;

        List<ServiceReference> srefs = new ArrayList<ServiceReference>();
        for (ServiceState serviceState : rs)
            srefs.add(serviceState.getReference());

        return srefs.toArray(new ServiceReference[srefs.size()]);
    }

    List<ServiceState> getRegisteredServicesInternal() {
        return Collections.unmodifiableList(registeredServices);
    }

    @Override
    public ServiceReference[] getServicesInUse() {
        assertNotUninstalled();
        Set<ServiceState> servicesInUse = getServicesInUseInternal();
        if (servicesInUse.isEmpty())
            return null;

        List<ServiceReference> srefs = new ArrayList<ServiceReference>();
        for (ServiceState serviceState : servicesInUse)
            srefs.add(serviceState.getReference());

        return srefs.toArray(new ServiceReference[srefs.size()]);
    }

    Set<ServiceState> getServicesInUseInternal() {
        return Collections.unmodifiableSet(usedServices.keySet());
    }

    void addServiceInUse(ServiceState serviceState) {
        LOGGER.tracef("Add service in use %s to: %s", serviceState, this);
        usedServices.putIfAbsent(serviceState, new AtomicInteger());
        AtomicInteger count = usedServices.get(serviceState);
        count.incrementAndGet();
    }

    int removeServiceInUse(ServiceState serviceState) {
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
        assertNotUninstalled();
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
        return getCurrentBundleRevision().getResource(name);
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        // If the specified locale is null then the locale returned
        // by java.util.Locale.getDefault is used.
        return getHeaders(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Dictionary<String, String> getHeaders(String locale) {
        // Get the raw (unlocalized) manifest headers
        Dictionary<String, String> rawHeaders = getOSGiMetaData().getHeaders();

        // If the specified locale is the empty string, this method will return the
        // raw (unlocalized) manifest headers including any leading "%"
        if ("".equals(locale))
            return rawHeaders;

        // If the specified locale is null then the locale
        // returned by java.util.Locale.getDefault is used
        if (locale == null)
            locale = Locale.getDefault().toString();

        // Get the localization base name
        String baseName = rawHeaders.get(Constants.BUNDLE_LOCALIZATION);
        if (baseName == null)
            baseName = Constants.BUNDLE_LOCALIZATION_DEFAULT_BASENAME;

        // Get the resource bundle URL for the given base and locale
        URL entryURL = getLocalizationEntry(baseName, locale);

        // If the specified locale entry could not be found fall back to the default locale entry
        if (entryURL == null) {
            String defaultLocale = Locale.getDefault().toString();
            entryURL = getLocalizationEntry(baseName, defaultLocale);
        }

        // Read the resource bundle
        ResourceBundle resBundle = null;
        if (entryURL != null) {
            try {
                resBundle = new PropertyResourceBundle(entryURL.openStream());
            } catch (IOException ex) {
                throw MESSAGES.illegalStateCannotReadResourceBundle(ex, entryURL);
            }
        }

        Dictionary<String, String> locHeaders = new Hashtable<String, String>();
        Enumeration<String> e = rawHeaders.keys();
        while (e.hasMoreElements()) {
            String key = e.nextElement();
            String value = rawHeaders.get(key);
            if (value.startsWith("%"))
                value = value.substring(1);

            if (resBundle != null) {
                try {
                    value = resBundle.getString(value);
                } catch (MissingResourceException ex) {
                    // ignore
                }
            }

            locHeaders.put(key, value);
        }

        return new CaseInsensitiveDictionary(locHeaders);
    }

    OSGiMetaData getOSGiMetaData() {
        return getCurrentBundleRevision().getOSGiMetaData();
    }

    boolean isResolved() {
        return getCurrentBundleRevision().isResolved();
    }

    boolean isUninstalled() {
        return getState() == Bundle.UNINSTALLED;
    }

    private URL getLocalizationEntry(String baseName, String locale) {
        // The Framework searches for localization entries by appending suffixes to
        // the localization base name according to a specified locale and finally
        // appending the .properties suffix. If a translation is not found, the locale
        // must be made more generic by first removing the variant, then the country
        // and finally the language until an entry is found that contains a valid translation.

        String entryPath = baseName + "_" + locale + ".properties";

        URL entryURL = getLocalizationEntry(entryPath);
        while (entryURL == null) {
            if (entryPath.equals(baseName + ".properties"))
                break;

            int lastIndex = locale.lastIndexOf('_');
            if (lastIndex > 0) {
                locale = locale.substring(0, lastIndex);
                entryPath = baseName + "_" + locale + ".properties";
            } else {
                entryPath = baseName + ".properties";
            }

            // The bundle's class loader is not used to search for localization entries. Only
            // the contents of the bundle and its attached fragments are searched.
            entryURL = getLocalizationEntry(entryPath);
        }
        return entryURL;
    }

    /**
     * The framework must search for localization entries using the following search rules based on the bundle type:
     * <p/>
     * fragment bundle - If the bundle is a resolved fragment, then the search for localization data must delegate to the
     * attached host bundle with the highest version. If the fragment is not resolved, then the framework must search the
     * fragment's JAR for the localization entry.
     * <p/>
     * other bundle - The framework must first search in the bundle’s JAR for the localization entry. If the entry is not found
     * and the bundle has fragments, then the attached fragment JARs must be searched for the localization entry.
     */
    private URL getLocalizationEntry(String entryPath) {
        return getCurrentBundleRevision().getLocalizationEntry(entryPath);
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        assertNotUninstalled();
        return getCurrentBundleRevision().loadClass(className);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return getCurrentBundleRevision().getResources(name);
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        return getCurrentBundleRevision().getEntryPaths(path);
    }

    @Override
    public URL getEntry(String path) {
        return getCurrentBundleRevision().getEntry(path);
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
        return getCurrentBundleRevision().findEntries(path, filePattern, recurse);
    }

    AbstractBundleContext getBundleContextInternal() {
        return bundleContext;
    }

    AbstractBundleContext createBundleContext() {
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
    @SuppressWarnings("rawtypes")
    public Map getSignerCertificates(int signersType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Version getVersion() {
        return getCurrentBundleRevision().getVersion();
    }

    @Override
    public void start() throws BundleException {
        assertNotUninstalled();
        startInternal(0);
    }

    @Override
    public void start(int options) throws BundleException {
        assertNotUninstalled();
        startInternal(options);
    }

    abstract void startInternal(int options) throws BundleException;

    @Override
    public void stop() throws BundleException {
        assertNotUninstalled();
        stopInternal(0);
    }

    @Override
    public void stop(int options) throws BundleException {
        assertNotUninstalled();
        stopInternal(options);
    }

    abstract void stopInternal(int options) throws BundleException;

    @Override
    public void update() throws BundleException {
        assertNotUninstalled();
        updateInternal(null);
        LOGGER.infoBundleUpdated(this);
        updateLastModified();
    }

    @Override
    public void update(InputStream input) throws BundleException {
        assertNotUninstalled();
        updateInternal(input);
        LOGGER.infoBundleUpdated(this);
        updateLastModified();
    }

    abstract void updateInternal(InputStream input) throws BundleException;

    @Override
    public void uninstall() throws BundleException {
        assertNotUninstalled();
        uninstallInternal();
    }

    abstract void uninstallInternal() throws BundleException;

    ResolutionException ensureResolved(boolean fireEvent) {

        if (isUninstalled())
            throw MESSAGES.illegalStateBundleAlreadyUninstalled(this);

        // If this bundle's state is INSTALLED, this method must attempt to resolve this bundle
        // If this bundle cannot be resolved, a Framework event of type FrameworkEvent.ERROR is fired
        // containing a BundleException with details of the reason this bundle could not be resolved.
        synchronized (this) {
            if (isResolved())
                return null;

            try {
                ResolverPlugin resolverPlugin = getFrameworkState().getResolverPlugin();
                resolverPlugin.resolveAndApply(Collections.singleton(getCurrentBundleRevision()), null);

                // Activate the service that represents bundle state RESOLVED
                getBundleManager().setServiceMode(getServiceName(RESOLVED), Mode.ACTIVE);

                if (LOGGER.isDebugEnabled()) {
                    BundleWiring wiring = getCurrentBundleRevision().getWiring();
                    LOGGER.debugf("Required resource wires for: %s", wiring.getResource());
                    for (Wire wire : wiring.getRequiredResourceWires(null)) {
                        LOGGER.debugf("   %s", wire);
                    }
                    LOGGER.debugf("Provided resource wires for: %s", wiring.getResource());
                    for (Wire wire : wiring.getProvidedResourceWires(null)) {
                        LOGGER.debugf("   %s", wire);
                    }
                }

                return null;
            } catch (ResolutionException ex) {
                if (fireEvent == true) {
                    FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
                    eventsPlugin.fireFrameworkEvent(this, FrameworkEvent.ERROR, new BundleException(ex.getMessage(), ex));
                }
                return ex;
            }
        }
    }

    void assertNotUninstalled() {
        if (getState() == Bundle.UNINSTALLED)
            throw MESSAGES.illegalStateBundleAlreadyUninstalled(this);
    }

    /**
     * Assert that the given bundle is an instance of AbstractBundleState
     */
    static AbstractBundleState assertBundleState(Bundle bundle) {
        assert bundle != null : "Null bundle";
        assert bundle instanceof AbstractBundleState : "Not a BundleState: " + bundle;
        return (AbstractBundleState) bundle;
    }

    String getCanonicalName() {
        return getSymbolicName() + ":" + getVersion();
    }

    @Override
    public int hashCode() {
        return (int) getBundleId() * 51;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AbstractBundleState == false)
            return false;
        if (obj == this)
            return true;

        AbstractBundleState other = (AbstractBundleState) obj;
        return getBundleId() == other.getBundleId();
    }

    @Override
    public String toString() {
        return getCanonicalName();
    }
}