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

import static org.jboss.osgi.framework.Services.BUNDLE_BASE_NAME;
import static org.jboss.osgi.framework.internal.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.internal.FrameworkMessages.MESSAGES;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.jboss.modules.ModuleIdentifier;
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
import org.jboss.osgi.framework.BundleManagerService;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.util.Java;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.VersionRange;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.Version;
import org.osgi.resource.Resource;

/**
 * The BundleManager is the central managing entity for OSGi bundles.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 29-Jun-2010
 */
public final class BundleManager extends AbstractService<BundleManagerService> implements BundleManagerService {

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

    final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
    final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();

    private final FrameworkBuilder frameworkBuilder;
    private final AtomicLong identityGenerator = new AtomicLong();
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean();
    private final ServiceTarget serviceTarget;
    private ServiceContainer serviceContainer;

    static BundleManager addService(ServiceTarget serviceTarget, FrameworkBuilder frameworkBuilder) {
        BundleManager service = new BundleManager(frameworkBuilder, serviceTarget);
        ServiceBuilder<BundleManagerService> builder = serviceTarget.addService(org.jboss.osgi.framework.Services.BUNDLE_MANAGER, service);
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, service.injectedEnvironment);
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
        return service;
    }

    private BundleManager(FrameworkBuilder frameworkBuilder, ServiceTarget serviceTarget) {
        this.frameworkBuilder = frameworkBuilder;
        this.serviceTarget = serviceTarget;

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

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                shutdownInitiated.set(true);
            }
        });
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        LOGGER.infoFrameworkImplementation(implementationTitle, implementationVersion);
        serviceContainer = context.getController().getServiceContainer();
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
    public BundleManager getValue() throws IllegalStateException {
        return this;
    }

    FrameworkBuilder getFrameworkBuilder() {
        return frameworkBuilder;
    }

    ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    SystemBundleState getSystemBundle() {
        return injectedSystemBundle.getValue();
    }

    boolean isFrameworkActive() {
        return shutdownInitiated.get() == false && getFrameworkState() != null;
    }

    void assertFrameworkActive() {
        if (isFrameworkActive() == false)
            throw MESSAGES.illegalStateFrameworkNotActive();
    }

    FrameworkState getFrameworkState() {
        return injectedFramework.getOptionalValue();
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
            throw MESSAGES.illegalStateCannotAddProperty();

        properties.put(key, value);
    }

    static ServiceName getServiceName(Deployment dep) {
        long bundleId = dep.getAttachment(BundleId.class).longValue();
        return getServiceNameInternal(bundleId, dep.getSymbolicName(), Version.parseVersion(dep.getVersion()));
    }

    @Override
    public ServiceName getServiceName(Bundle bundle) {
        return getServiceNameInternal(bundle.getBundleId(), bundle.getSymbolicName(), bundle.getVersion());
    }

    private static ServiceName getServiceNameInternal(long bundleId, String symbolicName, Version version) {
        // Currently the bundleId is needed for uniqueness because of
        // [MSC-97] Cannot re-install service with same name
        return ServiceName.of(BUNDLE_BASE_NAME, "" + bundleId, "" + symbolicName, "" + version);
    }

    /**
     * Get the set of installed bundles.
     * Bundles in state UNINSTALLED are not returned.
     */
    Set<AbstractBundleState> getBundles() {
        Set<AbstractBundleState> result = new HashSet<AbstractBundleState>();
        XEnvironment env = injectedEnvironment.getValue();
        for (Resource aux : env.getResources(null)) {
            if (aux instanceof AbstractBundleRevision) {
                AbstractBundleRevision brev = (AbstractBundleRevision) aux;
                AbstractBundleState bundleState = brev.getBundleState();
                if (bundleState.getState() != Bundle.UNINSTALLED)
                    result.add(bundleState);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Get the set of bundles that are in one of the given states.
     * If the states pattern is null, it returns all registered bundles.
     *
     * @param states The binary or combination of states or null
     */
    Set<AbstractBundleState> getBundles(Integer states) {
        Set<AbstractBundleState> result = new HashSet<AbstractBundleState>();
        XEnvironment env = injectedEnvironment.getValue();
        for (Resource aux : env.getResources(null)) {
            if (aux instanceof AbstractBundleRevision) {
                AbstractBundleRevision brev = (AbstractBundleRevision) aux;
                AbstractBundleState bundleState = brev.getBundleState();
                if (states == null || (bundleState.getState() & states.intValue()) != 0)
                    result.add(bundleState);
            }
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
    AbstractBundleState getBundleById(long bundleId) {
        if (bundleId == 0) {
            return getFrameworkState().getSystemBundle();
        }
        XEnvironment env = injectedEnvironment.getValue();
        for (Resource aux : env.getResources(null)) {
            if (aux instanceof AbstractBundleRevision) {
                AbstractBundleRevision brev = (AbstractBundleRevision) aux;
                AbstractBundleState bundleState = brev.getBundleState();
                if (bundleState.getBundleId() == bundleId) {
                    return bundleState;
                }
            }
        }
        return null;
    }

    /**
     * Get a bundle by location
     *
     * Note, this will get the bundle regadless of its state. i.e. The returned bundle may have been UNINSTALLED
     *
     * @param location the location of the bundle
     * @return the bundle or null if there is no bundle with that location
     */
    AbstractBundleState getBundleByLocation(String location) {
        assert location != null : "Null location";
        for (AbstractBundleState aux : getBundles()) {
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
    Set<AbstractBundleState> getBundles(String symbolicName, String versionRange) {
        Set<AbstractBundleState> resultSet = new HashSet<AbstractBundleState>();
        for (AbstractBundleState aux : getBundles(null)) {
            if (symbolicName == null || symbolicName.equals(aux.getSymbolicName())) {
                if (versionRange == null || VersionRange.parse(versionRange).isInRange(aux.getVersion())) {
                    resultSet.add(aux);
                }
            }
        }
        return Collections.unmodifiableSet(resultSet);
    }

    @Override
    public ServiceName installBundle(ServiceTarget serviceTarget, Deployment deployment) throws BundleException {
        if (deployment == null) 
            throw MESSAGES.illegalArgumentNull("deployment");
        ServiceName serviceName;
        // If a bundle containing the same location identifier is already installed,
        // the Bundle object for that bundle is returned.
        AbstractBundleState bundleState = getBundleByLocation(deployment.getLocation());
        if (bundleState != null) {
            serviceName = bundleState.getServiceName(Bundle.INSTALLED);
            VFSUtils.safeClose(deployment.getRoot());
        } else {
            try {
                // The storage state exists when we re-create the bundle from persistent storage
                BundleStorageState storageState = deployment.getAttachment(BundleStorageState.class);
                long bundleId = (storageState != null ? storageState.getBundleId() : nextBundleId());
                deployment.addAttachment(BundleId.class, new BundleId(bundleId));

                // Check that we have valid metadata
                OSGiMetaData metadata = deployment.getAttachment(OSGiMetaData.class);
                if (metadata == null) {
                    DeploymentFactoryPlugin plugin = getFrameworkState().getDeploymentFactoryPlugin();
                    metadata = plugin.createOSGiMetaData(deployment);
                }

                // Create the bundle services
                if (metadata.getFragmentHost() == null) {
                    serviceName = HostBundleInstalledService.addService(serviceTarget, getFrameworkState(), deployment);
                    HostBundleResolvedService.addService(serviceTarget, getFrameworkState(), serviceName.getParent());
                    HostBundleActiveService.addService(serviceTarget, getFrameworkState(), serviceName.getParent());
                } else {
                    serviceName = FragmentBundleInstalledService.addService(serviceTarget, getFrameworkState(), deployment);
                    FragmentBundleResolvedService.addService(serviceTarget, getFrameworkState(), serviceName.getParent());
                }
            } catch (RuntimeException rte) {
                VFSUtils.safeClose(deployment.getRoot());
                throw rte;
            } catch (BundleException ex) {
                VFSUtils.safeClose(deployment.getRoot());
                throw ex;
            }
        }
        deployment.addAttachment(ServiceName.class, serviceName);
        return serviceName;
    }

    long nextBundleId() {
        return identityGenerator.incrementAndGet();
    }

    @Override
    public void uninstallBundle(Deployment dep) {
        Bundle bundle = dep.getAttachment(Bundle.class);
        UserBundleState userBundle = UserBundleState.assertBundleState(bundle);
        uninstallBundle(userBundle, 0);
    }

    void uninstallBundle(UserBundleState userBundle, int options) {
        if (userBundle.aquireUninstallLock()) {
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
                            // If Bundle.stop throws an exception, a Framework event of type FrameworkEvent.ERROR is fired containing the
                            // exception
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

                // Remove other uninstalled bundles that now also have no active wires any more
                Set<AbstractBundleState> uninstalled = getBundles(Bundle.UNINSTALLED);
                for (AbstractBundleState auxState : uninstalled) {
                    UserBundleState auxUser = UserBundleState.assertBundleState(auxState);
                    if (auxUser.hasActiveWires() == false) {
                        removeBundle(auxUser, options);
                    }
                }
            } finally {
                userBundle.releaseUninstallLock();
            }
        }
    }

    void removeBundle(UserBundleState userBundle, int options) {
        LOGGER.tracef("Start removing bundle: %s", userBundle);

        if ((options & Bundle.STOP_TRANSIENT) == 0) {
            BundleStorageState storageState = userBundle.getBundleStorageState();
            storageState.deleteBundleStorage();
        }

        XEnvironment env = getFrameworkState().getEnvironment();
        for (AbstractBundleRevision abr : userBundle.getAllBundleRevisions()) {
            env.uninstallResources(abr);
        }

        FrameworkEventsPlugin eventsPlugin = getFrameworkState().getFrameworkEventsPlugin();
        eventsPlugin.fireBundleEvent(userBundle, BundleEvent.UNRESOLVED);

        ModuleManagerPlugin moduleManager = getFrameworkState().getModuleManagerPlugin();
        for (AbstractBundleRevision rev : userBundle.getAllBundleRevisions()) {
            UserBundleRevision userRev = (UserBundleRevision) rev;
            if (userBundle.isFragment() == false) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(rev);
                moduleManager.removeModule(identifier);
            }
            userRev.close();
        }

        LOGGER.debugf("Removed bundle: %s", userBundle);
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

    void fireFrameworkError(AbstractBundleState bundleState, String context, Throwable t) {
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
