/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.ModuleLoaderProvider;
import org.jboss.osgi.framework.util.Java;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XModule;
import org.jboss.osgi.resolver.XVersionRange;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;

/**
 * The BundleManager is the central managing entity for OSGi bundles.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 29-Jun-2010
 */
final class BundleManager extends AbstractService<BundleManager> {

    // Provide logging
    static final Logger log = Logger.getLogger(BundleManager.class);

    // The framework execution environment
    private static String OSGi_FRAMEWORK_EXECUTIONENVIRONMENT;
    // The framework language
    private static String OSGi_FRAMEWORK_LANGUAGE = Locale.getDefault().getLanguage();
    // The os name
    private static String OSGi_FRAMEWORK_OS_NAME;
    // The os version
    private static String OSGi_FRAMEWORK_OS_VERSION;
    // The os version
    private static String OSGi_FRAMEWORK_PROCESSOR;
    // The framework vendor
    private static String OSGi_FRAMEWORK_VENDOR = "jboss.org";
    // The framework version. This is the version of the org.osgi.framework package in r4v42
    private static String OSGi_FRAMEWORK_VERSION = "1.5";

    private static String implementationTitle;
    private static String implementationVersion;
    static {
        implementationTitle = BundleManager.class.getPackage().getImplementationTitle();
        implementationVersion = BundleManager.class.getPackage().getImplementationVersion();
    }

    final InjectedValue<ModuleLoader> injectedModuleLoader = new InjectedValue<ModuleLoader>();
    final InjectedValue<FrameworkCreate> injectedFramework = new InjectedValue<FrameworkCreate>();
    final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();

    private final FrameworkBuilder frameworkBuilder;
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final AtomicLong identityGenerator = new AtomicLong();
    private final Map<Long, BundleState> bundleMap = Collections.synchronizedMap(new HashMap<Long, BundleState>());
    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;

    static BundleManager addService(ServiceTarget serviceTarget, FrameworkBuilder frameworkBuilder) {
        BundleManager service = new BundleManager(frameworkBuilder);
        ServiceBuilder<BundleManager> builder = serviceTarget.addService(Services.BUNDLE_MANAGER, service);
        builder.addDependency(ModuleLoaderProvider.SERVICE_NAME, ModuleLoader.class, service.injectedModuleLoader);
        builder.install();
        return service;
    }

    private BundleManager(FrameworkBuilder frameworkBuilder) {
        this.frameworkBuilder = frameworkBuilder;

        // The properties on the BundleManager are mutable as long the framework is not created
        // Plugins may modify these properties in their respective constructor
        properties.putAll(frameworkBuilder.getProperties());

        // Init default framework properties
        if (getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT) == null)
            setProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT, OSGi_FRAMEWORK_EXECUTIONENVIRONMENT);
        if (getProperty(Constants.FRAMEWORK_LANGUAGE) == null)
            setProperty(Constants.FRAMEWORK_LANGUAGE, OSGi_FRAMEWORK_LANGUAGE);
        if (getProperty(Constants.FRAMEWORK_OS_NAME) == null)
            setProperty(Constants.FRAMEWORK_OS_NAME, OSGi_FRAMEWORK_OS_NAME);
        if (getProperty(Constants.FRAMEWORK_OS_VERSION) == null)
            setProperty(Constants.FRAMEWORK_OS_VERSION, OSGi_FRAMEWORK_OS_VERSION);
        if (getProperty(Constants.FRAMEWORK_PROCESSOR) == null)
            setProperty(Constants.FRAMEWORK_PROCESSOR, OSGi_FRAMEWORK_PROCESSOR);
        if (getProperty(Constants.FRAMEWORK_VENDOR) == null)
            setProperty(Constants.FRAMEWORK_VENDOR, OSGi_FRAMEWORK_VENDOR);
        if (getProperty(Constants.FRAMEWORK_VERSION) == null)
            setProperty(Constants.FRAMEWORK_VERSION, OSGi_FRAMEWORK_VERSION);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        log.infof(implementationTitle + " - " + implementationVersion);
        serviceContainer = context.getController().getServiceContainer();
        serviceTarget = context.getChildTarget();
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
    }

    @Override
    public BundleManager getValue() throws IllegalStateException {
        return this;
    }

    FrameworkBuilder getFrameworkBuilder() {
        return frameworkBuilder;
    }

    ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    SystemBundleState getSystemBundle() {
        return injectedSystemBundle.getValue();
    }

    boolean isFrameworkActive() {
        return getFrameworkState() != null;
    }

    void assertFrameworkActive() {
        if (isFrameworkActive() == false)
            throw new IllegalStateException("Framework not ACTIVE");
    }

    FrameworkState getFrameworkState() {
        FrameworkCreate framework = injectedFramework.getOptionalValue();
        return framework != null ? framework.getFrameworkState() : null;
    }

    Object getProperty(String key) {
        Object value = properties.get(key);
        if (value == null)
            value = SecurityActions.getSystemProperty(key, null);
        return value;
    }

    /**
     * Returns the framework properties merged with the System properties. The returned map is consistent with the
     * {@link #getProperty(String)} API.
     *
     * @return The effective framework properties in a map. The returned map is a copy, so the client can take ownership of it.
     */
    Map<String, Object> getProperties() {
        Map<String, Object> m = new HashMap<String, Object>();
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            m.put(entry.getKey().toString(), entry.getValue());
        }

        m.putAll(properties);
        return m;
    }

    void setProperty(String key, Object value) {
        if (isFrameworkActive())
            throw new IllegalStateException("Cannot add property to ACTIVE framwork");

        properties.put(key, value);
    }

    void addBundle(BundleState bundleState) {
        if (bundleState == null)
            throw new IllegalArgumentException("Null bundleState");

        long bundleId = bundleState.getBundleId();
        if (bundleMap.containsKey(bundleId) == true)
            throw new IllegalStateException("Bundle already added: " + bundleState);

        log.infof("Install bundle: %s", bundleState);
        bundleMap.put(bundleState.getBundleId(), bundleState);
    }

    /**
     * Get the set of installed bundles. 
     * Bundles in state UNINSTALLED are not returned.
     */
    Set<BundleState> getBundles() {
        Set<BundleState> result = new HashSet<BundleState>();
        for (BundleState aux : bundleMap.values()) {
            if (aux.getState() != Bundle.UNINSTALLED)
                result.add(aux);
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Get a bundle by id
     *
     * Note, this will get the bundle regadless of its state. i.e. The returned bundle may have been UNINSTALLED
     *
     * @param bundleId The identifier of the bundle
     * @return The bundle or null if there is no bundle with that id
     */
    BundleState getBundleById(long bundleId) {
        return bundleId == 0 ? getFrameworkState().getSystemBundle() : bundleMap.get(bundleId);
    }

    /**
     * Get a bundle by location
     *
     * Note, this will get the bundle regadless of its state. i.e. The returned bundle may have been UNINSTALLED
     *
     * @param location the location of the bundle
     * @return the bundle or null if there is no bundle with that location
     */
    BundleState getBundleByLocation(String location) {
        if (location == null)
            throw new IllegalArgumentException("Null location");

        for (BundleState aux : getBundles()) {
            String auxLocation = aux.getLocation();
            if (location.equals(auxLocation)) {
                return aux;
            }
        }
        return null;
    }

    /**
     * Get the set of bundles with the given symbolic name and version
     *
     * Note, this will get bundles regadless of their state. i.e. The returned bundles may have been UNINSTALLED
     *
     * @param symbolicName The bundle symbolic name
     * @param versionRange The optional bundle version
     * @return The bundles or an empty list if there is no bundle with that name and version
     */
    Set<BundleState> getBundles(String symbolicName, String versionRange) {
        Set<BundleState> resultSet = new HashSet<BundleState>();
        for (BundleState aux : bundleMap.values()) {
            if (symbolicName == null || symbolicName.equals(aux.getSymbolicName())) {
                if (versionRange == null || XVersionRange.parse(versionRange).isInRange(aux.getVersion())) {
                    resultSet.add(aux);
                }
            }
        }
        return Collections.unmodifiableSet(resultSet);
    }

    /**
     * Get the set of bundles that are in one of the given states. 
     * If the states pattern is null, it returns all registered bundles.
     *
     * @param states The binary or combination of states or null
     */
    Set<BundleState> getBundles(Integer states) {
        Set<BundleState> result = new HashSet<BundleState>();
        for (BundleState aux : bundleMap.values()) {
            if (states == null || (aux.getState() & states.intValue()) != 0)
                result.add(aux);
        }
        return Collections.unmodifiableSet(result);
    }

    @SuppressWarnings("unchecked")
    UserBundleState installBundle(Deployment dep) throws BundleException {
        ServiceName serviceName = installBundleInternal(serviceTarget, dep);
        ServiceController<UserBundleState> controller = (ServiceController<UserBundleState>) serviceContainer.getService(serviceName);
        FutureServiceValue<UserBundleState> future = new FutureServiceValue<UserBundleState>(controller);
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof BundleException)
                throw (BundleException) cause;
            throw new BundleException("Cannot install bundle: " + dep.getLocation(), ex);
        }
    }

    void uninstallBundle(UserBundleState bundleState, int options) {
        if (bundleState.aquireUninstallLock()) {
            try {
                int state = bundleState.getState();
                if (state == Bundle.UNINSTALLED)
                    return;

                // #2 If the bundle's state is ACTIVE, STARTING or STOPPING, the bundle is stopped
                if (bundleState.isFragment() == false) {
                    if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING) {
                        try {
                            bundleState.stopInternal(options);
                        } catch (Exception ex) {
                            // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is fired containing the
                            // exception
                            fireFrameworkError(bundleState, "Error stopping bundle: " + bundleState, ex);
                        }
                    }
                }

                // #3 This bundle's state is set to UNINSTALLED
                FrameworkEventsPlugin eventsPlugin = bundleState.getFrameworkState().getFrameworkEventsPlugin();
                bundleState.changeState(Bundle.UNINSTALLED, 0);

                // Check if the bundle has still active wires
                boolean hasActiveWires = bundleState.hasActiveWires();
                if (hasActiveWires == false) {
                    // #5 This bundle and any persistent storage area provided for this bundle by the Framework are removed
                    removeBundle(bundleState, options);
                }

                // #4 A bundle event of type BundleEvent.UNINSTALLED is fired
                eventsPlugin.fireBundleEvent(bundleState, BundleEvent.UNINSTALLED);

                if (hasActiveWires == false) {
                    // Remove the INSTALLED service
                    ServiceName serviceName = UserBundleState.getServiceName(bundleState.getBundleId());
                    ServiceController<?> controller = getServiceContainer().getService(serviceName);
                    controller.setMode(Mode.REMOVE);
                }

                // Remove other uninstalled bundles that now also have no active wires any more
                Set<BundleState> uninstalled = getBundles(Bundle.UNINSTALLED);
                for (BundleState aux : uninstalled) {
                    UserBundleState userBundle = UserBundleState.assertBundleState(aux);
                    if (userBundle.hasActiveWires() == false) {
                        removeBundle(userBundle, options);
                    }
                }
            } finally {
                bundleState.releaseUninstallLock();
            }
        }
    }

    void removeBundle(UserBundleState userBundle, int options) {
        log.tracef("Remove bundle: %s", userBundle);

        if ((options & Bundle.STOP_TRANSIENT) == 0) {
            BundleStorageState storageState = userBundle.getBundleStorageState();
            storageState.deleteBundleStorage();
        }

        ResolverPlugin resolverPlugin = getFrameworkState().getResolverPlugin();
        for (BundleRevision abr : userBundle.getRevisions()) {
            XModule resModule = abr.getResolverModule();
            resolverPlugin.removeModule(resModule);
        }

        FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
        eventsPlugin.fireBundleEvent(userBundle, BundleEvent.UNRESOLVED);

        ModuleManagerPlugin moduleManager = getFrameworkState().getModuleManagerPlugin();
        for (BundleRevision rev : userBundle.getRevisions()) {
            UserBundleRevision userRev = (UserBundleRevision) rev;
            if (userBundle.isFragment() == false) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(rev.getResolverModule());
                moduleManager.removeModule(identifier);
            }
            userRev.close();
        }

        bundleMap.remove(userBundle.getBundleId());
    }

    ServiceName installBundle(ServiceTarget serviceTarget, Module module) throws BundleException {
        BundleDeploymentPlugin plugin = getFrameworkState().getBundleDeploymentPlugin();
        Deployment dep = plugin.createDeployment(module);
        plugin.createOSGiMetaData(dep);
        return installBundleInternal(serviceTarget, dep);
    }

    private ServiceName installBundleInternal(ServiceTarget serviceTarget, Deployment dep) throws BundleException {
        if (dep == null)
            throw new IllegalArgumentException("Null deployment");

        // If a bundle containing the same location identifier is already installed,
        // the Bundle object for that bundle is returned.
        BundleState bundleState = getBundleByLocation(dep.getLocation());
        if (bundleState != null) {
            return bundleState.getServiceName();
        }

        ServiceName serviceName;
        try {
            // The storage state exists when we re-create the bundle from persistent storage
            BundleStorageState storageState = dep.getAttachment(BundleStorageState.class);
            long bundleId = storageState != null ? storageState.getBundleId() : getNextBundleId();
            
            // Check that we have valid metadata
            OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
            if (metadata == null) {
                BundleDeploymentPlugin plugin = getFrameworkState().getBundleDeploymentPlugin();
                metadata = plugin.createOSGiMetaData(dep);
            }
            
            // Create the bundle state
            if (metadata.getFragmentHost() == null) {
                serviceName = HostBundleService.addService(serviceTarget, getFrameworkState(), bundleId, dep);
            } else {
                serviceName = FragmentBundleService.addService(serviceTarget, getFrameworkState(), bundleId, dep);
            }
        } catch (RuntimeException rte) {
            VFSUtils.safeClose(dep.getRoot());
            throw rte;
        } catch (BundleException ex) {
            VFSUtils.safeClose(dep.getRoot());
            throw ex;
        }
        return serviceName;
    }

    long getNextBundleId() {
        return identityGenerator.incrementAndGet();
    }

    void uninstallBundle(Deployment dep) {
        Bundle bundle = dep.getAttachment(Bundle.class);
        UserBundleState bundleState = UserBundleState.assertBundleState(bundle);
        uninstallBundle(bundleState, 0);
    }

    void uninstallBundle(Module module) {
        ModuleIdentifier identifier = module.getIdentifier();
        uninstallBundle(identifier);
    }

    void uninstallBundle(ModuleIdentifier identifier) {
        try {
            ModuleManagerPlugin moduleManager = getFrameworkState().getModuleManagerPlugin();
            BundleState bundleState = moduleManager.getBundleState(identifier);
            bundleState.uninstall();
        } catch (BundleException ex) {
            log.errorf("Cannot uninstall module: " + identifier, ex);
        }
    }
    
    /**
     * Fire a framework error
     */
    void fireFrameworkError(BundleState bundleState, String context, Throwable t) {
        FrameworkEventsPlugin plugin = getFrameworkState().getFrameworkEventsPlugin();
        if (t instanceof BundleException) {
            plugin.fireFrameworkEvent(bundleState, FrameworkEvent.ERROR, t);
        } else if (bundleState != null) {
            plugin.fireFrameworkEvent(bundleState, FrameworkEvent.ERROR, new BundleException("Error " + context + " bundle: " + bundleState, t));
        } else {
            SystemBundleState systemBundle = injectedSystemBundle.getValue();
            plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.ERROR, new BundleException("Error " + context, t));
        }
    }

    /**
     * Fire a framework warning
     */
    void fireFrameworkWarning(BundleState bundleState, String context, Throwable t) {
        FrameworkEventsPlugin plugin = getFrameworkState().getFrameworkEventsPlugin();
        if (t instanceof BundleException) {
            plugin.fireFrameworkEvent(bundleState, FrameworkEvent.WARNING, t);
        } else if (bundleState != null) {
            plugin.fireFrameworkEvent(bundleState, FrameworkEvent.WARNING, new BundleException("Error " + context + " bundle: " + bundleState, t));
        } else {
            SystemBundleState systemBundle = injectedSystemBundle.getValue();
            plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.WARNING, new BundleException("Error " + context, t));
        }
    }

    static {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {

            public Object run() {
                List<String> execEnvironments = new ArrayList<String>();
                if (Java.isCompatible(Java.VERSION_1_5))
                    execEnvironments.add("J2SE-1.5");
                if (Java.isCompatible(Java.VERSION_1_6))
                    execEnvironments.add("JavaSE-1.6");

                String envlist = execEnvironments.toString();
                envlist = envlist.substring(1, envlist.length() - 1);
                OSGi_FRAMEWORK_EXECUTIONENVIRONMENT = envlist;

                OSGi_FRAMEWORK_OS_NAME = SecurityActions.getSystemProperty("os.name", null);
                OSGi_FRAMEWORK_OS_VERSION = getOSVersionInOSGiFormat();
                OSGi_FRAMEWORK_PROCESSOR = SecurityActions.getSystemProperty("os.arch", null);

                SecurityActions.setSystemProperty("org.osgi.vendor.framework", "org.jboss.osgi.framework");
                return null;
            }
        });
    }
    
    // Turn the OS version into an OSGi-compatible version. The spec says that an external operator
    // should do this by changing the framework properties, but this is pretty inconvenient and other
    // OSGi frameworks seem to automatically fix this too. The original os version is still available
    // in the "os.version" system property.
    static String getOSVersionInOSGiFormat() {
        StringBuilder osgiVersion = new StringBuilder();

        String sysVersion = SecurityActions.getSystemProperty("os.version", null);
        String[] elements = sysVersion.split("\\.");
        int i = 0;
        for (; i < 3 && i < elements.length; i++) {
            try {
                Integer.parseInt(elements[i]);
                if (i > 0)
                    osgiVersion.append('.');
                osgiVersion.append(elements[i]);
            } catch (NumberFormatException nfe) {
                break;
            }
        }

        if (i == 3 && elements.length > 3) {
            // All the parts were ok so far, now add the qualifier
            osgiVersion.append('.');
            osgiVersion.append(elements[3]);
        }

        return osgiVersion.toString();
    }
}
