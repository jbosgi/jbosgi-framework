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
import static org.jboss.osgi.framework.internal.InternalConstants.REVISION_IDENTIFIER_KEY;
import static org.jboss.osgi.framework.spi.IntegrationConstants.OSGI_METADATA_KEY;
import static org.jboss.osgi.framework.spi.IntegrationConstants.STORAGE_STATE_KEY;
import java.io.InputStream;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.modules.ModuleIdentifier;
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
import org.jboss.osgi.framework.Constants;
import org.jboss.osgi.framework.Services;
import org.jboss.osgi.framework.spi.AbstractIntegrationService;
import org.jboss.osgi.framework.spi.BundleLifecycle;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.StorageManager;
import org.jboss.osgi.framework.spi.DeploymentProvider;
import org.jboss.osgi.framework.spi.FrameworkBuilder;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.FrameworkStartLevelSupport;
import org.jboss.osgi.framework.spi.FrameworkWiringLock;
import org.jboss.osgi.framework.spi.IntegrationServices;
import org.jboss.osgi.framework.spi.LockManager;
import org.jboss.osgi.framework.spi.XLockableEnvironment;
import org.jboss.osgi.framework.spi.LockManager.Method;
import org.jboss.osgi.framework.spi.LockManager.LockableItem;
import org.jboss.osgi.framework.spi.ModuleManager;
import org.jboss.osgi.framework.spi.StorageState;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.resolver.XBundle;
import org.jboss.osgi.resolver.XBundleRevision;
import org.jboss.osgi.resolver.XEnvironment;
import org.jboss.osgi.resolver.XResolveContext;
import org.jboss.osgi.resolver.XResolver;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.vfs.VFSUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.resource.Resource;
import org.osgi.service.resolver.ResolutionException;
import org.jboss.osgi.framework.spi.LockManager.LockContext;

/**
 * The BundleManager is the central managing entity for OSGi bundles.
 *
 * @author thomas.diesler@jboss.com
 * @author David Bosschaert
 * @since 29-Jun-2010
 */
final class BundleManagerPlugin extends AbstractIntegrationService<BundleManager> implements BundleManager {

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
    // The framework version. This is the version of the org.osgi.framework package in R5
    private static String OSGi_FRAMEWORK_VERSION = "1.7";

    enum UniquenessPolicy {
        // Specifies the framework will allow multiple bundles to be installed having the same symbolic name and version.
        multiple,
        // Specifies the framework will only allow a single bundle to be installed for a given symbolic name and version.
        // It will be an error to install a bundle or update a bundle to have the same symbolic name and version as another installed bundle.
        single,
        // Specifies the framework must consult the bundle collision hook services to determine if it will be an error to install
        // a bundle or update a bundle to have the same symbolic name and version as another installed bundle.
        // If no bundle collision hook services are registered, then it will be an error to install a bundle or update a bundle
        // to have the same symbolic name and version as another installed bundle.
        managed
    }

    private static String implementationVersion;
    static {
        implementationVersion = BundleManagerPlugin.class.getPackage().getImplementationVersion();
    }

    private final InjectedValue<XEnvironment> injectedEnvironment = new InjectedValue<XEnvironment>();
    private final InjectedValue<LockManager> injectedLockManager = new InjectedValue<LockManager>();

    final InjectedValue<FrameworkState> injectedFramework = new InjectedValue<FrameworkState>();
    final InjectedValue<SystemBundleState> injectedSystemBundle = new InjectedValue<SystemBundleState>();
    final InjectedValue<Boolean> injectedFrameworkActive = new InjectedValue<Boolean>();

    private final FrameworkBuilder frameworkBuilder;
    private final ShutdownContainer shutdownContainer;
    private final Set<ExecutorService> executorServices = new HashSet<ExecutorService>();
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final AtomicInteger managerState = new AtomicInteger(Bundle.INSTALLED);
    private final AtomicBoolean managerStopped = new AtomicBoolean();
    private final ServiceContainer serviceContainer;
    private final UniquenessPolicy uniquenessPolicy;
    private Framework framework;
    private SystemBundleState cachedSystemBundle;
    private ServiceTarget serviceTarget;
    private int stoppedEvent;

    @SuppressWarnings("deprecation")
    BundleManagerPlugin(ServiceContainer serviceContainer, FrameworkBuilder frameworkBuilder) {
        super(Services.BUNDLE_MANAGER);
        this.serviceContainer = serviceContainer;
        this.frameworkBuilder = frameworkBuilder;
        this.stoppedEvent = FrameworkEvent.STOPPED;

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
        if (getProperty(Constants.FRAMEWORK_UUID) == null)
            setProperty(Constants.FRAMEWORK_UUID, UUID.randomUUID().toString());
        if (getProperty(Constants.FRAMEWORK_BSNVERSION) == null)
            setProperty(Constants.FRAMEWORK_BSNVERSION, Constants.FRAMEWORK_BSNVERSION_MANAGED);

        setProperty(Constants.FRAMEWORK_VENDOR, OSGi_FRAMEWORK_VENDOR);
        setProperty(Constants.FRAMEWORK_VERSION, OSGi_FRAMEWORK_VERSION);

        // Get and cache the BSNVERSION
        uniquenessPolicy = UniquenessPolicy.valueOf((String)getProperty(Constants.FRAMEWORK_BSNVERSION));

        boolean allowContainerShutdown = frameworkBuilder.getServiceContainer() == null;
        shutdownContainer = new ShutdownContainer(serviceContainer, allowContainerShutdown);
    }

    @Override
    protected void addServiceDependencies(ServiceBuilder<BundleManager> builder) {
        builder.addDependency(Services.ENVIRONMENT, XEnvironment.class, injectedEnvironment);
        builder.addDependency(IntegrationServices.LOCK_MANAGER_PLUGIN, LockManager.class, injectedLockManager);
        builder.setInitialMode(Mode.ON_DEMAND);
    }

    @Override
    public void start(StartContext context) throws StartException {
        super.start(context);
        LOGGER.infoFrameworkImplementation(implementationVersion);
        serviceTarget = context.getChildTarget();
        LOGGER.debugf("Framework properties");
        for (Entry<String, Object> entry : properties.entrySet()) {
            LOGGER.debugf(" %s = %s", entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void stop(StopContext context) {
        XEnvironment env = injectedEnvironment.getValue();
        for (XResource res : env.getResources(XEnvironment.ALL_IDENTITY_TYPES)) {
            if (res instanceof UserBundleRevision) {
                UserBundleRevision userRev = (UserBundleRevision) res;
                userRev.close();
            }
        }
    }

    @Override
    protected BundleManager createServiceValue(StartContext startContext) {
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
    public ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    @Override
    public SystemBundleState getSystemBundle() {
        return injectedSystemBundle.getOptionalValue();
    }

    BundleContext getSystemContext() {
        SystemBundleState sysbundle = injectedSystemBundle.getOptionalValue();
        return sysbundle != null ? sysbundle.getBundleContext() : null;
    }

    FrameworkStartLevel getFrameworkStartLevel() {
        return getFrameworkState().getFrameworkStartLevel();
    }

    Framework getFramework() {
        return framework;
    }

    void setFramework(Framework framework) {
        this.framework = framework;
    }

    UniquenessPolicy getUniquenessPolicy() {
        return uniquenessPolicy;
    }

    /**
     * Required by spec:
     *
     * {@link Framework} The Framework object from the launching API.
     * {@link FrameworkStartLevel} The Framework Start Level.
     * {@link FrameworkWiring} The Framework Wiring.
     *
     * Proprietary extensions:
     *
     * {@link ServiceContainer} The Bundle metadata.
     * {@link XEnvironment} The Bundle's storage state.
     */
    @SuppressWarnings("unchecked")
    <T> T adapt(Class<T> type) {
        if (!hasStopped()) {
            if (type.isAssignableFrom(FrameworkStartLevel.class)) {
                return (T) getFrameworkStartLevel();
            } else if (type.isAssignableFrom(Framework.class)) {
                return (T) getFramework();
            } else if (type.isAssignableFrom(FrameworkWiring.class)) {
                return (T) getFrameworkState().getFrameworkWiring();
            } else if (type.isAssignableFrom(ServiceContainer.class)) {
                return (T) getServiceContainer();
            } else if (type.isAssignableFrom(XEnvironment.class)) {
                return (T) getFrameworkState().getEnvironment();
            }
        }
        return null;
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

    static Version getFrameworkVersion() {
        Version version;
        String versionSpec = BundleManagerPlugin.class.getPackage().getImplementationVersion();
        try {
            version = Version.parseVersion(versionSpec);
        } catch (IllegalArgumentException ex) {
            if (versionSpec.endsWith("-SNAPSHOT")) {
                versionSpec = versionSpec.substring(0, versionSpec.length() - 9);
            }
            version = Version.parseVersion(versionSpec);
        }
        return version;
    }

    int getManagerState() {
        return managerState.get();
    }

    void setManagerState(int state) {
        managerState.set(state);
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

    @Override
    public Set<XBundle> getBundles() {
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
        XEnvironment env = injectedEnvironment.getValue();
        XBundleRevision brev = (XBundleRevision) env.getResourceById(bundleId);
        return brev != null ? brev.getBundle() : null;
    }

    @Override
    public XBundle getBundleByLocation(String location) {
        assert location != null : "Null location";
        if (Constants.SYSTEM_BUNDLE_LOCATION.equals(location)) {
            return getSystemBundle();
        }
        for (XBundle aux : getBundles()) {
            String auxLocation = aux.getLocation();
            if (location.equals(auxLocation)) {
                return aux;
            }
        }
        return null;
    }

    @Override
    public Set<XBundle> getBundles(String symbolicName, VersionRange versionRange) {
        Set<XBundle> resultSet = new HashSet<XBundle>();
        if (Constants.SYSTEM_BUNDLE_SYMBOLICNAME.equals(symbolicName) && versionRange == null) {
            resultSet.add(getSystemBundle());
        } else {
            for (XBundle aux : getBundles(null)) {
                if (symbolicName == null || symbolicName.equals(aux.getSymbolicName())) {
                    if (versionRange == null || versionRange.includes(aux.getVersion())) {
                        resultSet.add(aux);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(resultSet);
    }

    XBundleRevision createBundleRevisionLifecycle(BundleContext context, Deployment dep) throws BundleException {
        return getBundleLifecycle().createBundleRevision(context, dep);
    }

    @Override
    public XBundleRevision createBundleRevision(BundleContext context, Deployment dep, ServiceTarget serviceTarget) throws BundleException {
        if (context == null)
            throw MESSAGES.illegalArgumentNull("context");
        if (dep == null)
            throw MESSAGES.illegalArgumentNull("deployment");

        if (serviceTarget == null)
            serviceTarget = getServiceTarget();

        String symbolicName = dep.getSymbolicName();
        Version version = Version.parseVersion(dep.getVersion());

        // If a bundle containing the same location identifier is already installed,
        // the Bundle object for that bundle is returned.
        boolean isBundleUpdate = dep.isBundleUpdate();
        if (isBundleUpdate == false) {
            XBundle bundle = getBundleByLocation(dep.getLocation());
            if (bundle != null) {
                LOGGER.debugf("Installing an already existing bundle: %s", dep);
                VFSUtils.safeClose(dep.getRoot());
                return bundle.getBundleRevision();
            }

            // Check for symbolic name, version uniqueness
            checkUniqunessPolicy(context.getBundle(), symbolicName, version, CollisionHook.INSTALLING);
        }

        UserBundleRevision brev;
        try {

            // Create the revision identifier
            RevisionIdentifier revIdentifier = dep.getAttachment(REVISION_IDENTIFIER_KEY);
            if (revIdentifier == null) {
                revIdentifier = createRevisionIdentifier(symbolicName, dep);
                dep.putAttachment(REVISION_IDENTIFIER_KEY, revIdentifier);
            }

            // Check that we have valid metadata
            OSGiMetaData metadata = dep.getAttachment(OSGI_METADATA_KEY);
            if (metadata == null) {
                DeploymentProvider plugin = getFrameworkState().getDeploymentProvider();
                metadata = plugin.createOSGiMetaData(dep);
            }

            // Create the bundle services
            if (metadata.getFragmentHost() == null) {
                brev = new HostBundleRevisionFactory(getFrameworkState(), context, dep, serviceTarget).create();
            } else {
                brev = new FragmentBundleRevisionFactory(getFrameworkState(), context, dep, serviceTarget).create();
            }
        } catch (RuntimeException rte) {
            VFSUtils.safeClose(dep.getRoot());
            throw rte;
        } catch (BundleException ex) {
            VFSUtils.safeClose(dep.getRoot());
            throw ex;
        }

        return brev;
    }

    private RevisionIdentifier createRevisionIdentifier(String symbolicName, Deployment dep) {
        Long resourceId;
        StorageState storageState = dep.getAttachment(STORAGE_STATE_KEY);
        if (!dep.isBundleUpdate() && storageState != null) {
            resourceId = new Long(storageState.getBundleId());
        } else {
            XEnvironment env = injectedEnvironment.getValue();
            resourceId = env.nextResourceIdentifier(null, symbolicName);
        }
        return new RevisionIdentifier(resourceId);
    }

    void resolveBundleLifecycle(XBundle bundle) throws ResolutionException {
        getBundleLifecycle().resolve(bundle);
    }

    @Override
    public void resolveBundle(XBundle bundle) throws ResolutionException {
        LockManager lockManager = getFrameworkState().getLockManager();
        LockContext lockContext = null;
        try {
            LockableItem wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
            lockContext = lockManager.lockItems(Method.RESOLVE, (LockableItem) bundle, wireLock);
            Set<XBundleRevision> mandatory = Collections.singleton(bundle.getBundleRevision());
            XEnvironment environment = getFrameworkState().getEnvironment();
            XResolver resolver = getFrameworkState().getResolver();
            XResolveContext context = resolver.createResolveContext(environment, mandatory, null);
            resolver.resolveAndApply(context);
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    void startBundleLifecycle(XBundle bundle, int options) throws BundleException {
        BundleLifecycle bundleLifecycle = getBundleLifecycle();
        bundleLifecycle.start(bundle, options);
    }

    @Override
    public void startBundle(XBundle bundle, int options) throws BundleException {

        // If this bundle's state is ACTIVE then this method returns immediately.
        if (bundle.getState() == Bundle.ACTIVE)
            return;

        LockManager lockManager = getFrameworkState().getLockManager();
        LockContext lockContext = null;
        try {
            AbstractBundleState<?> bundleState = AbstractBundleState.assertBundleState(bundle);
            lockContext = lockManager.lockItems(Method.START, (LockableItem)bundle);
            bundleState.startInternal(options);
        } catch (BundleException ex) {
            LOGGER.debugf(ex, "Cannot start bundle: %s", bundle);
            throw ex;
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    void stopBundleLifecycle(XBundle bundle, int options) throws BundleException {
        BundleLifecycle bundleLifecycle = getBundleLifecycle();
        bundleLifecycle.stop(bundle, options);
    }

    @Override
    public void stopBundle(XBundle bundle, int options) throws BundleException {

        // If this bundle's state is not STARTING or ACTIVE then this method returns immediately
        if (bundle.getState() != Bundle.STARTING && bundle.getState() != Bundle.ACTIVE)
            return;

        LockManager lockManager = getFrameworkState().getLockManager();
        LockContext lockContext = null;
        try {
            AbstractBundleState<?> bundleState = AbstractBundleState.assertBundleState(bundle);
            lockContext = lockManager.lockItems(Method.STOP, (LockableItem)bundle);
            bundleState.stopInternal(options);
        } catch (BundleException ex) {
            LOGGER.debugf(ex, "Cannot stop bundle: %s", bundle);
            throw ex;
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public void updateBundle(XBundle bundle, InputStream input) throws BundleException {
        LockManager lockManager = getFrameworkState().getLockManager();
        LockContext lockContext = null;
        try {
            AbstractBundleState<?> bundleState = AbstractBundleState.assertBundleState(bundle);
            lockContext = lockManager.lockItems(Method.UPDATE, (LockableItem)bundle);
            bundleState.updateInternal(input);
        } catch (BundleException ex) {
            LOGGER.debugf(ex, "Cannot update bundle: %s", bundle);
            throw ex;
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    @Override
    public void uninstallBundle(XBundle bundle, int options) throws BundleException {

        if (bundle.getState() == Bundle.UNINSTALLED)
            return;

        LockManager lockManager = getFrameworkState().getLockManager();
        LockContext lockContext = null;
        try {
            AbstractBundleState<?> bundleState = AbstractBundleState.assertBundleState(bundle);
            LockableItem wireLock = lockManager.getItemForType(FrameworkWiringLock.class);
            lockContext = lockManager.lockItems(Method.UNINSTALL, (LockableItem)bundle, wireLock);
            bundleState.uninstallInternal(options);
        } catch (BundleException ex) {
            LOGGER.errorCannotUninstallBundle(ex, bundle);
            throw ex;
        } finally {
            lockManager.unlockItems(lockContext);
        }
    }

    void removeRevisionLifecycle(XBundleRevision brev, int options) {
        BundleLifecycle bundleLifecycle = getBundleLifecycle();
        bundleLifecycle.removeRevision(brev, options);
    }

    @Override
    public void removeRevision(XBundleRevision brev, int options) {
        XLockableEnvironment env = getFrameworkState().getEnvironment();
        boolean aquireLock = (options & InternalConstants.UNINSTALL_INTERNAL) == 0;
        env.uninstallResources(new XResource[] { brev }, aquireLock);
        if (brev instanceof UserBundleRevision) {
            UserBundleRevision userRev = (UserBundleRevision) brev;
            userRev.getBundleState().removeRevision(userRev);
            userRev.close();
        }
    }

    private BundleLifecycle getBundleLifecycle() {
        return getFrameworkState().getCoreServices().getBundleLifecycle();
    }

    void checkUniqunessPolicy(Bundle targetBundle, String symbolicName, Version version, int policy) throws BundleException {

        if (uniquenessPolicy == UniquenessPolicy.multiple)
            return;

        Set<Bundle> candidates = new HashSet<Bundle>();
        if (symbolicName != null) {
            VersionRange versionRange = null;
            if (!Version.emptyVersion.equals(version)) {
                versionRange = new VersionRange("[" + version + "," + version + "]");
            }
            for (XBundle aux : getBundles(symbolicName, versionRange)) {
                if (aux != targetBundle && aux.getState() != Bundle.UNINSTALLED) {
                    candidates.add(aux);
                }
            }
        }
        if (candidates.isEmpty())
            return;

        if (uniquenessPolicy == UniquenessPolicy.managed) {
            BundleContext syscontext = getFrameworkState().getSystemBundle().getBundleContext();
            Collection<ServiceReference<CollisionHook>> srefs = null;
            try {
                srefs = syscontext.getServiceReferences(CollisionHook.class, null);
            } catch (InvalidSyntaxException ex) {
                // ignore
            }
            for (ServiceReference<CollisionHook> sref : srefs) {
                CollisionHook hook = syscontext.getService(sref);
                hook.filterCollisions(policy, targetBundle, candidates);
            }
        }
        if (!candidates.isEmpty()) {
            throw new BundleException(MESSAGES.nameAndVersionAlreadyInstalled(symbolicName, version), BundleException.DUPLICATE_BUNDLE_ERROR);
        }
    }

    void unresolveBundle(UserBundleState userBundle) {
        LOGGER.tracef("Unresolving bundle: %s", userBundle);
        ModuleManager moduleManager = getFrameworkState().getModuleManager();
        for (XBundleRevision brev : userBundle.getAllBundleRevisions()) {
            UserBundleRevision userRev = (UserBundleRevision) brev;
            if (userRev.isFragment() == false) {
                ModuleIdentifier identifier = moduleManager.getModuleIdentifier(brev);
                moduleManager.removeModule(brev, identifier);
            }
        }
    }

    void removeBundle(UserBundleState userBundle, int options) {
        LOGGER.tracef("Start removing bundle: %s", userBundle);

        if ((options & Bundle.STOP_TRANSIENT) == 0) {
            StorageManager storagePlugin = getFrameworkState().getStorageManager();
            storagePlugin.deleteStorageState(userBundle.getStorageState());
        }

        for (XBundleRevision brev : userBundle.getAllBundleRevisions()) {
            if ((options & InternalConstants.UNINSTALL_INTERNAL) == 0) {
                removeRevisionLifecycle(brev, options);
            } else {
                removeRevision(brev, options);
            }
        }

        LOGGER.debugf("Removed bundle: %s", userBundle);
    }

    @Override
    public void registerExecutorService(ExecutorService executorService) {
        synchronized (executorServices) {
            executorServices.add(executorService);
        }
    }

    @Override
    public void unregisterExecutorService(ExecutorService executorService) {
        synchronized (executorServices) {
            executorServices.remove(executorService);
        }
    }

    void fireFrameworkError(XBundle bundle, String context, Throwable t) {
        FrameworkEvents plugin = getFrameworkState().getFrameworkEvents();
        if (t instanceof BundleException) {
            plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, t);
        } else if (bundle != null) {
            plugin.fireFrameworkEvent(bundle, FrameworkEvent.ERROR, new BundleException("Error " + context + " bundle: " + bundle, t));
        } else {
            SystemBundleState systemBundle = injectedSystemBundle.getValue();
            plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.ERROR, new BundleException("Error " + context, t));
        }
    }

    void fireFrameworkWarning(AbstractBundleState<?> bundleState, String context, Throwable t) {
        FrameworkEvents plugin = getFrameworkState().getFrameworkEvents();
        if (t instanceof BundleException) {
            plugin.fireFrameworkEvent(bundleState, FrameworkEvent.WARNING, t);
        } else if (bundleState != null) {
            plugin.fireFrameworkEvent(bundleState, FrameworkEvent.WARNING, new BundleException("Error " + context + " bundle: " + bundleState, t));
        } else {
            SystemBundleState systemBundle = injectedSystemBundle.getValue();
            plugin.fireFrameworkEvent(systemBundle, FrameworkEvent.WARNING, new BundleException("Error " + context, t));
        }
    }

    boolean hasStopped() {
        return shutdownContainer.isShutdownInitiated() || managerStopped.get();
    }

    void assertNotStopped() {
        if (hasStopped())
            throw MESSAGES.illegalStateFrameworkAlreadyStopped();
    }

    void shutdownManager(boolean stopForUpdate) {

        // If the Framework is not STARTING and not ACTIVE there is nothing to do
        int state = getManagerState();
        if (state != Bundle.STARTING && state != Bundle.ACTIVE)
            return;

        LOGGER.debugf("Stop framework");

        stoppedEvent = stopForUpdate ? FrameworkEvent.STOPPED_UPDATE : FrameworkEvent.STOPPED;
        getSystemBundle().changeState(Bundle.STOPPING);
        setManagerState(Bundle.STOPPING);

        final CountDownLatch latch = new CountDownLatch(1);
        FrameworkListener listener = new FrameworkListener() {
            @Override
            public void frameworkEvent(FrameworkEvent event) {
                if (event.getType() == FrameworkEvent.STARTLEVEL_CHANGED) {
                    latch.countDown();
                }
            }
        };

        // Move to start level 0 in the current thread
        FrameworkStartLevel startLevel = getSystemBundle().adapt(FrameworkStartLevel.class);
        ((FrameworkStartLevelSupport) startLevel).shutdownFramework(listener);

        // Wait for the start level event
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            // ignore
        }

        // Shutdown all executor services
        synchronized (executorServices) {
            for (ExecutorService service : executorServices) {
                service.shutdown();
            }
            for (ExecutorService service : executorServices) {
                try {
                    service.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    // ignore
                }
            }
        }

        cachedSystemBundle = getSystemBundle();
        shutdownContainer.shutdown();
        setManagerState(Bundle.RESOLVED);
    }

    /**
     * Wait until this Framework has completely stopped.
     *
     * The stop and update methods on a Framework performs an asynchronous stop of the Framework. This method can be used to
     * wait until the asynchronous stop of this Framework has completed. This method will only wait if called when this
     * Framework is in the Bundle.STARTING, Bundle.ACTIVE, or Bundle.STOPPING states. Otherwise it will return immediately.
     *
     * A Framework Event is returned to indicate why this Framework has stopped.
     */
    FrameworkEvent waitForStop(long timeout) throws InterruptedException {
        SystemBundleState systemBundle = getSystemBundle();
        Bundle eventSource = systemBundle != null ? systemBundle : cachedSystemBundle;

        FrameworkEvent frameworkEvent = null;
        shutdownContainer.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        if (eventSource != null) {
            int eventType = shutdownContainer.isShutdownComplete() ? stoppedEvent : FrameworkEvent.WAIT_TIMEDOUT;
            frameworkEvent = new FrameworkEvent(eventType, eventSource, null);
        }
        return frameworkEvent;
    }

    static {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {

            @Override
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

    // The TCK calls waitForStop before it initiates the shutdown, in which case the {@link ServiceContainer}
    // returns imediately from awaitTermination and returns false from isShutdownComplete.
    // We compensate with a grace periode that allows the shutdown to get initiated after awaitTermination.
    static class ShutdownContainer {
        private final ServiceContainer serviceContainer;
        private final AtomicBoolean shutdownInitiated;
        private final boolean allowContainerShutdown;

        ShutdownContainer(final ServiceContainer serviceContainer, boolean allowContainerShutdown) {
            this.serviceContainer = serviceContainer;
            this.allowContainerShutdown = allowContainerShutdown;
            this.shutdownInitiated = new AtomicBoolean(false);
        }

        ServiceController<?> getRequiredService(ServiceName serviceName) {
            return serviceContainer.getRequiredService(serviceName);
        }

        boolean isShutdownInitiated() {
            return shutdownInitiated.get();
        }

        void shutdown() {
            if (allowContainerShutdown) {
                serviceContainer.shutdown();
                synchronized (shutdownInitiated) {
                    shutdownInitiated.set(true);
                    LOGGER.debugf("shutdownInitiated.notifyAll");
                    shutdownInitiated.notifyAll();
                }
            }
        }

        void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            LOGGER.debugf("awaitTermination: %dms", unit.toMillis(timeout));
            synchronized (shutdownInitiated) {
                if (shutdownInitiated.get() == false) {
                    LOGGER.debugf("shutdownInitiated.wait");
                    shutdownInitiated.wait(2000);
                }
            }
            serviceContainer.awaitTermination(timeout == 0 ? Long.MAX_VALUE : timeout, unit);
        }

        boolean isShutdownComplete() {
            return serviceContainer.isShutdownComplete();
        }
    }
}