package org.jboss.osgi.framework.internal;
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

import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BundleManager;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.StorageState;
import org.jboss.osgi.framework.internal.AbstractBundleState.BundleLock.Method;
import org.jboss.osgi.framework.util.Java;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.VersionRange;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.resource.Resource;

/**
 * The BundleManager is the central managing entity for OSGi bundles.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 29-Jun-2010
 */
final class BundleManagerPlugin extends AbstractPluginService<BundleManager> implements BundleManager {

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

    private static String implementationVersion;
    static {
        implementationVersion = BundleManagerPlugin.class.getPackage().getImplementationVersion();
    }

    final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
    final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    final InjectedValue<LockManagerPlugin> injectedLockManager = new InjectedValue<LockManagerPlugin>();
    final InjectedValue<Boolean> injectedFrameworkActive = new InjectedValue<Boolean>();

    private final FrameworkBuilder frameworkBuilder;
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private ServiceContainer serviceContainer;
    private ServiceTarget serviceTarget;

    static BundleManagerPlugin addService(ServiceTarget serviceTarget, FrameworkBuilder frameworkBuilder) {
        BundleManagerPlugin service = new BundleManagerPlugin(frameworkBuilder);
        ServiceBuilder<BundleManager> builder = serviceTarget.addService(Services.BUNDLE_MANAGER, service);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, service.injectedEnvironment);
        builder.addDependency(InternalServices.LOCK_MANAGER_PLUGIN, LockManagerPlugin.class, service.injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
        return service;
    }

    private BundleManagerPlugin(FrameworkBuilder frameworkBuilder) {
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
        LOGGER.infoFrameworkImplementation(implementationVersion);
        serviceContainer = context.getController().getServiceContainer();
        serviceTarget = context.getChildTarget();
        LOGGER.debugf("Framework properties");
        for (Entry<String, Object> entry : properties.entrySet()) {
            LOGGER.debugf(" %s = %s", entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void stop(StopContext context) {
        super.stop(context);
    }

    @Override
    public BundleManagerPlugin getValue() throws IllegalStateException {
        return this;
    }

    FrameworkBuilder getFrameworkBuilder() {
        return frameworkBuilder;
    }

    @Override
    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    @Override
    public SystemBundleState getSystemBundle() {
        return injectedSystemBundle.getOptionalValue();
    }

    /**
     * True if the framework has reached the {@link Services#FRAMEWORK_CREATE} state
     */
    boolean isFrameworkCreated() {
        return getFrameworkState() != null;
    }

    void assertFrameworkCreated() {
        if (isFrameworkCreated() == false)
            throw MESSAGES.illegalStateFrameworkNotActive();
    }

    @Override
    public boolean isFrameworkActive() {
        return Boolean.TRUE.equals(injectedFrameworkActive.getOptionalValue());
    }

    FrameworkState getFrameworkState() {
        return injectedFramework.getOptionalValue();
    }

    @Override
    public Object getProperty(String key) {
        Object value = properties.get(key);
        if (value == null)
            value = SecurityActions.getSystemProperty(key, null);
        return value;
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> m = new HashMap<String, Object>();
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            m.put(entry.getKey().toString(), entry.getValue());
        }
        m.putAll(properties);
        return m;
    }

    void setProperty(String key, Object value) {
        if (isFrameworkCreated())
            throw MESSAGES.illegalStateCannotAddProperty();

        properties.put(key, value);
    }

    static ServiceName getServiceName(Deployment dep) {
        // Currently the bundleId is needed for uniqueness because of
        // [MSC-97] Cannot re-install service with same name
        Long bundleId = dep.getAttachment(Long.class);
        return ServiceName.of(InternalServices.BUNDLE_BASE_NAME, "" + bundleId, "" + dep.getSymbolicName(), "" + dep.getVersion());
    }

    Set<XBundle> getBundles() {
        Set<XBundle> result = new HashSet<XBundle>();
        XEnvironment env = injectedEnvironment.getValue();
        for (Resource aux : env.getResources(XEnvironment.ALL_IDENTITY_TYPES)) {
            XBundle bundle = ((XBundleRevision) aux).getBundle();
            if (bundle.getState() != Bundle.UNINSTALLED)
                result.add(bundle);
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<XBundle> getBundles(Integer states) {
        Set<XBundle> result = new HashSet<XBundle>();
        XEnvironment env = injectedEnvironment.getValue();
        for (Resource aux : env.getResources(XEnvironment.ALL_IDENTITY_TYPES)) {
            XBundle bundle = ((XBundleRevision) aux).getBundle();
            if (states == null || (bundle.getState() & states.intValue()) != 0)
                result.add(bundle);
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public XBundle getBundleById(long bundleId) {
        if (bundleId == 0) {
            return getFrameworkState().getSystemBundle();
        }
        XEnvironment env = injectedEnvironment.getValue();
        Collection<XResource> resources = env.getResources(XEnvironment.ALL_IDENTITY_TYPES);
        for (Resource aux : resources) {
            XBundle bundle = ((XBundleRevision) aux).getBundle();
            if (bundle.getBundleId() == bundleId) {
                return bundle;
            }
        }
        return null;
    }

    @Override
    public XBundle getBundleByLocation(String location) {
        assert location != null : "Null location";
        for (XBundle aux : getBundles()) {
            String auxLocation = aux.getLocation();
            if (location.equals(auxLocation)) {
                return aux;
            }
        }
        return null;
    }

    @Override
    public Set<XBundle> getBundles(String symbolicName, String versionRange) {
        Set<XBundle> resultSet = new HashSet<XBundle>();
        for (XBundle aux : getBundles(null)) {
            if (symbolicName == null || symbolicName.equals(aux.getSymbolicName())) {
                if (versionRange == null || VersionRange.parse(versionRange).isInRange(aux.getVersion())) {
                    resultSet.add(aux);
                }
            }
        }
        return Collections.unmodifiableSet(resultSet);
    }

    @Override
    public ServiceName installBundle(Deployment dep, ServiceListener<XBundle> listener) throws BundleException {
        if (dep == null)
            throw MESSAGES.illegalArgumentNull("deployment");

        ServiceName serviceName;

        // If a bundle containing the same location identifier is already installed,
        // the Bundle object for that bundle is returned.
        XBundle bundle = getBundleByLocation(dep.getLocation());
        if (bundle instanceof AbstractBundleState) {
            LOGGER.debugf("Installing an already existing bundle: %s", dep);
            AbstractBundleState bundleState = AbstractBundleState.assertBundleState(bundle);
            serviceName = bundleState.getServiceName(Bundle.INSTALLED);
            VFSUtils.safeClose(dep.getRoot());
        } else {
            try {
                Long bundleId;
                String symbolicName = dep.getSymbolicName();
                XEnvironment env = injectedEnvironment.getValue();

                // The storage state exists when we re-create the bundle from persistent storage
                StorageState storageState = dep.getAttachment(StorageState.class);
                if (storageState != null) {
                    LOGGER.debugf("Found storage state: %s", storageState);
                    dep.setAutoStart(storageState.isPersistentlyStarted());
                    bundleId = env.nextResourceIdentifier(storageState.getBundleId(), symbolicName);
                } else {
                    bundleId = env.nextResourceIdentifier(null, symbolicName);
                }
                dep.addAttachment(Long.class, bundleId);

                // Check that we have valid metadata
                OSGiMetaData metadata = dep.getAttachment(OSGiMetaData.class);
                if (metadata == null) {
                    DeploymentFactoryPlugin plugin = getFrameworkState().getDeploymentFactoryPlugin();
                    metadata = plugin.createOSGiMetaData(dep);
                }

                // Create the bundle services
                if (metadata.getFragmentHost() == null) {
                    serviceName = HostBundleInstalledService.addService(serviceTarget, getFrameworkState(), dep, listener);
                    HostBundleActiveService.addService(serviceTarget, getFrameworkState(), serviceName.getParent());
                } else {
                    serviceName = FragmentBundleInstalledService.addService(serviceTarget, getFrameworkState(), dep, listener);
                }
            } catch (RuntimeException rte) {
                VFSUtils.safeClose(dep.getRoot());
                throw rte;
            } catch (BundleException ex) {
                VFSUtils.safeClose(dep.getRoot());
                throw ex;
            }
        }
        dep.addAttachment(ServiceName.class, serviceName);
        return serviceName;
    }

    @Override
    public void uninstallBundle(Deployment dep) {
        Bundle bundle = dep.getAttachment(Bundle.class);
        UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
        uninstallBundle(userBundle, 0);
    }

    void uninstallBundle(UserBundleState userBundle, int options) {
        if (userBundle.aquireBundleLock(Method.UNINSTALL)) {
            try {
                int state = userBundle.getState();
                if (state == Bundle.UNINSTALLED)
                    return;

                // #2 If the bundle's state is ACTIVE, STARTING or STOPPING, the bundle is stopped
                if (userBundle.isFragment() == false) {
                    if (state == Bundle.ACTIVE || state == Bundle.STARTING || state == Bundle.STOPPING) {
                        try {
                            userBundle.stopInternal(options);
                        } catch (Exception ex) {
                            // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is fired
                            fireFrameworkError(userBundle, "stopping bundle: " + userBundle, ex);
                        }
                    }
                }

                // #3 This bundle's state is set to UNINSTALLED
                FrameworkEventsPlugin eventsPlugin = userBundle.getFrameworkState().getFrameworkEventsPlugin();
                userBundle.changeState(Bundle.UNINSTALLED, 0);

                // Remove the bundle services
                userBundle.removeServices();

                // Check if the bundle has still active wires
                boolean hasActiveWires = userBundle.hasActiveWires();
                if (hasActiveWires == false) {
                    // #5 This bundle and any persistent storage area provided for this bundle by the Framework are removed
                    removeBundle(userBundle, options);
                }

                // #4 A bundle event of type BundleEvent.UNINSTALLED is fired
                eventsPlugin.fireBundleEvent(userBundle, BundleEvent.UNINSTALLED);

                LOGGER.infoBundleUninstalled(userBundle);

                // Remove other uninstalled bundles that now also have no active wires any more
                Set<XBundle> uninstalled = getBundles(Bundle.UNINSTALLED);
                for (Bundle auxState : uninstalled) {
                    UserBundleState auxUser = UserBundleState.assertBundleState(auxState);
                    if (auxUser.hasActiveWires() == false) {
                        removeBundle(auxUser, options);
                    }
                }
            } finally {
                userBundle.releaseBundleLock(Method.UNINSTALL);
            }
        }
    }

    void removeBundle(UserBundleState userBundle, int options) {
        LOGGER.tracef("Start removing bundle: %s", userBundle);

        if ((options & Bundle.STOP_TRANSIENT) == 0) {
            BundleStoragePlugin storagePlugin = getFrameworkState().getBundleStoragePlugin();
            storagePlugin.deleteStorageState(userBundle.getStorageState());
        }

        XEnvironment env = getFrameworkState().getEnvironment();
        for (XBundleRevision abr : userBundle.getAllBundleRevisions()) {
            env.uninstallResources(abr);
        }

        FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
        eventsPlugin.fireBundleEvent(userBundle, BundleEvent.UNRESOLVED);

        ModuleManagerPlugin moduleManager = getFrameworkState().getModuleManagerPlugin();
        for (XBundleRevision brev : userBundle.getAllBundleRevisions()) {
            UserBundleRevision userRev = (UserBundleRevision) brev;
            if (userRev.isFragment() == false) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(brev);
                moduleManager.removeModule(brev, identifier);
            }
            userRev.close();
        }

        LOGGER.debugf("Removed bundle: %s", userBundle);
    }

    @Override
    public ServiceName getServiceName(XBundle bundle, int state) {
        ServiceName result = null;
        if (bundle instanceof AbstractBundleState) {
            AbstractBundleState bundleState = (AbstractBundleState)bundle;
            result = bundleState.getServiceName(state);
        }
        return result;
    }

    void setServiceMode(ServiceName serviceName, Mode mode) {
        ServiceController<?> controller = serviceContainer.getService(serviceName);
        if (controller == null)
            LOGGER.debugf("Cannot set mode %s on non-existing service: %s", mode, serviceName);
        else
            setServiceMode(controller, mode);
    }

    void setServiceMode(ServiceController<?> controller, Mode mode) {
        try {
            LOGGER.tracef("Set mode %s on service: %s", mode, controller.getName());
            controller.setMode(mode);
        } catch (IllegalArgumentException rte) {
            // [MSC-105] Cannot determine whether container is shutting down
            if (rte.getMessage().equals("Container is shutting down") == false)
                throw rte;
        }
    }

    void fireFrameworkError(Bundle bundle, String context, Throwable t) {
        FrameworkEventsPlugin plugin = getFrameworkState().getFrameworkEventsPlugin();
        if (t instanceof BundleException) {
            plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, t);
        } else if (bundle != null) {
            plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, new BundleException("Error " + context + " bundle: " + bundle, t));
        } else {
            SystemBundleState systemBundle = injectedSystemBundle.getValue();
            plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.ERROR, new BundleException("Error " + context, t));
        }
    }

    void fireFrameworkWarning(AbstractBundleState bundleState, String context, Throwable t) {
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
                if (Java.isCompatible(Java.VERSION_1_1)) {
                    execEnvironments.add("OSGi/Minimum-1.1");
                    execEnvironments.add("J2SE-1.1");
                }
                if (Java.isCompatible(Java.VERSION_1_2)) {
                    execEnvironments.add("OSGi/Minimum-1.2");
                    execEnvironments.add("J2SE-1.2");
                }
                if (Java.isCompatible(Java.VERSION_1_3))
                    execEnvironments.add("J2SE-1.3");
                if (Java.isCompatible(Java.VERSION_1_4))
                    execEnvironments.add("J2SE-1.4");
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